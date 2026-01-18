package com.obabichev.kodama.compiler

import com.obabichev.kodama.compiler.metadata.KspMetadataRoot
import com.obabichev.kodama.compiler.parser.KotlinASTParser
import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File

class KodamaGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Check if KSP plugin is applied
        project.pluginManager.withPlugin("com.google.devtools.ksp") {
            project.logger.info("Kodama: KSP plugin detected")
        }

        // Create and register the extension
        val extension = project.extensions.create("kodama", KodamaExtension::class.java, project)

        // Register Phase 1 task: Structure-driven code generation
        val generateTableMetadataTask = project.tasks.register("generateKodamaTableMetadata", GenerateTableMetadataTask::class.java) {
            it.group = "kodama"
            it.description = "Generate table metadata (Phase 1: structure-driven, independent of usage patterns)"
        }

        // Register Phase 2 task: Pattern-driven code generation
        val generateQueryExtensionsTask = project.tasks.register("generateKodamaQueryExtensions", GenerateQueryExtensionsTask::class.java) {
            it.group = "kodama"
            it.description = "Generate query extensions (Phase 2: pattern-driven, based on usage in tests)"
        }

        // Configure task properties after project evaluation
        project.afterEvaluate {
            // Check if KSP plugin is applied
            val kspPluginApplied = project.plugins.hasPlugin("com.google.devtools.ksp")
            if (!kspPluginApplied) {
                throw IllegalStateException("""
                    |
                    |====================================================================================
                    |Kodama Error: KSP plugin is not applied!
                    |====================================================================================
                    |
                    |Kodama requires the KSP (Kotlin Symbol Processing) plugin to discover table definitions.
                    |
                    |Please add the KSP plugin to your build.gradle.kts:
                    |
                    |    plugins {
                    |        id("com.google.devtools.ksp") version "2.0.21-1.0.27"
                    |        id("com.obabichev.kodama") version "0.4.0"
                    |    }
                    |
                    |The KSP plugin must be applied BEFORE the Kodama plugin.
                    |====================================================================================
                    |
                """.trimMargin())
            }

            // Auto-configure KSP processor dependency
            // Check if kodama-ksp-processor is available as a project dependency (for internal builds)
            val kspProcessorDependency = if (project.rootProject.subprojects.any { it.name == "kodama-ksp-processor" }) {
                project.project(":kodama-ksp-processor")
            } else {
                "com.obabichev.kodama:kodama-ksp-processor:0.4.0"
            }

            try {
                project.dependencies.add("ksp", kspProcessorDependency)
                project.logger.info("Kodama: Added KSP processor dependency: $kspProcessorDependency")
            } catch (e: Exception) {
                project.logger.warn("Kodama: Could not automatically add KSP processor dependency. " +
                    "Please add manually: ksp(\"com.obabichev.kodama:kodama-ksp-processor:0.4.0\")")
            }

            val schemaDir = File(project.projectDir, "src/main/kotlin")
            val detectedSchemaPackage = if (extension.schemaPackage.get().isEmpty()) {
                detectPackageFromSourceFiles(schemaDir, project)
            } else {
                extension.schemaPackage.get()
            }

            val detectedGeneratedPackage = if (extension.generatedPackage.get().isEmpty()) {
                "$detectedSchemaPackage.generated"
            } else {
                extension.generatedPackage.get()
            }

            project.logger.info("Kodama: Using schema package: $detectedSchemaPackage")
            project.logger.info("Kodama: Using generated package: $detectedGeneratedPackage")

            // Configure Phase 1 task (Table Metadata)
            val buildDir = project.layout.buildDirectory.asFile.get()

            // Configure KSP options
            project.extensions.findByName("ksp")?.let { kspExtension ->
                try {
                    val argMethod = kspExtension.javaClass.getMethod("arg", String::class.java, String::class.java)
                    argMethod.invoke(kspExtension, "kodama.build.dir", buildDir.absolutePath)
                    project.logger.info("Kodama: Configured KSP option kodama.build.dir=${buildDir.absolutePath}")
                } catch (e: Exception) {
                    project.logger.warn("Kodama: Failed to configure KSP option: ${e.message}")
                }
            }
            val generatedDir = File(buildDir, "generated/kodama")
            val kspMetadataFile = File(buildDir, "generated/ksp/main/resources/kodama-ksp-metadata.json")
            val tableMetadataFile = File(generatedDir, "TableMetadata.kt")
            val runtimeMetadataJsonFile = File(buildDir, "generated/kodama/runtime-table-metadata.json")

            generateTableMetadataTask.configure {
                it.schemaPackage.set(detectedSchemaPackage)
                it.generatedPackage.set(detectedGeneratedPackage)
                it.kspMetadataFile.set(kspMetadataFile)
                it.outputFile.set(tableMetadataFile)
                it.runtimeMetadataJsonFile.set(runtimeMetadataJsonFile)
            }

            // Configure Phase 2 task (Query Extensions)
            val testDir = File(project.projectDir, "src/test/kotlin")
            generateQueryExtensionsTask.configure {
                it.schemaPackage.set(detectedSchemaPackage)
                it.generatedPackage.set(detectedGeneratedPackage)
                it.maxTableCount.set(extension.maxTableCount.get())
                it.kspMetadataFile.set(kspMetadataFile)
                it.tableMetadataFile.set(tableMetadataFile)  // Depends on Phase 1 output
                it.testFiles.setFrom(project.fileTree(testDir).matching { pattern ->
                    pattern.include("**/*.kt")
                })
                it.outputDirectory.set(generatedDir)  // Now outputs to directory, not single file
            }
        }

        // Configure task dependencies and source sets
        project.afterEvaluate {
            val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
            kotlinExtension?.let { kotlin ->
                // Add generated sources to test source set (generated code is for test queries)
                val generatedDir = File(project.layout.buildDirectory.asFile.get(), "generated/kodama")
                kotlin.sourceSets.findByName("test")?.kotlin?.srcDir(generatedDir)

                // Critical: Set up proper task dependencies for two-phase KSP-based code generation
                // Phase 1 (Structure-Driven):
                //   1. kspKotlin runs first (generates metadata JSON from source)
                //   2. compileKotlin runs second (compiles Table classes to bytecode)
                //   3. generateKodamaTableMetadata runs third (generates structure-driven code)
                // Phase 2 (Pattern-Driven):
                //   4. generateKodamaQueryExtensions runs fourth (scans tests, generates pattern-driven code)
                //   5. compileTestKotlin runs fifth (compiles tests using generated code)

                // Phase 1 dependencies: Make generateKodamaTableMetadata depend on compileKotlin
                // (needs compiled Table classes for runtime metadata extraction)
                project.tasks.findByName("compileKotlin")?.let { compileTask ->
                    generateTableMetadataTask.configure { it.dependsOn(compileTask) }
                }

                // Phase 1 dependencies: Make generateKodamaTableMetadata depend on kspKotlin
                // (needs KSP metadata JSON)
                project.tasks.findByName("kspKotlin")?.let { kspTask ->
                    generateTableMetadataTask.configure { it.dependsOn(kspTask) }
                }

                // Note on entity generation: Entity generation happens in kspKotlin,
                // but it needs runtime metadata from generateTableMetadataTask.
                // Since generateTableMetadataTask depends on kspKotlin's output, we have a circular dependency.
                // Solution: Run the build twice - first build generates runtime metadata, second uses it.
                // TODO: Move entity generation to a separate code generator task that runs after generateTableMetadataTask

                // Phase 2 dependencies: Make generateKodamaQueryExtensions depend on Phase 1
                // (needs TableMetadata.kt from Phase 1)
                generateQueryExtensionsTask.configure {
                    it.dependsOn(generateTableMetadataTask)
                }

                // Make compileTestKotlin depend on Phase 2
                // (tests need generated QueryExtensions.kt)
                project.tasks.findByName("compileTestKotlin")?.let { compileTestTask ->
                    compileTestTask.dependsOn(generateQueryExtensionsTask)
                }

                // Make kspTestKotlin depend on Phase 2
                // (KSP processes test sources which may reference generated code)
                project.tasks.findByName("kspTestKotlin")?.let { kspTestTask ->
                    kspTestTask.dependsOn(generateQueryExtensionsTask)
                }
            }
        }
    }

    /**
     * Auto-detect the package name by finding the first Table definition.
     *
     * Strategy:
     * 1. Try to load KSP metadata from previous build (if exists)
     * 2. Prefer "schema" package over "entity" package for stability
     * 3. Fall back to source file scanning if KSP metadata not available
     */
    private fun detectPackageFromSourceFiles(sourceDir: File, project: Project): String {
        // Strategy 1: Try KSP metadata first (from previous build)
        val kspMetadataFile = File(project.buildDir, "generated/ksp/main/resources/kodama-ksp-metadata.json")
        if (kspMetadataFile.exists()) {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val metadata = json.decodeFromString<KspMetadataRoot>(kspMetadataFile.readText())
                if (metadata.tables.isNotEmpty()) {
                    // Stable package selection: prefer "schema" package over "entity" package
                    // This ensures consistent generated code location across builds
                    val detectedPackage = metadata.tables
                        // First, try to find a table in a "schema" package
                        .firstOrNull { it.packageName.endsWith(".schema") }?.packageName
                        // If no schema package, use the first table's package (sorted for stability)
                        ?: metadata.tables.sortedBy { it.packageName }.first().packageName

                    project.logger.info("Kodama: Auto-detected schema package from KSP metadata: $detectedPackage")
                    if (metadata.tables.any { it.packageName != detectedPackage }) {
                        project.logger.info("Kodama: Note: Multiple packages detected. Using '$detectedPackage' for generated code.")
                        project.logger.info("Kodama: To specify a different package, configure: kodama { generatedPackage.set(\"your.package.generated\") }")
                    }
                    return detectedPackage
                }
            } catch (e: Exception) {
                project.logger.debug("Kodama: Could not load KSP metadata, falling back to source scanning: ${e.message}")
            }
        }

        // Strategy 2: Fall back to source file scanning
        if (!sourceDir.exists()) {
            project.logger.warn("Kodama: Source directory not found: $sourceDir, using default package")
            return "com.obabichev.kodama.schema"
        }

        val kotlinFiles = sourceDir.walkTopDown().filter { it.extension == "kt" }.toList()

        // Look for files with Table definitions using AST parsing
        val parser = KotlinASTParser()
        val discoveredPackages = mutableListOf<String>()
        try {
            for (file in kotlinFiles) {
                val content = file.readText()

                // Look for: object SomeName : Table(...)
                if (content.contains(": Table(")) {
                    try {
                        // Extract package declaration using AST parsing (zero regex!)
                        val ktFile = parser.parse(file)
                        val packageName = ktFile.packageFqName.asString()
                        if (packageName.isNotEmpty()) {
                            discoveredPackages.add(packageName)
                        }
                    } catch (e: Exception) {
                        project.logger.debug("Kodama: Failed to parse ${file.name} with AST, skipping: ${e.message}")
                    }
                }
            }
        } finally {
            parser.dispose()
        }

        // Prefer "schema" package for stability
        if (discoveredPackages.isNotEmpty()) {
            val selectedPackage = discoveredPackages
                .firstOrNull { it.endsWith(".schema") }
                ?: discoveredPackages.sorted().first()

            project.logger.info("Kodama: Auto-detected schema package from source files: $selectedPackage")
            if (discoveredPackages.distinct().size > 1) {
                project.logger.info("Kodama: Note: Multiple packages detected. Using '$selectedPackage' for generated code.")
                project.logger.info("Kodama: To specify a different package, configure: kodama { generatedPackage.set(\"your.package.generated\") }")
            }
            return selectedPackage
        }

        // Final fallback: try to detect any package declaration using AST parsing
        val fallbackParser = KotlinASTParser()
        try {
            for (file in kotlinFiles) {
                try {
                    val ktFile = fallbackParser.parse(file)
                    val packageName = ktFile.packageFqName.asString()
                    if (packageName.isNotEmpty()) {
                        project.logger.info("Kodama: Using package from ${file.name}: $packageName")
                        return packageName
                    }
                } catch (e: Exception) {
                    project.logger.debug("Kodama: Failed to parse ${file.name}, skipping: ${e.message}")
                }
            }
        } finally {
            fallbackParser.dispose()
        }

        project.logger.warn("Kodama: Could not auto-detect package, using default")
        return "com.obabichev.kodama.schema"
    }
}
