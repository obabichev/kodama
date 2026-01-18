package com.obabichev.kodama.compiler

import com.obabichev.kodama.compiler.metadata.KspMetadataLoader
import com.obabichev.kodama.compiler.metadata.RuntimeMetadataExtractor
import com.obabichev.kodama.compiler.metadata.TableMetadata
import com.obabichev.kodama.compiler.parser.ASTQueryDiscoveryIntegration
import com.obabichev.kodama.compiler.parser.DiscoveredMarkers
import com.obabichev.kodama.compiler.parser.QueryOperation
import com.obabichev.kodama.compiler.parser.SubqueryPattern
import com.obabichev.kodama.compiler.util.toSnakeCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Represents column information for subquery code generation.
 * @param propertyName The Kotlin property name used in accessor API (e.g., userName)
 * @param sqlColumnName The SQL column name used in database queries (e.g., user_name)
 * @param kotlinType The Kotlin type of the column (e.g., String, Int)
 */
internal data class SubqueryColumnInfo(
    val propertyName: String,
    val sqlColumnName: String,
    val kotlinType: String,
    val isNullable: Boolean = false,  // True if the column can be null
    val isMarkerBased: Boolean = false  // True if this column uses a marker interface (like MyAlias)
)

/**
 * Represents a subquery that can be joined in queries.
 */
internal data class SubqueryInfo(
    val name: String,            // PascalCase name (e.g., "ExpensiveOrders")
    val sqlAlias: String,        // snake_case alias (e.g., "expensive_orders")
    val columns: List<SubqueryColumnInfo>,  // List of column information
    val sourceTables: List<String> = emptyList()  // Tables involved in this subquery (e.g., ["Order"])
)

/**
 * Represents a marker interface used for selectAs<T> type-safe selections.
 * @param interfaceName The interface name in PascalCase (e.g., TotalCost)
 * @param propertyName The corresponding property name in camelCase (e.g., totalCost)
 * @param packageName The package where the interface is defined
 * @param resultType The actual type returned by the expression
 * @param sqlAliasStyle The SQL alias naming style (CAMEL_CASE or SNAKE_CASE)
 */
internal data class SelectionMarkerInfo(
    val interfaceName: String,
    val propertyName: String,
    val packageName: String,
    val resultType: String = "Number",
    val sqlAliasStyle: String = "SNAKE_CASE"  // Default to snake_case for backwards compatibility
)

/**
 * JSON data classes for parsing relationships.json.
 * Used by kotlinx.serialization to deserialize the relationships file.
 */
@Serializable
internal data class RelationshipJson(
    val from: String,
    val to: String
)

@Serializable
internal data class RelationshipsFileJson(
    val `package`: String,
    val relationships: List<RelationshipJson>
)

/**
 * Holds relationship metadata loaded from KSP-generated relationships.json.
 * This tells us which table pairs have CanJoin instances and can have
 * compile-time join validation.
 * @param relationships Set of table pairs that have declared relationships
 */
data class RelationshipMetadata(
    val relationships: Set<Pair<String, String>>  // (From, To) pairs
) {
    /**
     * Check if a direct relationship exists between two tables.
     */
    fun hasRelationship(from: String, to: String): Boolean =
        relationships.contains(from to to)

    /**
     * Check if a join is valid from a set of already-joined tables to a target table.
     *
     * This supports transitive relationships:
     * - Direct: hasRelationshipFrom([Person], Order) → checks Person→Order
     * - Transitive: hasRelationshipFrom([Person, Order], Company) → checks Person→Company OR Order→Company
     *
     * Returns true if ANY of the already-joined tables has a relationship to the target.
     */
    fun hasRelationshipFrom(alreadyJoinedTables: List<String>, targetTable: String): Boolean {
        return alreadyJoinedTables.any { fromTable ->
            hasRelationship(fromTable, targetTable)
        }
    }

    /**
     * Get all tables that can be joined from a given table.
     */
    fun getReachableTables(from: String): Set<String> {
        return relationships
            .filter { it.first == from }
            .map { it.second }
            .toSet()
    }

    /**
     * Get all tables that can be joined from any of the given tables (transitive closure).
     */
    fun getReachableTablesFrom(fromTables: List<String>): Set<String> {
        return fromTables.flatMap { getReachableTables(it) }.toSet()
    }
}

