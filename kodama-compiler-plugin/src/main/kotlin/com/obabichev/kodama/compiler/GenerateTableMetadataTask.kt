package com.obabichev.kodama.compiler

import com.obabichev.kodama.compiler.metadata.KspMetadataLoader
import com.obabichev.kodama.compiler.metadata.RuntimeMetadataExtractor
import com.obabichev.kodama.compiler.metadata.TableMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Phase 1: Structure-Driven Code Generation
 *
 * Generates code based solely on table structure (after KSP + runtime metadata extraction).
 * This code is independent of query usage patterns and can be cached when only test patterns change.
 *
 * Generates:
 * - from() functions for each table
 * - Table accessors (person.name, person.age)
 * - AllColumnsMarker classes
 * - Single-table result accessors
 * - INSERT methods
 * - ORDER BY accessors
 * - GROUP BY accessors
 *
 * Output: TableMetadata.kt (~2,000-3,000 lines)
 */
@CacheableTask
abstract class GenerateTableMetadataTask : DefaultTask() {

    @get:Input
    abstract val schemaPackage: Property<String>

    @get:Input
    abstract val generatedPackage: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kspMetadataFile: RegularFileProperty

    /**
     * Load table metadata using KSP + Runtime metadata extraction.
     */
    private fun loadTableMetadata(): List<TableMetadata> {
        logger.lifecycle("Kodama Phase 1: Loading table metadata...")

        // Step 1: Load KSP metadata from JSON
        val metadataLoader = KspMetadataLoader()
        val kspMetadataPath = kspMetadataFile.get().asFile

        if (!kspMetadataPath.exists()) {
            error("""
                Kodama: KSP metadata file not found at ${kspMetadataPath.absolutePath}

                The Kodama plugin should automatically configure KSP, but it seems KSP hasn't run yet.

                Troubleshooting:
                1. Make sure you have defined Table objects: object MyTable : Table("...")
                2. Try running: ./gradlew kspKotlin --rerun-tasks
                3. If KSP plugin is missing, add to build.gradle.kts:
                   id("com.google.devtools.ksp") version "2.0.21-1.0.27"
                4. Check that mavenCentral() is in your repositories (for KSP processor)
            """.trimIndent())
        }

        val kspTables = metadataLoader.loadMetadata(kspMetadataPath)

        if (kspTables.isEmpty()) {
            error("""
                Kodama: No tables found in KSP metadata.

                Make sure you have defined Table objects: object MyTable : Table("...")
            """.trimIndent())
        }

        logger.lifecycle("Kodama Phase 1: Found ${kspTables.size} tables from KSP")

        // Step 2: Extract runtime metadata from compiled classes
        val buildDir = project.layout.buildDirectory.get().asFile
        val classOutputDir = buildDir.resolve("classes/kotlin/main")

        if (!classOutputDir.exists()) {
            error("""
                Kodama: Compiled classes not found at ${classOutputDir.absolutePath}

                Make sure compileKotlin task has run successfully.
            """.trimIndent())
        }

        // Build full classpath
        val classpathFiles = mutableListOf<File>()
        classpathFiles.add(classOutputDir)

        val runtimeClasspath = project.configurations.findByName("runtimeClasspath")
        runtimeClasspath?.resolvedConfiguration?.resolvedArtifacts?.forEach { artifact ->
            classpathFiles.add(artifact.file)
        }

        val extractor = RuntimeMetadataExtractor(classOutputDir, classpathFiles)

        val tableMetadata = try {
            extractor.extractAllTables(kspTables)
        } catch (e: Exception) {
            extractor.close()
            error("Kodama: Failed to extract table metadata: ${e.message}\n${e.stackTraceToString()}")
        } finally {
            extractor.close()
        }

        logger.lifecycle("Kodama Phase 1: Extracted metadata for ${tableMetadata.size} tables")

        return tableMetadata
    }

    @TaskAction
    fun generate() {
        val genPkg = generatedPackage.get()
        val schemaPkg = schemaPackage.get()

        // Load table metadata
        val tables = loadTableMetadata()

        // Generate Phase 1 code
        val generatedCode = generateTableMetadataCode(genPkg, schemaPkg, tables)

        // Write to file
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(generatedCode)
        }

