buildscript {
    dependencies {
        classpath("commons-codec:commons-codec:1.15")
    }
}

plugins {
    alias(libs.plugins.jvm) apply true
}

dependencies {
}

repositories {
    mavenLocal()
    mavenCentral()
}

// Common properties for all modules
// Note: group and version are inherited from gradle.properties
allprojects {
    // group and version are set in gradle.properties
}

subprojects {
    if (name == "kodama-bom") return@subprojects

    apply(plugin = rootProject.libs.plugins.jvm.get().pluginId)
}

// Task to publish all modules (core, KSP processor, and compiler plugin)
// The compiler plugin is an included build, so we need to invoke it explicitly
tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes all modules (including compiler plugin and KSP processor) to Maven Local"

    dependsOn(gradle.includedBuild("kodama-compiler-plugin").task(":publishToMavenLocal"))
    dependsOn(":kodama-core:publishToMavenLocal")
    dependsOn(":kodama-ksp-processor:publishToMavenLocal")
}

tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes all modules (including compiler plugin and KSP processor) to configured repositories"

    dependsOn(gradle.includedBuild("kodama-compiler-plugin").task(":publish"))
    dependsOn(":kodama-core:publish")
    dependsOn(":kodama-ksp-processor:publish")
}

// Task to create a Maven Central bundle with checksums for manual upload
// This is the RECOMMENDED approach - uses already-signed artifacts from Maven Local
tasks.register("createCentralBundle") {
    group = "publishing"
    description = "Creates a ZIP bundle with checksums (uses Maven Local - RECOMMENDED)"

    val bundleDir = layout.buildDirectory.dir("central-bundle")
    val outputZip = layout.buildDirectory.file("distributions/kodama-${project.version}-bundle.zip")

    doLast {
        val bundleDirPath = bundleDir.get().asFile
        val mavenLocalRepo = file("${System.getProperty("user.home")}/.m2/repository")
        val sourceDir = file("$mavenLocalRepo/com/obabichev/kodama")

        // Check if artifacts exist in Maven Local
        if (!sourceDir.exists()) {
            throw GradleException(
                "Artifacts not found in Maven Local repository.\n" +
                "Please run: ./gradlew publishAllToMavenLocal\n" +
                "Then run this task again."
            )
        }

        // Clean and create bundle directory
        delete(bundleDirPath)
        mkdir(bundleDirPath)

        // Copy artifacts from Maven Local
        copy {
            from(sourceDir)
            into("${bundleDirPath}/com/obabichev/kodama")
            exclude("**/*-local.xml")
            exclude("**/0.2.0/**")  // Exclude old versions
        }

        // Generate MD5 and SHA1 checksums for all artifacts
        fileTree(bundleDirPath).matching {
            include("**/*.jar", "**/*.pom", "**/*.module", "**/*.asc")
        }.forEach { file ->
            // Generate MD5
            val md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(file.readBytes())
            file("${file.absolutePath}.md5").writeText(md5)

            // Generate SHA1
            val sha1 = org.apache.commons.codec.digest.DigestUtils.sha1Hex(file.readBytes())
            file("${file.absolutePath}.sha1").writeText(sha1)
        }

        // Count checksums
        val checksumCount = fileTree(bundleDirPath).matching {
            include("**/*.md5", "**/*.sha1")
        }.files.size

        println("Generated $checksumCount checksum files")

        // Create ZIP bundle
        mkdir(outputZip.get().asFile.parentFile)
        ant.withGroovyBuilder {
            "zip"("destfile" to outputZip.get().asFile.absolutePath) {
                "fileset"("dir" to bundleDirPath)
            }
        }

        println("")
        println("✓ Maven Central bundle created successfully!")
        println("Location: ${outputZip.get().asFile.absolutePath}")
        println("Size: ${outputZip.get().asFile.length() / 1024}KB")
        println("")
        println("Upload to: https://central.sonatype.com/")
    }
}

// Task to create release bundle directly without using Maven Local
// NOTE: This requires entering GPG passphrase during the build
tasks.register("createReleaseBundle") {
    group = "publishing"
    description = "Creates a release bundle directly (requires GPG passphrase)"

    val stagingDir = layout.buildDirectory.dir("maven-staging")
    val bundleDir = layout.buildDirectory.dir("release-bundle")
    val outputZip = layout.buildDirectory.file("distributions/kodama-${project.version}-release.zip")

    // Publish to staging directory first (requires GPG signing)
    dependsOn(gradle.includedBuild("kodama-compiler-plugin").task(":publishAllPublicationsToStagingRepository"))
    dependsOn(":kodama-core:publishMavenPublicationToStagingRepository")
    dependsOn(":kodama-ksp-processor:publishMavenPublicationToStagingRepository")

    doLast {
        val bundleDirPath = bundleDir.get().asFile
        val stagingDirPath = stagingDir.get().asFile
        val sourceDir = file("$stagingDirPath/com/obabichev/kodama")

        // Check if artifacts were published to staging
        if (!sourceDir.exists()) {
            throw GradleException(
                "Artifacts not found in staging directory.\n" +
                "Publishing to staging may have failed."
            )
        }

        // Clean and create bundle directory
        delete(bundleDirPath)
        mkdir(bundleDirPath)

        // Copy artifacts from staging
        copy {
            from(sourceDir)
            into("${bundleDirPath}/com/obabichev/kodama")
        }

        // Generate MD5 and SHA1 checksums for all artifacts
        fileTree(bundleDirPath).matching {
            include("**/*.jar", "**/*.pom", "**/*.module", "**/*.asc")
        }.forEach { file ->
            // Generate MD5
            val md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(file.readBytes())
            file("${file.absolutePath}.md5").writeText(md5)

            // Generate SHA1
            val sha1 = org.apache.commons.codec.digest.DigestUtils.sha1Hex(file.readBytes())
            file("${file.absolutePath}.sha1").writeText(sha1)
        }

        // Count checksums
        val checksumCount = fileTree(bundleDirPath).matching {
            include("**/*.md5", "**/*.sha1")
        }.files.size

        println("Generated $checksumCount checksum files")

        // Create ZIP bundle
        mkdir(outputZip.get().asFile.parentFile)
        ant.withGroovyBuilder {
            "zip"("destfile" to outputZip.get().asFile.absolutePath) {
                "fileset"("dir" to bundleDirPath)
            }
        }

        println("")
        println("✓ Release bundle created successfully!")
        println("Location: ${outputZip.get().asFile.absolutePath}")
        println("Size: ${outputZip.get().asFile.length() / 1024}KB")
        println("")
        println("Staging directory: ${stagingDirPath.absolutePath}")
        println("(You can delete the staging directory after creating the bundle)")
        println("")
        println("Upload to: https://central.sonatype.com/")
    }
}