/**
 * Phase 2: Pattern-Driven Code Generation
 *
 * Generates code based on query usage patterns discovered in test files.
 * This code depends on the actual queries written in tests and changes when patterns change.
 *
 * Generates:
 * - Query builders for discovered table combinations
 * - Join extensions for discovered join patterns
 * - Select contexts
 * - Marker interfaces for discovered markers
 * - Multi-table result classes
 * - Execute methods for discovered selection patterns
 * - Subquery support
 *
 * Output: Multiple organized files (~100-150 files total)
 * - _infrastructure/ - Shared markers, phantom types, registries
 * - single_table/ - One file per regular table
 * - combinations/ - One file per multi-table combination
 * - subqueries/ - One file per subquery
 * - synthetic/ - Auto-generated Table+Subquery combinations
 */
@CacheableTask
abstract class GenerateQueryExtensionsTask : DefaultTask() {

    @get:Input
    abstract val schemaPackage: Property<String>

    @get:Input
    abstract val generatedPackage: Property<String>

    @get:Input
    abstract val maxTableCount: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kspMetadataFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tableMetadataFile: RegularFileProperty  // Phase 1 output (for dependency tracking)

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaFiles: ConfigurableFileCollection

    /**
     * Load table metadata (same as Phase 1, but we need it for pattern generation).
     * In the future, we could optimize this to read from Phase 1's generated code.
     */
    private fun loadTableMetadata(): List<TableMetadata> {
        logger.lifecycle("Kodama Phase 2: Loading table metadata...")

        val metadataLoader = KspMetadataLoader()
        val kspMetadataPath = kspMetadataFile.get().asFile

        if (!kspMetadataPath.exists()) {
            error("Kodama: KSP metadata file not found at ${kspMetadataPath.absolutePath}")
        }

        val kspTables = metadataLoader.loadMetadata(kspMetadataPath)

        val buildDir = project.layout.buildDirectory.get().asFile
        val classOutputDir = buildDir.resolve("classes/kotlin/main")

        if (!classOutputDir.exists()) {
            error("Kodama: Compiled classes not found at ${classOutputDir.absolutePath}")
        }

        val classpathFiles = mutableListOf<File>()
        classpathFiles.add(classOutputDir)

        val runtimeClasspath = project.configurations.findByName("runtimeClasspath")
        runtimeClasspath?.resolvedConfiguration?.resolvedArtifacts?.forEach { artifact ->
            classpathFiles.add(artifact.file)
        }

        val extractor = RuntimeMetadataExtractor(classOutputDir, classpathFiles)

        val tableMetadata = try {
            extractor.extractAllTables(kspTables)
        } finally {
            extractor.close()
        }

        logger.lifecycle("Kodama Phase 2: Loaded metadata for ${tableMetadata.size} tables")

        return tableMetadata
    }

    /**
     * Load relationship metadata from KSP-generated relationships.json file.
     * This metadata tells us which table pairs have declared relationships and
     * CanJoin instances, allowing us to add compile-time join validation.
     */
    private fun loadRelationshipMetadata(): RelationshipMetadata {
        val buildDir = project.layout.buildDirectory.get().asFile

        // Try multiple possible locations for relationships.json
        val possibleLocations = listOf(
            buildDir.resolve("generated/ksp/main/resources/relationships.json"),
            buildDir.resolve("resources/main/relationships.json"),
            buildDir.resolve("kspCaches/main/backups/resources/relationships.json")
        )

        val relationshipsFile = possibleLocations.firstOrNull { it.exists() }

        if (relationshipsFile == null || !relationshipsFile.exists()) {
            logger.lifecycle("Kodama Phase 2: No relationships.json found, CanJoin constraints will not be generated")
            logger.info("Kodama: Searched in: ${possibleLocations.joinToString(", ") { it.absolutePath }}")
            return RelationshipMetadata(emptySet())
        }

        logger.lifecycle("Kodama Phase 2: Loading relationships from ${relationshipsFile.name}")

        try {
            val jsonContent = relationshipsFile.readText()

            // Parse JSON using kotlinx.serialization (type-safe, no regex!)
            val json = Json { ignoreUnknownKeys = true }
            val data = json.decodeFromString<RelationshipsFileJson>(jsonContent)

            val relationships = data.relationships.map { it.from to it.to }.toSet()

            relationships.forEach { (from, to) ->
                logger.info("Kodama: Found relationship: $from -> $to")
            }

            logger.lifecycle("Kodama Phase 2: Loaded ${relationships.size} relationship(s)")
            return RelationshipMetadata(relationships)
        } catch (e: Exception) {
            logger.warn("Kodama Phase 2: Failed to load relationships.json: ${e.message}")
            return RelationshipMetadata(emptySet())
        }
    }

