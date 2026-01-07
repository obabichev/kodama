package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.data.*
import com.obabichev.kodama.compiler.generator.markers.*
import com.obabichev.kodama.compiler.generator.accessors.*
import com.obabichev.kodama.compiler.generator.contexts.*
import com.obabichev.kodama.compiler.generator.builders.*
import com.obabichev.kodama.compiler.generator.extensions.*
import com.obabichev.kodama.compiler.generator.results.*
import com.obabichev.kodama.compiler.generator.subqueries.*
import com.obabichev.kodama.compiler.transform.DataTransformer

/**
 * Factory for creating all code generators based on transformed data.
 *
 * The GeneratorFactory is the central orchestrator that instantiates all generators
 * in the correct order. It takes structured data from DataTransformer and creates
 * appropriate generator instances for each construct.
 *
 * Organization:
 * - Markers (7 generators)
 * - Accessors (6 generators)
 * - Contexts (6 generators)
 * - Builders (4 generators)
 * - Extensions (22 generators)
 * - Results (8 generators including hybrid)
 * - Subqueries (3 generators)
 *
 * Example usage:
 * ```
 * val factory = GeneratorFactory(transformedData, schemaPackage, generatedPackage)
 * val allGenerators = factory.createAllGenerators()
 *
 * val fileGenerator = FileGenerator(
 *     packageName = generatedPackage,
 *     fileName = "QueryExtensions.kt",
 *     generators = allGenerators
 * )
 *
 * outputFile.writeText(fileGenerator.generate())
 * ```
 */
