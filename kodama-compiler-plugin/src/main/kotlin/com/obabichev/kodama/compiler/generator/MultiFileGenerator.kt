package com.obabichev.kodama.compiler.generator

import java.io.File

/**
 * Generates multiple organized files instead of a single large QueryExtensions.kt file.
 *
 * This generator:
 * 1. Groups code generators by their target file
 * 2. For each target file:
 *    - Creates the directory structure
 *    - Generates package declaration
 *    - Collects and deduplicates imports
 *    - Generates all code from grouped generators
 *    - Writes to file
 *
 * File Organization:
 * ```
 * generated/
 * ├── _infrastructure/
 * │   ├── Markers.kt
 * │   ├── SelectionSets.kt
 * │   ├── JoinPatterns.kt
 * │   └── SubqueryInfrastructure.kt
 * ├── single_table/
 * │   ├── PersonQuery.kt
 * │   ├── OrderQuery.kt
 * │   └── ...
 * ├── combinations/
 * │   ├── PersonOrderQuery.kt
 * │   └── ...
 * ├── subqueries/
 * │   ├── UsersWithOrdersQuery.kt
 * │   └── ...
 * └── synthetic/
 *     ├── PersonUsersWithOrdersQuery.kt
 *     └── ...
 * ```
 *
 * Benefits:
 * - Parallel compilation (Gradle compiles multiple files simultaneously)
 * - Better IDE performance (smaller files, faster indexing)
 * - Easier debugging (clear file names, smaller stacktraces)
 * - Logical organization (related code grouped together)
 *
 * Example usage:
 * ```kotlin
 * val generator = MultiFileGenerator(
 *     outputDirectory = File("build/generated/kodama"),
 *     packageName = "com.example.generated",
 *     generators = allGenerators
 * )
 * generator.generate()
 * ```
 */
class MultiFileGenerator(
    private val outputDirectory: File,
    private val packageName: String,
    private val generators: List<CodeGenerator>
) {
    /**
     * Generate all files by grouping generators by target file.
     *
     * @return Map of file path → generated content (useful for testing/debugging)
     */
    fun generate(): Map<String, String> {
        // Group generators by their target file
        val generatorsByFile = generators.groupBy { it.targetFile() }

        println("Kodama Phase 2: Generating ${generatorsByFile.size} files...")

        val generatedFiles = mutableMapOf<String, String>()

        generatorsByFile.forEach { (relativePath, fileGenerators) ->
            val file = File(outputDirectory, relativePath)
            val content = generateFileContent(relativePath, fileGenerators)

            // Create parent directories if needed
            file.parentFile?.mkdirs()

            // Write file
            file.writeText(content)

            generatedFiles[relativePath] = content

            println("  ✓ $relativePath (${fileGenerators.size} generators, ${content.lines().size} lines)")
        }

        return generatedFiles
    }

    /**
     * Generate the complete content for a single file.
     *
     * @param relativePath The relative file path (e.g., "single_table/PersonQuery.kt")
     * @param fileGenerators The generators whose output goes in this file
     * @return Complete file content with package, imports, and generated code
     */
    private fun generateFileContent(
        relativePath: String,
        fileGenerators: List<CodeGenerator>
    ): String = buildString {
        // Determine package name based on subdirectory
        val filePackage = determinePackage(relativePath)

        // Package declaration
        appendLine("package $filePackage")
        appendLine()

        // Collect and deduplicate imports
        // Filter out imports from the same package (generated types don't need imports)
        // Also fix non-existent imports that were added by legacy generators
        val imports = fileGenerators
            .flatMap { it.requiredImports() }
            .distinct()
            .map { import ->
                // Replace invalid imports with correct ones
                when (import) {
                    "com.obabichev.kodama.schema.Column" -> "com.obabichev.kodama.components.Column"
                    else -> import
                }
            }
            .filterNot { it.startsWith(filePackage) }  // Don't import from same package
            .filterNot { it == "com.obabichev.kodama.query.SubqueryRegistry" }  // Generated in same package
            .filterNot { it == "com.obabichev.kodama.query.JoinPattern" }  // Generated in same package
            .distinct()  // Deduplicate again after replacements
            .sorted()

        // Add imports
        if (imports.isNotEmpty()) {
            imports.forEach { appendLine("import $it") }
            appendLine()
        }

        // Generate code from each generator
        fileGenerators.forEach { generator ->
            val code = generator.generate()
            if (code.isNotBlank()) {
                appendLine(code)
                appendLine()
            }
        }
    }

    /**
     * Determine the package name for a given file path.
     *
     * Examples:
     * - `_infrastructure/Markers.kt` → `com.example.generated`
     * - `single_table/PersonQuery.kt` → `com.example.generated`
     * - `combinations/PersonOrderQuery.kt` → `com.example.generated`
     *
     * Note: We keep everything in the same package to avoid import complexity.
     * Subdirectories are for file organization only, not package structure.
     *
     * @param relativePath The relative file path
     * @return The package name for this file
     */
    private fun determinePackage(relativePath: String): String {
        // For now, all files use the same base package
        // This avoids import complexity and keeps backward compatibility
        return packageName
    }

    /**
     * Get statistics about the generation.
     *
     * @return Map of statistics (useful for logging/monitoring)
     */
    fun getStatistics(): Map<String, Any> {
        val generatorsByFile = generators.groupBy { it.targetFile() }

        return mapOf(
            "totalFiles" to generatorsByFile.size,
            "totalGenerators" to generators.size,
            "infrastructureFiles" to generatorsByFile.keys.count { it.startsWith("_infrastructure/") },
            "singleTableFiles" to generatorsByFile.keys.count { it.startsWith("single_table/") },
            "combinationFiles" to generatorsByFile.keys.count { it.startsWith("combinations/") },
            "subqueryFiles" to generatorsByFile.keys.count { it.startsWith("subqueries/") },
            "syntheticFiles" to generatorsByFile.keys.count { it.startsWith("synthetic/") },
            "avgGeneratorsPerFile" to generators.size / generatorsByFile.size.toFloat()
        )
    }
}
