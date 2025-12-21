package com.obabichev.kodama.compiler

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File

class KodamaGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create and register the extension
        val extension = project.extensions.create("kodama", KodamaExtension::class.java, project)

        // Register the code generation task - using new table-based generator
        val generateTask = project.tasks.register("generateKodamaExtensions", KodamaTableBasedCodegenTask::class.java) {
            it.group = "kodama"
            it.description = "Generate query extensions for Kodama based on Table definitions"
        }

        // Configure task properties after project evaluation
        project.afterEvaluate {
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

            generateTask.configure {
                it.schemaPackage.set(detectedSchemaPackage)
                it.generatedPackage.set(detectedGeneratedPackage)
            }
        }

        // Make compilation depend on code generation
        project.afterEvaluate {
            val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
            kotlinExtension?.let { kotlin ->
                // Add generated sources to TEST source set (not main), so test classes are available
                val generatedDir = File(project.layout.buildDirectory.asFile.get(), "generated/kodama")
                kotlin.sourceSets.findByName("test")?.kotlin?.srcDir(generatedDir)

                // Make BOTH main and test compilation depend on code generation
                // This ensures IntelliJ's "Build Project" triggers regeneration
                project.tasks.findByName("compileKotlin")?.let { compileTask ->
                    compileTask.dependsOn(generateTask)
                }
                project.tasks.named("compileTestKotlin").configure { compileTask ->
                    compileTask.dependsOn(generateTask)
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