        logger.lifecycle("Kodama Phase 1: Generated TableMetadata.kt with ${tables.size} tables")
    }

    private fun generateTableMetadataCode(
        genPkg: String,
        schemaPkg: String,
        tables: List<TableMetadata>
    ): String = buildString {
        // Package and imports
        appendLine("package $genPkg")
        appendLine()
        appendLine("import com.obabichev.kodama.schema.*")
        appendLine("import com.obabichev.kodama.query.*")
        appendLine("import com.obabichev.kodama.execute.*")
        appendLine("import com.obabichev.kodama.insert.*")
        appendLine("import com.obabichev.kodama.components.*")
        appendLine("import $schemaPkg.*")
        appendLine("import java.sql.ResultSet")
        appendLine()
        appendLine("// ============================================================================")
        appendLine("// Phase 1: Structure-Driven Generation")
        appendLine("// Generated based on table structure (independent of query usage patterns)")
        appendLine("// ============================================================================")
        appendLine()

        // Type aliases
        appendLine("// ============================================================================")
        appendLine("// Type Aliases")
        appendLine("// ============================================================================")
        appendLine()
        // NOTE: NoSelections typealias and AfterFromQueryBuilder classes are generated in Phase 2
        //       Only from() entry point functions are generated here in Phase 1 for immediate availability
        appendLine()

        // NOTE: Table accessor classes (PersonAccessor, etc.) are generated in Phase 2 (QueryExtensions.kt)
        // because they return TypedColumn with type markers that are pattern-driven

        // AllColumnsMarker classes
        appendLine("// ============================================================================")
        appendLine("// AllColumnsMarker Classes")
        appendLine("// ============================================================================")
        appendLine()
        appendLine("sealed class AllColumnsMarker(val table: Table) : com.obabichev.kodama.components.SelectionMarker {")
        appendLine("    fun asTableAllSelection(): TableAllSelection {")
        appendLine("        return TableAllSelection(table, table.allColumns())")
        appendLine("    }")
        appendLine("}")
        appendLine()

        tables.forEach { table ->
            appendLine("class ${table.name}AllMarker(table: Table) : AllColumnsMarker(table)")
        }
        appendLine()

        // Extension functions for .all()
        tables.forEach { table ->
            appendLine("fun $schemaPkg.${table.name}.all() = ${table.name}AllMarker(this)")
        }
        appendLine()

        // Single-table result accessors
        appendLine("// ============================================================================")
        appendLine("// Single-Table Result Accessors")
        appendLine("// ============================================================================")
        appendLine()

        tables.forEach { table ->
            // Generate NON-NULLABLE accessor variant
            // Used for INNER JOINs and base tables - respects column's intrinsic nullability
            appendLine("/**")
            appendLine(" * Result accessor for all columns of ${table.name} (Non-nullable variant)")
            appendLine(" * Used for:")
            appendLine(" * - Base table in `from(${table.name})`")
            appendLine(" * - INNER JOIN tables")
            appendLine(" * Properties return non-nullable types unless column is defined as nullable in schema.")
            appendLine(" */")
            appendLine("class ${table.name}ResultAccessor_All_NonNull(")
            appendLine("    resultSet: ResultSet,")
            appendLine("    relations: RelationsContainer,")
            appendLine("    selectedColumns: List<Column<*>>")
            appendLine(") : TableResultAccessor(resultSet, relations, selectedColumns) {")

            table.columns.forEach { column ->
                // Respect column's intrinsic nullability
                // If column is defined as nullable in schema, keep it nullable
                // Otherwise, return non-nullable type
                appendLine("    val ${column.propertyName}: ${column.kotlinType}")
                appendLine("        get() = readColumn($schemaPkg.${table.name}.${column.propertyName}) as ${column.kotlinType}")
                appendLine()
            }

            appendLine("}")
            appendLine()

            // Generate NULLABLE accessor variant
            // Used for OUTER JOINs where table can be missing
            appendLine("/**")
            appendLine(" * Result accessor for all columns of ${table.name} (Nullable variant)")
            appendLine(" * Used for:")
            appendLine(" * - LEFT JOIN (right table)")
            appendLine(" * - RIGHT JOIN (left table)")
            appendLine(" * - FULL OUTER JOIN (both tables)")
            appendLine(" * All properties return nullable types because table rows can be NULL.")
            appendLine(" */")
            appendLine("class ${table.name}ResultAccessor_All_Nullable(")
            appendLine("    resultSet: ResultSet,")
            appendLine("    relations: RelationsContainer,")
            appendLine("    selectedColumns: List<Column<*>>")
            appendLine(") : TableResultAccessor(resultSet, relations, selectedColumns) {")

            table.columns.forEach { column ->
                // Force all properties to nullable
                val nullableType = if (column.kotlinType.endsWith("?")) column.kotlinType else "${column.kotlinType}?"
                appendLine("    val ${column.propertyName}: $nullableType")
                appendLine("        get() = readColumn($schemaPkg.${table.name}.${column.propertyName}) as $nullableType")
                appendLine()
            }

            appendLine("}")
            appendLine()
        }

        // INSERT methods
        appendLine("// ============================================================================")
        appendLine("// INSERT Methods")
        appendLine("// ============================================================================")
        appendLine()

        tables.forEach { table ->
            // Filter out auto-generated columns from INSERT
            val insertColumns = table.columns.filter { !it.isAutoGenerated }

            appendLine("/**")
            appendLine(" * Type-safe INSERT for ${table.name} table")
            appendLine(" */")
            appendLine("fun $schemaPkg.${table.name}.insert(")
            appendLine("    transaction: JdbcTransaction,")

            insertColumns.forEachIndexed { index, column ->
                val comma = if (index < insertColumns.size - 1) "," else ""
                appendLine("    ${column.propertyName}: ${column.kotlinType}$comma")
            }

            appendLine("): InsertResult {")
            appendLine("    val table = this")
            appendLine("    val insert = InsertStatement(")
            appendLine("        table = table,")
            appendLine("        columns = listOf(${insertColumns.joinToString(", ") { "table.${it.propertyName}" }}),")
            appendLine("        values = listOf(${insertColumns.joinToString(", ") { it.propertyName }})")
            appendLine("    )")
            appendLine("    return transaction.executeInsert(insert)")
            appendLine("}")
            appendLine()
        }

        // ORDER BY accessors
        appendLine("// ============================================================================")
        appendLine("// ORDER BY Accessors")
        appendLine("// ============================================================================")
        appendLine()

        tables.forEach { table ->
            appendLine("class ${table.name}OrderByAccessor(private val tableAccessor: TableAccessor) {")

            table.columns.forEach { column ->
                appendLine("    val ${column.propertyName} get() = OrderByColumn($schemaPkg.${table.name}.${column.propertyName})")
            }

            appendLine("}")
            appendLine()
        }

        // GROUP BY accessors
        appendLine("// ============================================================================")
        appendLine("// GROUP BY Accessors")
        appendLine("// ============================================================================")
        appendLine()

        tables.forEach { table ->
            appendLine("class ${table.name}GroupByAccessor(private val tableAccessor: TableAccessor) {")

            table.columns.forEach { column ->
                appendLine("    val ${column.propertyName} get() = $schemaPkg.${table.name}.${column.propertyName}")
            }

            appendLine("}")
            appendLine()
        }

        // from() Entry Point Functions (Phantom Types)
        appendLine("// ============================================================================")
        appendLine("// from() Entry Point Functions (Phantom Types)")
        appendLine("// ============================================================================")
        appendLine()

        tables.forEach { table ->
            appendLine("/**")
            appendLine(" * Create a query starting from ${table.name} table using phantom types.")
            appendLine(" * Returns QueryBuilder_1<${table.name}Marker, NoSelections> for type-safe single-table queries.")
            appendLine(" */")
            appendLine("fun from(table: $schemaPkg.${table.name}): $genPkg.QueryBuilder_1<$genPkg.${table.name}Marker, $genPkg.NoSelections> {")
            appendLine("    val state = QueryState()")
            appendLine("    state._from = state.relations.relation(table)")
            appendLine("    return $genPkg.QueryBuilder_1(state)")
            appendLine("}")
            appendLine()
        }
    }
}
