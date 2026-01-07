package com.obabichev.kodama.compiler

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
            val generatedDir = File(buildDir, "generated/kodama")
            val kspMetadataFile = File(buildDir, "generated/ksp/main/resources/kodama-ksp-metadata.json")
            val tableMetadataFile = File(generatedDir, "TableMetadata.kt")
            val queryExtensionsFile = File(generatedDir, "QueryExtensions.kt")

            generateTableMetadataTask.configure {
                it.schemaPackage.set(detectedSchemaPackage)
                it.generatedPackage.set(detectedGeneratedPackage)
                it.kspMetadataFile.set(kspMetadataFile)
                it.outputFile.set(tableMetadataFile)
            }

            // Configure Phase 2 task (Query Extensions)
            val testDir = File(project.projectDir, "src/test/kotlin")
            generateQueryExtensionsTask.configure {
                it.schemaPackage.set(detectedSchemaPackage)
                it.generatedPackage.set(detectedGeneratedPackage)
                it.kspMetadataFile.set(kspMetadataFile)
                it.tableMetadataFile.set(tableMetadataFile)  // Depends on Phase 1 output
                it.testFiles.setFrom(project.fileTree(testDir).matching { pattern ->
                    pattern.include("**/*.kt")
                })
                it.outputFile.set(queryExtensionsFile)
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
     * Auto-detect the package name by finding the first Table definition
     */
    private fun detectPackageFromSourceFiles(sourceDir: File, project: Project): String {
        if (!sourceDir.exists()) {
            project.logger.warn("Kodama: Source directory not found: $sourceDir, using default package")
            return "com.obabichev.kodama.schema"
        }

        val kotlinFiles = sourceDir.walkTopDown().filter { it.extension == "kt" }

        for (file in kotlinFiles) {
            val content = file.readText()

            // Look for: object SomeName : Table(...)
            if (content.contains(": Table(")) {
                // Extract package declaration
                val packagePattern = """package\s+([\w.]+)""".toRegex()
                val packageMatch = packagePattern.find(content)
                if (packageMatch != null) {
                    val detectedPackage = packageMatch.groupValues[1]
                    project.logger.info("Kodama: Auto-detected schema package from ${file.name}: $detectedPackage")
                    return detectedPackage
                }
            }
        }

        // Fallback: try to detect any package declaration
        for (file in kotlinFiles) {
            val content = file.readText()
            val packagePattern = """package\s+([\w.]+)""".toRegex()
            val packageMatch = packagePattern.find(content)
            if (packageMatch != null) {
                val detectedPackage = packageMatch.groupValues[1]
                project.logger.info("Kodama: Using package from ${file.name}: $detectedPackage")
                return detectedPackage
            }
        }

        project.logger.warn("Kodama: Could not auto-detect package, using default")
        return "com.obabichev.kodama.schema"
    }
}