class GeneratorFactory(
    private val data: DataTransformer.TransformedData,
    private val schemaPackage: String,
    private val generatedPackage: String
) {

    /**
     * Creates all generators in the appropriate order.
     * Returns a flat list of all generators ready for FileGenerator.
     */
    fun createAllGenerators(): List<CodeGenerator> {
        return buildList {
            // Phase 1: Marker Interfaces
            addAll(createMarkerGenerators())

            // Phase 2: Accessors
            addAll(createAccessorGenerators())

            // Phase 3: Contexts
            addAll(createContextGenerators())

            // Phase 4: Subquery Infrastructure (must be before builders that use it)
            addAll(createSubqueryGenerators())

            // Phase 5: Builders
            addAll(createBuilderGenerators())

            // Phase 6: Extensions
            addAll(createExtensionGenerators())

            // Phase 7: Results
            addAll(createResultGenerators())
        }
    }

    /**
     * Helper function to wrap a generator with target file information for a combination.
     */
    private fun CodeGenerator.forCombination(combination: QueryCombinationInfo): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forCombination(combination))

    /**
     * Helper function to wrap a generator with target file information for a subquery.
     */
    private fun CodeGenerator.forSubquery(subquery: SubqueryInfo): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forSubquery(subquery))

    /**
     * Helper function to wrap a generator for markers file.
     */
    private fun CodeGenerator.forMarkersFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forMarkers())

    /**
     * Helper function to wrap a generator for selection sets file.
     */
    private fun CodeGenerator.forSelectionSetsFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forSelectionSets())

    /**
     * Helper function to wrap a generator for join patterns file.
     */
    private fun CodeGenerator.forJoinPatternsFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forJoinPatterns())

    /**
     * Helper function to wrap a generator for subquery infrastructure file.
     */
    private fun CodeGenerator.forSubqueryInfrastructureFile(): CodeGenerator =
        this.withTargetFile(TargetFileResolver.forSubqueryInfrastructure())

    /**
     * Helper function to wrap a generator for a single table's file.
     */
    private fun CodeGenerator.forTable(table: com.obabichev.kodama.compiler.data.TableInfo): CodeGenerator =
        this.withTargetFile("single_table/${table.capitalizedName}Query.kt")

    /**
     * Phase 1: Marker interface generators (8 types).
     */
    private fun createMarkerGenerators(): List<CodeGenerator> = buildList {
        // Column markers for each UNIQUE column name (deduplicated across all tables)
        val uniqueColumns = data.tables.flatMap { it.columns }
            .distinctBy { it.propertyName }
        uniqueColumns.forEach { column ->
            add(ColumnMarkerGenerator(column).forMarkersFile())
        }

        // Table markers for each table
        data.tables.forEach { table ->
            add(TableMarkerGenerator(table).forMarkersFile())
        }

        // NOTE: AllMarker classes (PersonAllMarker, etc.) are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration

        // Selection markers
        data.markers.forEach { marker ->
            add(SelectionMarkerGenerator(marker).forSelectionSetsFile())
        }

        // Subquery markers
        data.subqueries.forEach { subquery ->
            add(SubqueryMarkerGenerator(subquery).forMarkersFile())
            add(SubqueryAllMarkerGenerator(subquery).forSubquery(subquery))
        }

        // Selection set markers (generated for each marker combination AND all its prefixes)
        val generatedPhantomTypes = mutableSetOf<String>()
        data.markerCombinations.forEach { combination ->
            // Generate phantom types for all prefixes: [M1], [M1, M2], [M1, M2, M3], etc.
            for (prefixLength in 1..combination.markers.size) {
                val prefix = combination.markers.take(prefixLength)
                val phantomTypeName = "SelectionSet_" + prefix.joinToString("_") { it.interfaceName }
                if (phantomTypeName !in generatedPhantomTypes) {
                    generatedPhantomTypes.add(phantomTypeName)
                    add(SelectionSetMarkerGenerator(phantomTypeName).forSelectionSetsFile())
                }
            }
        }

        // HasAliasedSelections marker (single instance)
        add(HasAliasedSelectionsMarkerGenerator().forMarkersFile())

        // Join pattern markers - collect all unique join patterns from query combinations
        val allJoinPatterns = data.queryCombinations
            .map { it.joinPattern }
            .toSet()
        add(JoinPatternMarkerGenerator(allJoinPatterns).forJoinPatternsFile())
    }

    /**
     * Phase 2: Accessor generators (11 types).
     */
    private fun createAccessorGenerators(): List<CodeGenerator> = buildList {
        // Table accessors - go to their respective table files
        data.tables.forEach { table ->
            add(TableAccessorGenerator(table, schemaPackage).forTable(table))
        }

        // NOTE: OrderByAccessor and GroupByAccessor classes are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration

        // Subquery accessors - go to their respective subquery files
        data.subqueries.forEach { subquery ->
            add(SubqueryAccessorGenerator(subquery).forSubquery(subquery))
            add(SubqueryOrderByAccessorGenerator(subquery).forSubquery(subquery))
            add(SubqueryGroupByAccessorGenerator(subquery).forSubquery(subquery))
        }

        // Result accessors - All variant
        // NOTE: ResultAccessor_All, ResultAccessor_<Column>, etc. are generated in TableMetadata.kt
        // Don't generate them here to avoid redeclaration
    }

    /**
     * Phase 3: Context generators (6 types).
     * Deduplicate by table names only - contexts are shared across join patterns.
     */
    private fun createContextGenerators(): List<CodeGenerator> = buildList {
        // Deduplicate combinations by table names for shared context classes
        val uniqueCombinations = data.queryCombinations.distinctBy { combination ->
            combination.tables.map { it.name }.joinToString("_")
        }

        uniqueCombinations.forEach { combination ->
            add(SelectContextGenerator(combination, schemaPackage).forCombination(combination))
            add(WhereContextGenerator(combination, schemaPackage).forCombination(combination))
            add(OrderByContextGenerator(combination, schemaPackage).forCombination(combination))
            add(GroupByContextGenerator(combination, schemaPackage).forCombination(combination))
            add(SelectAllContextGenerator(combination, schemaPackage).forCombination(combination))
        }

        // JoinContext requires specific fromCombination + joiningTable
        // Generate for valid table progressions with deduplication
        val generatedJoinContexts = mutableSetOf<String>()
        uniqueCombinations.forEach { combination ->
            if (combination.tables.size >= 2) {
                // For each table except the first (joined tables)
                combination.tables.drop(1).forEachIndexed { index, joiningTable ->
                    // Create a sub-combination for join context generation
                    val subTables = combination.tables.take(index + 1)
                    val fromCombination = QueryCombinationInfo(
                        baseTable = subTables.first(),
                        joinedTables = subTables.drop(1).map {
                            com.obabichev.kodama.compiler.data.JoinedTableInfo(it, com.obabichev.kodama.compiler.data.JoinType.INNER)
                        }
                    )
                    val allTables = fromCombination.tables + joiningTable
                    val contextName = "JoinContext_" + allTables.joinToString("_") { it.capitalizedName }

                    if (contextName !in generatedJoinContexts) {
                        generatedJoinContexts.add(contextName)
                        // JoinContext goes to the target combination file (fromCombination + joiningTable)
                        val targetCombination = QueryCombinationInfo(
                            baseTable = fromCombination.baseTable,
                            joinedTables = fromCombination.joinedTables +
                                com.obabichev.kodama.compiler.data.JoinedTableInfo(joiningTable, com.obabichev.kodama.compiler.data.JoinType.INNER)
                        )
                        add(JoinContextGenerator(fromCombination, joiningTable, schemaPackage).forCombination(targetCombination))
                    }
                }
            }
        }
    }

    /**
     * Phase 4: Builder generators (4 types).
     */
    private fun createBuilderGenerators(): List<CodeGenerator> = buildList {
        // Query builder classes - deduplicate by table names (builders are shared across join patterns)
        val uniqueCombinations = data.queryCombinations.distinctBy { combination ->
            combination.tables.map { it.name }.joinToString("_")
        }

        uniqueCombinations.forEach { combination ->
            add(QueryBuilderClassGenerator(combination).forCombination(combination))
            add(QueryBuilderBuildMethodGenerator(combination).forCombination(combination))
            add(QueryBuilderAliasAsMethodGenerator(combination).forCombination(combination))
        }

        // NOTE: Subquery builder classes are now generated via queryCombinations
        // Single-subquery combinations are added automatically in DataTransformer
        // So SubqueryBuilderClassGenerator is no longer needed
    }

    /**
     * Phase 5: Extension method generators (22 types).
     * Deduplicate join methods by table names - join methods are shared across join patterns.
     */
    private fun createExtensionGenerators(): List<CodeGenerator> = buildList {
        // NOTE: from() methods are generated in TableMetadata.kt by generateKodamaTableMetadata task
        // Don't generate them here to avoid conflicts

        // Deduplicate combinations by table names for shared join methods
        val uniqueCombinations = data.queryCombinations.distinctBy { combination ->
            combination.tables.map { it.name }.joinToString("_")
        }

        // Join methods for each combination progression
        // Only generate join methods for combinations that actually exist
        uniqueCombinations.forEach { toCombination ->
            if (toCombination.tables.size >= 2) {
                // This is a multi-table combination, so generate join from the previous combination
                val joiningTable = toCombination.tables.last()
                val fromTables = toCombination.tables.dropLast(1)
                val fromCombination = QueryCombinationInfo(
                    baseTable = fromTables.first(),
                    joinedTables = fromTables.drop(1).map {
                        com.obabichev.kodama.compiler.data.JoinedTableInfo(it, com.obabichev.kodama.compiler.data.JoinType.INNER)
                    }
                )

                if (!joiningTable.isSubquery) {
                    // Generate join methods only for patterns that actually exist
                    // Helper function to check if a join pattern exists
                    fun patternExists(pattern: String): Boolean {
                        val tableNames = toCombination.tables.map { it.name }
                        return data.queryCombinations.any { combo ->
                            combo.tables.map { it.name } == tableNames && combo.joinPattern == pattern
                        }
                    }

                    // Compute what the join pattern would be for each join type
                    val basePattern = if (fromCombination.joinedTables.isEmpty()) "" else "${fromCombination.joinPattern}_"

                    // Generate join methods only if target pattern exists
                    // Join methods go to the FROM combination file (the source of the join)
                    if (patternExists(basePattern + "INNER")) {
                        add(JoinMethodGenerator(fromCombination, joiningTable, toCombination, schemaPackage).forCombination(fromCombination))
                        add(InnerJoinMethodGenerator(fromCombination, joiningTable, toCombination, schemaPackage).forCombination(fromCombination))
                    }
                    if (patternExists(basePattern + "LEFT")) {
                        add(LeftJoinMethodGenerator(fromCombination, joiningTable, toCombination, schemaPackage).forCombination(fromCombination))
                    }
                    if (patternExists(basePattern + "RIGHT")) {
                        add(RightJoinMethodGenerator(fromCombination, joiningTable, toCombination, schemaPackage).forCombination(fromCombination))
                    }
                    if (patternExists(basePattern + "FULL")) {
                        add(FullJoinMethodGenerator(fromCombination, joiningTable, toCombination, schemaPackage).forCombination(fromCombination))
                    }
                }
            }
        }

        // Generate joinAliased and leftJoinAliased methods for SINGLE-TABLE combinations with each subquery
        // This allows inline subqueries to be joined to any existing query
        // We limit this to single tables to avoid code explosion (N tables × M subqueries creates N*M combinations)
        // Users can chain joins if needed: from(T1).join(T2).joinAliased(SQ) works via the T1+T2 builder
        val singleTableCombinations = uniqueCombinations.filter { combo ->
            combo.tables.size == 1 && !combo.tables.first().isSubquery
        }

        data.subqueries.forEach { subquery ->
            // For each single table, generate joinAliased methods to add this subquery
            singleTableCombinations.forEach { fromCombination ->
                // Create the target combination by adding the subquery
                val subqueryTable = TableInfo(
                    name = subquery.name,
                    sqlTableName = subquery.sqlAlias,
                    columns = subquery.columns.map { col ->
                        ColumnInfo(
                            propertyName = col.propertyName,
                            sqlColumnName = col.sqlColumnName,
                            kotlinType = col.kotlinType,
                            isNullable = false,
                            isAutoGenerated = false,
                            isPrimaryKey = false
                        )
                    },
                    isSubquery = true
                )

                val toCombination = QueryCombinationInfo(
                    baseTable = fromCombination.baseTable,
                    joinedTables = fromCombination.joinedTables + JoinedTableInfo(subqueryTable, JoinType.INNER)
                )

                // joinAliased methods go to the FROM combination file
                add(JoinAliasedMethodGenerator(fromCombination, subquery, toCombination).forCombination(fromCombination))
                add(LeftJoinAliasedMethodGenerator(fromCombination, subquery, toCombination).forCombination(fromCombination))
                add(RightJoinAliasedMethodGenerator(fromCombination, subquery, toCombination).forCombination(fromCombination))
                add(FullJoinAliasedMethodGenerator(fromCombination, subquery, toCombination).forCombination(fromCombination))
            }
        }

        // Select methods for each combination (deduplicate by table names)
        uniqueCombinations.forEach { combination ->
            add(SelectMethodGenerator(combination).forCombination(combination))
            // NOTE: Generic selectAs removed - replaced by marker-specific selectAs methods below
            // add(SelectAsMethodGenerator(combination))
            // Generate selectAll() for EACH table in the combination
            combination.tables.forEach { table ->
                add(SelectAllDirectMethodGenerator(combination, table, schemaPackage).forCombination(combination))

                // For subqueries, also generate selectAll(marker) overload
                if (table.isSubquery) {
                    val subqueryInfo = data.subqueries.find { it.name == table.name }
                    if (subqueryInfo != null) {
                        add(SelectAllSubqueryMarkerMethodGenerator(combination, subqueryInfo).forCombination(combination))
                    }
                }
            }
            add(SelectAllLambdaMethodGenerator(combination).forCombination(combination))
        }

        // NEW: Generate marker-specific selectAs with deduplication
        // Track which (queryCombination, fromType, markerName, toType) methods we've generated
        // IMPORTANT: Skip synthetic combinations (Table+Subquery) to avoid code explosion
        val generatedSelectAsMethods = mutableSetOf<String>()

        uniqueCombinations.filter { !it.isSynthetic }.forEach { queryCombination ->
            data.markerCombinations.forEach { markerCombination ->
                // Generate selectAs for each marker in the combination
                markerCombination.markers.indices.forEach { markerIndex ->
                    val marker = markerCombination.markers[markerIndex]

                    // Determine from/to types
                    val fromType = if (markerIndex == 0) {
                        "NoSelections"
                    } else {
                        "SelectionSet_" + markerCombination.markers.take(markerIndex).joinToString("_") { it.interfaceName }
                    }
                    val toType = "SelectionSet_" + markerCombination.markers.take(markerIndex + 1).joinToString("_") { it.interfaceName }

                    // Create unique signature for this method
                    val signature = "${queryCombination.builderClassName}|$fromType|${marker.interfaceName}|$toType"

                    if (signature !in generatedSelectAsMethods) {
                        generatedSelectAsMethods.add(signature)
                        add(SelectAsForMarkerGenerator(queryCombination, markerCombination, markerIndex).forCombination(queryCombination))
                    }
                }
            }
        }

        // Clause methods for each combination (deduplicate by table names)
        uniqueCombinations.forEach { combination ->
            add(WhereMethodGenerator(combination).forCombination(combination))
            add(OrderByMethodGenerator(combination).forCombination(combination))
            add(GroupByMethodGenerator(combination).forCombination(combination))
            add(LimitMethodGenerator(combination).forCombination(combination))
            add(OffsetMethodGenerator(combination).forCombination(combination))
        }

        // Subquery methods for each combination (deduplicate by table names)
        uniqueCombinations.forEach { combination ->
            add(ExistsMethodGenerator(combination).forCombination(combination))
            add(NotExistsMethodGenerator(combination).forCombination(combination))
            add(ScalarSubqueryMethodGenerator(combination).forCombination(combination))
        }

        // Generate generic execute methods for query combinations
        data.queryCombinations.forEach { combination ->
            add(ExecuteMethodGenerator(combination).forCombination(combination))
        }

        // Subquery-specific methods - go to infrastructure file
        data.subqueries.forEach { subquery ->
            add(FromAliasedMethodGenerator(subquery).forSubqueryInfrastructureFile())
            add(FromAliasedWithLambdaMethodGenerator(subquery).forSubqueryInfrastructureFile())
        }
    }

    /**
     * Phase 6: Result generators (7 types).
     */
    private fun createResultGenerators(): List<CodeGenerator> = buildList {
        // Query result classes - NO DEDUPLICATION (pattern-specific!)
        // Each join pattern needs its own result class with correct nullability
        data.queryCombinations.forEach { combination ->
            add(QueryResultClassGenerator(combination).forCombination(combination))
            add(ExecuteQueryResultMethodGenerator(combination).forCombination(combination))
        }

        // Selection result classes and execute methods (for marker combinations)
        data.markerCombinations.forEach { markerCombination ->
            // Generate SelectionResult class for this combination (once per combination) - infrastructure file
            add(SelectionResultClassGenerator(markerCombination).forSelectionSetsFile())
        }

        // Generate execute() methods with deduplication
        // Track which (queryCombination, phantomType, resultClass) methods we've generated
        // IMPORTANT: Skip synthetic combinations (Table+Subquery) to avoid code explosion
        val generatedExecuteMethods = mutableSetOf<String>()

        // Deduplicate by table names for marker-based execute methods
        val uniqueCombinationsForMarkers = data.queryCombinations
            .filter { !it.isSynthetic }
            .distinctBy { combination ->
                combination.tables.map { it.name }.joinToString("_")
            }

        uniqueCombinationsForMarkers.forEach { queryCombination ->
            data.markerCombinations.forEach { markerCombination ->
                val phantomType = "SelectionSet_" + markerCombination.markers.joinToString("_") { it.interfaceName }
                val signature = "${queryCombination.builderClassName}|$phantomType|${markerCombination.resultClassName}"

                if (signature !in generatedExecuteMethods) {
                    generatedExecuteMethods.add(signature)
                    add(ExecuteAggregateMethodGenerator(queryCombination, markerCombination).forCombination(queryCombination))
                }
            }
        }

        // Hybrid result classes and execute methods (for mixed marker + table selections)
        // Generate for each combination of: marker combination + single table with AllColumnsSelected
        // IMPORTANT: Skip synthetic combinations to avoid code explosion

        // First, generate unique hybrid result classes (deduplicated by marker + table combination)
        // Only include tables from non-synthetic combinations - infrastructure file
        val generatedHybridClasses = mutableSetOf<String>()
        data.markerCombinations.forEach { markerCombination ->
            // Get all unique tables across non-synthetic query combinations
            val allTables = data.queryCombinations
                .filter { !it.isSynthetic }
                .flatMap { it.tables }
                .distinctBy { it.name }
            allTables.forEach { table ->
                val classSignature = "${markerCombination.resultClassName}_${table.capitalizedName}"
                if (classSignature !in generatedHybridClasses) {
                    generatedHybridClasses.add(classSignature)
                    add(HybridResultClassGenerator(markerCombination, listOf(table)).forSelectionSetsFile())
                }
            }
        }

        // Then, generate execute methods for each non-synthetic query combination (deduplicate by table names)
        data.markerCombinations.forEach { markerCombination ->
            uniqueCombinationsForMarkers.forEach { queryCombination ->
                queryCombination.tables.forEach { table ->
                    // Generate execute method for this hybrid pattern
                    add(ExecuteHybridMethodGenerator(queryCombination, markerCombination, listOf(table), schemaPackage).forCombination(queryCombination))
                }
            }
        }

        // Subquery result accessors - go to subquery files
        data.subqueries.forEach { subquery ->
            add(SubqueryResultAccessorGenerator(subquery).forSubquery(subquery))
            add(SubqueryResultAccessorAllGenerator(subquery).forSubquery(subquery))
        }
    }

    /**
     * Phase 7: Subquery infrastructure generators (3 types).
     */
    private fun createSubqueryGenerators(): List<CodeGenerator> = buildList {
        data.subqueries.forEach { subquery ->
            add(SubqueryTableClassGenerator(subquery).forSubqueryInfrastructureFile())
        }

        // Always generate SubqueryRegistry (even if empty) since aliasAs() methods depend on it
        add(SubqueryRegistryGenerator(data.subqueries).forSubqueryInfrastructureFile())
    }
}