    @TaskAction
    fun generate() {
        val genPkg = generatedPackage.get()
        val schemaPkg = schemaPackage.get()

        // Load table metadata
        val kspTableMetadata = loadTableMetadata()
        logger.info("Kodama Phase 2: Using KSP metadata for ${kspTableMetadata.size} tables")

        // Convert package name to file path (com.example.package -> com/example/package)
        val genPkgPath = genPkg.replace('.', '/')
        val outputDir = outputDirectory.asFile.get()

        // Discover tables using KSP + Runtime metadata extraction
        val tables = mutableSetOf<String>()
        val tableNameMap = mutableMapOf<String, String>()  // lowercase -> original case mapping
        val tableToProperties = mutableMapOf<String, List<String>>()
        val tableToPropertyTypes = mutableMapOf<String, Map<String, String>>()
        val tableToPropertySqlNames = mutableMapOf<String, Map<String, String>>()  // Maps property name to SQL column name
        val tableToPropertyNullability = mutableMapOf<String, Map<String, Boolean>>()
        val tableToPropertyAutoGenerated = mutableMapOf<String, Map<String, Boolean>>()  // Tracks AlwaysGenerated columns
        val tableToPackages = mutableMapOf<String, String>()  // Maps table name to package name

        // Get schema files for later use (marker interface scanning)
                val schemaKtFiles = schemaFiles.files.asSequence().filter { it.extension == "kt" }

        // Load table metadata via KSP + Runtime extraction
        kspTableMetadata.forEach { table ->
            val tableName = table.name
            tables.add(tableName)
            tableNameMap[tableName.lowercase()] = tableName
            tableToPackages[tableName] = table.packageName  // Preserve package information

            val properties = table.columns.map { it.propertyName }
            val propertyTypes = table.columns.associate { it.propertyName to it.kotlinType }
            val propertySqlNames = table.columns.associate { it.propertyName to it.sqlColumnName }
            val propertyNullability = table.columns.associate { it.propertyName to it.isNullable }
            val propertyAutoGenerated = table.columns.associate { it.propertyName to it.isAutoGenerated }

            if (properties.isNotEmpty()) {
                tableToProperties[tableName] = properties
                tableToPropertyTypes[tableName] = propertyTypes
                tableToPropertySqlNames[tableName] = propertySqlNames
                tableToPropertyNullability[tableName] = propertyNullability
                tableToPropertyAutoGenerated[tableName] = propertyAutoGenerated
            }
        }

        // ==============================================
        // AST-BASED QUERY DISCOVERY
        // ==============================================
        val testKtFiles = testFiles.files.filter { it.extension == "kt" }.toList()

        logger.lifecycle("=".repeat(70))
        logger.lifecycle("Kodama AST Parser: Starting query discovery")
        logger.lifecycle("=".repeat(70))

        // Declare variables outside try block so they're accessible later
        var astMarkers: DiscoveredMarkers
        var astSubqueries: List<SubqueryPattern>
        var astCombinations: Set<List<String>>

        try {
            val astIntegration = ASTQueryDiscoveryIntegration(logger)

            // Discover table combinations using AST
            astCombinations = astIntegration.discoverTableCombinations(testKtFiles)

            logger.lifecycle("✅ AST Parser: Discovered ${astCombinations.size} table combinations")
            astCombinations.take(10).forEach { combination ->
                logger.info("  - ${combination.joinToString(" → ")}")
            }
            if (astCombinations.size > 10) {
                logger.info("  ... and ${astCombinations.size - 10} more")
            }

            // Discover markers using AST
            astMarkers = astIntegration.discoverColumnMarkers(testKtFiles)

            logger.lifecycle("✅ AST Parser: Discovered ${astMarkers.markerTypes.size} column markers")
            astMarkers.markerTypes.entries.take(5).forEach { (marker, type) ->
                logger.info("  - $marker: $type")
            }
            if (astMarkers.markerTypes.size > 5) {
                logger.info("  ... and ${astMarkers.markerTypes.size - 5} more")
            }

            // Discover subqueries using AST
            astSubqueries = astIntegration.discoverSubqueries(testKtFiles)

            logger.lifecycle("✅ AST Parser: Discovered ${astSubqueries.size} subqueries")
            astSubqueries.take(5).forEach { subquery ->
                logger.info("  - ${subquery.alias} (base: ${subquery.getBaseTable()})")
            }
            if (astSubqueries.size > 5) {
                logger.info("  ... and ${astSubqueries.size - 5} more")
            }

            logger.lifecycle("=".repeat(70))
            logger.lifecycle("AST Parser discovery complete - using AST data for code generation")
            logger.lifecycle("=".repeat(70))

        } catch (e: Exception) {
            logger.warn("⚠️ AST Parser encountered an error - using empty defaults:")
            logger.warn("   ${e.message}")
            logger.info("   Full stack trace:", e)
            // Provide fallback empty data
            astMarkers = DiscoveredMarkers(emptyMap(), emptyMap(), emptyMap())
            astSubqueries = emptyList()
            astCombinations = emptySet()
        }

        // ==============================================
        // EXTRACT DATA FROM AST DISCOVERY
        // ==============================================

        // Extract marker data from AST discovery
        val usedColumnMarkers = astMarkers.markerTypes.keys.toMutableSet()
        val markerTypes = astMarkers.markerTypes.toMutableMap()
        val markerAliasStyles = astMarkers.markerAliasStyles.toMutableMap()
        val markerTableUsage = astMarkers.markerTableUsage.toMutableMap()

        logger.lifecycle("Kodama: Using AST-discovered markers (zero regex!)")
        logger.lifecycle("  - Markers: ${usedColumnMarkers.size}")
        logger.lifecycle("  - Types inferred: ${markerTypes.size}")

        // Extract subquery data from AST discovery
        val subqueriesFromAST = astSubqueries.associateBy { it.alias }
        logger.lifecycle("  - Subqueries: ${subqueriesFromAST.size}")

        // ==============================================
        // CONVERT AST DATA TO GENERATOR FORMAT
        // ==============================================

        // Selection patterns and query combinations
        val queryCombinations = mutableListOf<List<String>>()
        val selectionPatterns = mutableMapOf<List<String>, MutableSet<List<String>>>()
        val combinationJoinTypes = mutableMapOf<String, List<Pair<String, String?>>>()
        val subqueries = mutableMapOf<String, SubqueryInfo>()
        val selectionMarkers = mutableSetOf<SelectionMarkerInfo>()
        val markerCombinations = mutableSetOf<List<String>>()

        // Convert AST markers to SelectionMarkerInfo format
        markerTypes.forEach { (markerName, kotlinType) ->
            val aliasStyle = markerAliasStyles[markerName] ?: "SNAKE_CASE"
            val propertyName = markerName.replaceFirstChar { it.lowercase() }  // TotalCost -> totalCost

            selectionMarkers.add(SelectionMarkerInfo(
                interfaceName = markerName,
                propertyName = propertyName,
                packageName = schemaPkg,
                resultType = kotlinType,
                sqlAliasStyle = aliasStyle
            ))
        }

        // Convert AST subqueries to SubqueryInfo format
        subqueriesFromAST.forEach { entry ->
            val alias = entry.key
            val subqueryPattern = entry.value

            // Extract columns from the subquery pattern
            val columns = subqueryPattern.operations
                .filter { op: QueryOperation -> op.type == com.obabichev.kodama.compiler.parser.OperationType.SELECT_ALIASED }
                .map { op: QueryOperation ->
                    val markerName = op.marker ?: "value"
                    SubqueryColumnInfo(
                        propertyName = markerName.replaceFirstChar { it.lowercase() },
                        sqlColumnName = markerName.toSnakeCase(),
                        kotlinType = markerTypes[op.marker] ?: "String",
                        isMarkerBased = true
                    )
                }

            subqueries[alias] = SubqueryInfo(
                name = alias,
                sqlAlias = alias.toSnakeCase(),
                columns = columns,
                sourceTables = subqueryPattern.getTables()
            )
        }

        logger.lifecycle("Kodama: Converted AST data to generator format")
        logger.lifecycle("  - Selection markers: ${selectionMarkers.size}")
        logger.lifecycle("  - Subqueries: ${subqueries.size}")

        // ==============================================
        // QUERY COMBINATION GENERATION
        // Using relationship-based generation (preferred) or AST discovery (fallback)
        // ==============================================

        // Load relationship metadata (mutable to allow AST-discovered updates)
        var relationshipMetadata = loadRelationshipMetadata()

        if (relationshipMetadata.relationships.isNotEmpty()) {
            logger.lifecycle("Kodama Phase 2: Generating combinations from ${relationshipMetadata.relationships.size} declared relationships")

            // Track which combinations we've already added (to avoid duplicates)
            val existingCombinations = queryCombinations.toSet()

            // Generate direct relationship combinations (2 tables)
            // For each relationship, generate all join type variants (INNER, LEFT, RIGHT, FULL)
            relationshipMetadata.relationships.forEach { (from, to) ->
                val combination = listOf(from, to)
                if (!existingCombinations.contains(combination)) {
                    queryCombinations.add(combination)

                    // Add all join type variants
                    val baseKey = combination.joinToString("_")
                    combinationJoinTypes[baseKey] = listOf(from to null, to to "INNER")
                    combinationJoinTypes["$baseKey:LEFT"] = listOf(from to null, to to "LEFT")
                    combinationJoinTypes["$baseKey:RIGHT"] = listOf(from to null, to to "RIGHT")
                    combinationJoinTypes["$baseKey:FULL"] = listOf(from to null, to to "FULL")

                    logger.info("  Added direct relationship: $from → $to (all join types)")
                }
            }

            // Generate transitive relationship combinations (3+ tables)
            // For each 2-table combination, check if we can add a third table
            val twoTableCombos = queryCombinations.filter { it.size == 2 && it.all { name -> tables.contains(name) } }
            twoTableCombos.forEach { combo ->
                val (table1, table2) = combo

                // Find tables reachable from either table1 or table2
                val reachableFromEither = relationshipMetadata.getReachableTablesFrom(listOf(table1, table2))

                reachableFromEither.forEach { table3 ->
                    // Only add if table3 is not already in the combination
                    if (!combo.contains(table3)) {
                        val threeTableCombo = combo + table3
                        if (!existingCombinations.contains(threeTableCombo)) {
                            queryCombinations.add(threeTableCombo)

                            // Add all join type variants for 3-table combinations too
                            val baseKey = threeTableCombo.joinToString("_")
                            combinationJoinTypes[baseKey] = listOf(
                                table1 to null,
                                table2 to "INNER",
                                table3 to "INNER"
                            )
                            // Also add variants with different join types
                            combinationJoinTypes["$baseKey:LEFT_INNER"] = listOf(
                                table1 to null,
                                table2 to "LEFT",
                                table3 to "INNER"
                            )
                            combinationJoinTypes["$baseKey:INNER_LEFT"] = listOf(
                                table1 to null,
                                table2 to "INNER",
                                table3 to "LEFT"
                            )

                            logger.info("  Added transitive relationship: ${threeTableCombo.joinToString(" → ")}")
                        }
                    }
                }
            }

            logger.lifecycle("Kodama Phase 2: Total combinations after relationship-based generation: ${queryCombinations.size}")
        } else {
            // FALLBACK: Use AST-discovered combinations when relationships.json is missing
            logger.lifecycle("Kodama Phase 2: No relationships.json found - using AST-discovered combinations")

            if (astCombinations.isNotEmpty()) {
                logger.lifecycle("Kodama Phase 2: Generating from ${astCombinations.size} AST-discovered table combinations")

                // Extract relationships from AST combinations and update relationshipMetadata
                val discoveredRelationships = mutableSetOf<Pair<String, String>>()

                astCombinations.forEach { combination ->
                    queryCombinations.add(combination)

                    // Extract pairwise relationships from this combination
                    // For [A, B, C], extract: A→B, B→C, A→C (all possible pairs)
                    for (i in combination.indices) {
                        for (j in i + 1 until combination.size) {
                            val from = combination[i]
                            val to = combination[j]

                            // Skip self-joins (Table → Table)
                            // Self-joins would create duplicate property names in JoinContext
                            if (from != to) {
                                // Add bidirectional relationships (both directions)
                                discoveredRelationships.add(from to to)
                                discoveredRelationships.add(to to from)
                            }
                        }
                    }

                    // Add all join type variants for this combination
                    val baseKey = combination.joinToString("_")

                    if (combination.size == 2) {
                        val (table1, table2) = combination
                        combinationJoinTypes[baseKey] = listOf(table1 to null, table2 to "INNER")
                        combinationJoinTypes["$baseKey:LEFT"] = listOf(table1 to null, table2 to "LEFT")
                        combinationJoinTypes["$baseKey:RIGHT"] = listOf(table1 to null, table2 to "RIGHT")
                        combinationJoinTypes["$baseKey:FULL"] = listOf(table1 to null, table2 to "FULL")

                        logger.info("  Added AST combination: ${combination.joinToString(" → ")} (all join types)")
                    } else if (combination.size == 3) {
                        val (table1, table2, table3) = combination
                        combinationJoinTypes[baseKey] = listOf(
                            table1 to null,
                            table2 to "INNER",
                            table3 to "INNER"
                        )
                        combinationJoinTypes["$baseKey:LEFT_INNER"] = listOf(
                            table1 to null,
                            table2 to "LEFT",
                            table3 to "INNER"
                        )
                        combinationJoinTypes["$baseKey:INNER_LEFT"] = listOf(
                            table1 to null,
                            table2 to "INNER",
                            table3 to "LEFT"
                        )

                        logger.info("  Added AST combination: ${combination.joinToString(" → ")}")
                    } else {
                        // For 4+ tables, add default INNER join pattern
                        val joinTypes = combination.mapIndexed { index, table ->
                            table to if (index == 0) null else "INNER"
                        }
                        combinationJoinTypes[baseKey] = joinTypes

                        logger.info("  Added AST combination: ${combination.joinToString(" → ")}")
                    }
                }

                // CRITICAL FIX: Update relationshipMetadata with discovered relationships
                // This enables JOIN generator creation in GeneratorFactory
                relationshipMetadata = RelationshipMetadata(discoveredRelationships)

                logger.lifecycle("Kodama Phase 2: Extracted ${discoveredRelationships.size} relationships from AST combinations")
                discoveredRelationships.take(10).forEach { (from, to) ->
                    logger.info("  - $from → $to")
                }
                if (discoveredRelationships.size > 10) {
                    logger.info("  ... and ${discoveredRelationships.size - 10} more")
                }

                logger.lifecycle("Kodama Phase 2: Total combinations after AST-based generation: ${queryCombinations.size}")
            } else {
                logger.lifecycle("Kodama Phase 2: No AST combinations found - generating from single tables only")
            }
        }

        // ==============================================
        // ADD DEFAULT SELECTION PATTERNS
        // ==============================================

        // Ensure default "All from all tables" patterns exist for each combination
        // This is needed because default execute() methods reference these QueryResult classes
        queryCombinations.forEach { combination ->
            // Create default pattern for this specific combination
            val defaultPattern = combination.map { table -> "$table:All" }

            // Add to selectionPatterns (create entry if needed)
            if (!selectionPatterns.containsKey(combination)) {
                selectionPatterns[combination] = mutableSetOf()
            }
            selectionPatterns[combination]!!.add(defaultPattern)

            // ALSO add patterns for selecting from individual tables in multi-table combinations
            // This handles cases like LEFT JOIN where you might only select from the main table
            if (combination.size > 1) {
                combination.forEach { table ->
                    val singleTablePattern = listOf("$table:All")
                    selectionPatterns[combination]!!.add(singleTablePattern)
                }
            }
        }

        logger.lifecycle("Kodama Phase 2: Added default selection patterns for ${queryCombinations.size} combinations")

        // ==============================================
        // NEW: Use refactored generator system
        // ==============================================

        logger.lifecycle("Kodama Phase 2: Transforming discovered patterns to structured data...")

        // Build table metadata map for DataTransformer
        val tableMetadataMap = tables.associateWith { tableName ->
            val columns = (tableToProperties[tableName] ?: emptyList()).map { propName ->
                mapOf(
                    "propertyName" to propName,
                    "sqlColumnName" to (tableToPropertySqlNames[tableName]?.get(propName) ?: propName),
                    "kotlinType" to (tableToPropertyTypes[tableName]?.get(propName) ?: "String"),
                    "isNullable" to (tableToPropertyNullability[tableName]?.get(propName) ?: false),
                    "isAutoGenerated" to (tableToPropertyAutoGenerated[tableName]?.get(propName) ?: false)
                )
            }

            mapOf(
                "packageName" to (tableToPackages[tableName] ?: schemaPkg),  // Include package name
                "sqlTableName" to (tableName.lowercase()),
                "columns" to columns
            )
        }

        // Build discovered data map
        val discoveredDataMap = mapOf<String, Any>(
            "queryCombinations" to queryCombinations.toList(),
            "combinationJoinTypes" to combinationJoinTypes.mapValues { (_, joinTypes) ->
                joinTypes.map { (tableName, joinType) ->
                    mapOf("table" to tableName, "joinType" to joinType)
                }
            },
            "subqueries" to subqueries.values.map { subquery ->
                mapOf(
                    "name" to subquery.name,
                    "sqlAlias" to subquery.sqlAlias,
                    "columns" to subquery.columns.map { col ->
                        mapOf(
                            "propertyName" to col.propertyName,
                            "sqlColumnName" to col.sqlColumnName,
                            "kotlinType" to col.kotlinType
                        )
                    },
                    "sourceTables" to subquery.sourceTables
                )
            },
            "markers" to selectionMarkers.map { marker ->
                mapOf(
                    "interfaceName" to marker.interfaceName,
                    "resultType" to marker.resultType,
                    "sqlAliasStyle" to marker.sqlAliasStyle
                )
            },
            "markerCombinations" to markerCombinations.toList()
        )

        logger.lifecycle("Kodama Phase 2: Creating generators...")
        logger.lifecycle("  - Tables: ${tables.size}")
        logger.lifecycle("  - Query combinations: ${queryCombinations.size}")
        logger.lifecycle("  - Subqueries: ${subqueries.size}")
        logger.lifecycle("  - Selection markers: ${selectionMarkers.size}")
        logger.lifecycle("  - Marker combinations: ${markerCombinations.size}")

        // Transform discovered data to structured format
        val transformer = com.obabichev.kodama.compiler.transform.DataTransformer(
            tableMetadata = tableMetadataMap,
            discoveredData = discoveredDataMap,
            generatedPackage = genPkg
        )
        val transformedData = transformer.transform()

        logger.lifecycle("Kodama Phase 2: After transformation:")
        logger.lifecycle("  - Query combinations (with auto-generated Table+Subquery): ${transformedData.queryCombinations.size}")

        // relationshipMetadata already loaded above (line 1262)

        // Create all generators
        val generatorFactory = com.obabichev.kodama.compiler.generator.GeneratorFactory(
            data = transformedData,
            schemaPackage = schemaPkg,
            generatedPackage = genPkg,
            relationshipMetadata = relationshipMetadata,
            maxTableCount = maxTableCount.get()
        )
        val allGenerators = generatorFactory.createAllGenerators()

        logger.lifecycle("Kodama Phase 2: Generated ${allGenerators.size} code generators")

        // Generate multiple files using MultiFileGenerator
        val multiFileGenerator = com.obabichev.kodama.compiler.generator.MultiFileGenerator(
            outputDirectory = outputDir,
            packageName = genPkg,
            generators = allGenerators
        )

        // Write output files
        outputDir.mkdirs()
        val generatedFiles = multiFileGenerator.generate()

        // Log statistics
        val stats = multiFileGenerator.getStatistics()
        logger.lifecycle("Kodama Phase 2: Successfully generated ${stats["totalFiles"]} files")
        logger.lifecycle("  - Infrastructure: ${stats["infrastructureFiles"]} files")
        logger.lifecycle("  - Single-table: ${stats["singleTableFiles"]} files")
        logger.lifecycle("  - Combinations: ${stats["combinationFiles"]} files")
        logger.lifecycle("  - Subqueries: ${stats["subqueryFiles"]} files")
        logger.lifecycle("  - Synthetic: ${stats["syntheticFiles"]} files")
        logger.lifecycle("  Output directory: ${outputDir.absolutePath}")
    }
}
