package com.obabichev.kodama.compiler

import com.obabichev.kodama.compiler.builder.*
import com.obabichev.kodama.compiler.generator.KodamaFileGenerator
import com.obabichev.kodama.compiler.model.*
import java.io.File

/**
 * Main orchestrator for the Kodama code generation process.
 *
 * This class coordinates the entire code generation pipeline:
 * 1. Scan source files for patterns
 * 2. Build domain models
 * 3. Generate code
 * 4. Write output
 *
 * Benefits of this architecture:
 * - **Testability**: Each component can be tested independently
 * - **Maintainability**: Clear separation of concerns
 * - **Extensibility**: Easy to add new generators or scanners
 * - **Clarity**: The flow is explicit and easy to follow
 *
 * Example usage:
 * ```kotlin
 * val orchestrator = CodegenOrchestrator(
 *     schemaPackage = "com.example.schema",
 *     generatedPackage = "com.example.generated",
 *     scanners = listOf(AggregateScanner())
 * )
 *
 * val model = orchestrator.buildModelFromFiles(
 *     schemaDir = File("src/main/kotlin/schema"),
 *     testDir = File("src/test/kotlin")
 * )
 *
 * val generatedCode = orchestrator.generateCode(model)
 * orchestrator.writeToFile(generatedCode, outputFile)
 * ```
 */
