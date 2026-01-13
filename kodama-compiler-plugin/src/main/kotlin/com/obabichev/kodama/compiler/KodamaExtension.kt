package com.obabichev.kodama.compiler

import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Extension for configuring Kodama code generation.
 *
 * Example usage in build.gradle.kts:
 * ```kotlin
 * kodama {
 *     schemaPackage.set("com.mycompany.myproject.schema")
 *     generatedPackage.set("com.mycompany.myproject.generated")
 * }
 * ```
 *
 * If not configured, Kodama will auto-detect packages from your source files.
 */
abstract class KodamaExtension(project: Project) {

    /**
     * Package where table definitions (objects extending Table) are located.
     *
     * Default: Auto-detected from source files
     * Example: "com.mycompany.myproject.schema"
     */
    abstract val schemaPackage: Property<String>

    /**
     * Package where generated query extensions will be placed.
     *
     * Default: Auto-detected (prefers ".schema" package if multiple packages detected) + ".generated"
     * Example: "com.mycompany.myproject.generated"
     *
     * When tables are in multiple packages (e.g., both schema and entity packages),
     * Kodama will prefer the ".schema" package for stability. To override this behavior,
     * explicitly set this property:
     * ```kotlin
     * kodama {
     *     generatedPackage.set("com.mycompany.myproject.entity.generated")
     * }
     * ```
     */
    abstract val generatedPackage: Property<String>

    /**
     * Maximum number of tables supported in a single query.
     *
     * Determines how many QueryBuilder_N classes are generated (QueryBuilder_1 through QueryBuilder_N).
     * Higher values allow more complex queries but increase generated code size.
     *
     * Default: 5 (covers 99.99% of real-world queries)
     * Typical usage: 1-3 tables in most queries
     * Recommended max: 10 (each additional N adds ~1,000 lines of generated code)
     *
     * Example: Set to 7 for queries joining up to 7 tables
     * ```kotlin
     * kodama {
     *     maxTableCount.set(7)
     * }
     * ```
     */
    abstract val maxTableCount: Property<Int>

    init {
        // Defaults will be set after auto-detection in the plugin
        schemaPackage.convention("")
        generatedPackage.convention("")
        maxTableCount.convention(5)
    }
}
