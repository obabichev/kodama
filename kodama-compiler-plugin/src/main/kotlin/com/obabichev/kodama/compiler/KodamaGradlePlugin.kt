package com.obabichev.kodama.compiler

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File

class KodamaGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register the code generation task - using new table-based generator
        val generateTask = project.tasks.register("generateKodamaExtensions", KodamaTableBasedCodegenTask::class.java) {
            it.group = "kodama"
            it.description = "Generate query extensions for Kodama based on Table definitions"
        }

        // Make compilation depend on code generation
        project.afterEvaluate {
            val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
            kotlinExtension?.let { kotlin ->
                // Add generated sources to TEST source set (not main), so test classes are available
                val generatedDir = File(project.layout.buildDirectory.asFile.get(), "generated/kodama")
                kotlin.sourceSets.findByName("test")?.kotlin?.srcDir(generatedDir)

                // Make test compilation depend on code generation
                project.tasks.named("compileTestKotlin").configure { compileTask ->
                    compileTask.dependsOn(generateTask)
                }
            }
        }
    }
}