class CodegenOrchestrator(
    private val schemaPackage: String,
    private val generatedPackage: String,
    private val scanners: List<SelectionPatternScanner> = listOf(AggregateScanner())
) {

    private val fileGenerator = KodamaFileGenerator()
    private val modelBuilder = CodeGenerationModelBuilder()

    /**
     * Main entry point: Build model from source files and generate code.
     *
     * @param schemaDir Directory containing table definitions
     * @param testDir Directory containing test files with query patterns
     * @return Generated Kotlin source code
     */
    fun execute(schemaDir: File, testDir: File): String {
        val model = buildModelFromFiles(schemaDir, testDir)
        return generateCode(model)
    }

    /**
     * Build the domain model from source files.
     *
     * This method:
     * 1. Scans schema files for table definitions
     * 2. Scans test files for query patterns
     * 3. Constructs domain models
     *
     * @param schemaDir Directory with table definitions
     * @param testDir Directory with test files
     * @return Complete code generation model
     */
    fun buildModelFromFiles(schemaDir: File, testDir: File): CodeGenerationModel {
        // Step 1: Scan for tables
        val tableData = scanTables(schemaDir)

        // Step 2: Build table models
        val tableBuilder = TableModelBuilder()
        val tables = tableData.map { tableBuilder.build(it) }

        // Step 3: Scan for query and selection patterns
        val (queryPatterns, selectionPatterns, tableNameMap) = scanPatterns(testDir, tables)

        // Step 4: Build complete model
        return modelBuilder.build(
            CodeGenerationBuildInput(
                tables = tables,
                queryPatterns = queryPatterns,
                selectionPatterns = selectionPatterns,
                schemaPackage = schemaPackage,
                generatedPackage = generatedPackage
            )
        )
    }

    /**
     * Generate code from a domain model.
     *
     * This method is pure - it only transforms models to code.
     * It can be easily tested with mock models.
     *
     * @param model The code generation model
     * @return Generated Kotlin source code
     */
    fun generateCode(model: CodeGenerationModel): String {
        return fileGenerator.generate(model)
    }

    /**
     * Write generated code to a file.
     *
     * @param code The generated code
     * @param outputFile The target file
     */
    fun writeToFile(code: String, outputFile: File) {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(code)
    }

    /**
     * Scan schema directory for table definitions.
     *
     * This extracts:
     * - Table names
     * - Column names and types
     * - Nullability information
     *
     * @return List of table build inputs
     */
    private fun scanTables(schemaDir: File): List<TableBuildInput> {
        val tables = mutableListOf<TableBuildInput>()
        val tableToProperties = mutableMapOf<String, MutableList<String>>()
        val tableToPropertyTypes = mutableMapOf<String, MutableMap<String, String>>()
        val tableToPropertyNullability = mutableMapOf<String, MutableMap<String, Boolean>>()

        // Pattern to match: object TableName : Table("table_name") { ... }
        val tablePattern = """object\s+(\w+)\s*:\s*(?:Entity)?Table(?:<[^>]+>)?\s*\(\s*"([^"]+)"\s*\)""".toRegex()

        // Pattern for columns: val columnName = type("db_column_name", ...).nullable()
        val columnPattern = """val\s+(\w+)\s*=\s*(\w+)\s*\([^)]*\)(?:\.nullable\(\))?(?:\.primaryKey\(\))?""".toRegex()

        val schemaFiles = schemaDir.walkTopDown().filter { it.extension == "kt" }

        schemaFiles.forEach { file ->
            val content = file.readText()

            // Find all table definitions
            tablePattern.findAll(content).forEach { match ->
                val tableName = match.groupValues[1]

                // Find columns for this table
                val properties = mutableListOf<String>()
                val propertyTypes = mutableMapOf<String, String>()
                val propertyNullability = mutableMapOf<String, Boolean>()

                columnPattern.findAll(content).forEach { colMatch ->
                    val propName = colMatch.groupValues[1]
                    val typeName = colMatch.groupValues[2]
                    val isNullable = colMatch.value.contains(".nullable()")

                    properties.add(propName)
                    propertyTypes[propName] = mapColumnTypeToKotlinType(typeName)
                    propertyNullability[propName] = isNullable
                }

                if (properties.isNotEmpty()) {
                    tables.add(
                        TableBuildInput(
                            tableName = tableName,
                            properties = properties,
                            propertyTypes = propertyTypes,
                            propertyNullability = propertyNullability,
                            schemaPackage = schemaPackage
                        )
                    )
                }
            }
        }

        return tables
    }

    /**
     * Scan test directory for query and selection patterns.
     *
     * @return Triple of query patterns, selection patterns, and table name map
     */
    private fun scanPatterns(
        testDir: File,
        tables: List<TableModel>
    ): Triple<
            Map<List<String>, Set<List<String>>>,
            Map<List<String>, Set<SelectionPattern>>,
            Map<String, String>
            > {
        // Build table name map for case-insensitive lookups
        val tableNameMap = tables.associate { it.name.lowercase() to it.name }

        val queryPatterns = mutableMapOf<List<String>, MutableSet<List<String>>>()
        val selectionPatterns = mutableMapOf<List<String>, MutableSet<SelectionPattern>>()

        val testFiles = testDir.walkTopDown().filter { it.extension == "kt" }

        testFiles.forEach { file ->
            val content = file.readText()

            // Scan using each scanner
            scanners.forEach { scanner ->
                val patterns = scanner.scanFile(content, tableNameMap)
                patterns.forEach { pattern ->
                    selectionPatterns.getOrPut(pattern.tables) { mutableSetOf() }.add(pattern)
                }
            }

            // Also scan for basic query patterns (from/join combinations)
            scanQueryPatterns(content, tableNameMap, queryPatterns)
        }

        return Triple(queryPatterns, selectionPatterns, tableNameMap)
    }

    /**
     * Scan for basic query patterns (table combinations).
     */
    private fun scanQueryPatterns(
        content: String,
        tableNameMap: Map<String, String>,
        queryPatterns: MutableMap<List<String>, MutableSet<List<String>>>
    ) {
        // Pattern to match query chains
        val queryPattern = """query\s*\(\s*\)(?:(?!\.(?:build|execute)\()[\s\S])*?\.(?:execute|build)\(""".toRegex()

        queryPattern.findAll(content).forEach { match ->
            val queryChain = match.value

            // Extract tables
            val tables = linkedSetOf<String>()
            val tableRefPattern = """(?:from|fromAliased|join|joinAliased|leftJoin|leftJoinAliased)\s*\(\s*(\w+)""".toRegex()

            tableRefPattern.findAll(queryChain).forEach { tableMatch ->
                val tableRef = tableMatch.groupValues[1]
                val tableName = tableNameMap[tableRef.lowercase()] ?: tableRef
                tables.add(tableName)
            }

            if (tables.isNotEmpty()) {
                queryPatterns.getOrPut(tables.toList()) { mutableSetOf() }.add(emptyList())
            }
        }
    }

    /**
     * Map column type name to Kotlin type.
     */
    private fun mapColumnTypeToKotlinType(typeName: String): String {
        return when (typeName.lowercase()) {
            "integer" -> "Int"
            "varchar" -> "String"
            "boolean" -> "Boolean"
            "smallint" -> "Short"
            "bigint" -> "Long"
            "decimal" -> "java.math.BigDecimal"
            "real" -> "Float"
            "doubleprecision" -> "Double"
            else -> "Any"
        }
    }
}
