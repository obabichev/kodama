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
     * Default: Same as schemaPackage + ".generated"
     * Example: "com.mycompany.myproject.generated"
     */
    abstract val generatedPackage: Property<String>

    init {
        // Defaults will be set after auto-detection in the plugin
        schemaPackage.convention("")
        generatedPackage.convention("")
    }
}
