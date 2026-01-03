import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import java.util.Properties

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("kapt") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    `java-gradle-plugin`
    `maven-publish`
    signing
}

// Read version and group from parent gradle.properties
// This is an included build, so we read the parent's properties file directly
val parentProperties = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}
group = parentProperties.getProperty("group")
version = parentProperties.getProperty("version")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Kotlin compiler dependencies
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")

    // Auto-service for plugin registration
    kapt("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")

    // JSON parsing for KSP metadata
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Kotlin reflection for runtime metadata extraction
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Test dependencies (currently no tests)
    testImplementation(kotlin("test-junit"))
}

gradlePlugin {
    plugins {
        create("kodamaPlugin") {
            id = "com.obabichev.kodama"
            implementationClass = "com.obabichev.kodama.compiler.KodamaGradlePlugin"
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Enable context receivers if needed for future extensions
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

// Create sources and javadoc JARs (required by Maven Central)
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    // Empty javadoc JAR is acceptable for Maven Central
}

// Maven publishing configuration
// Note: The java-gradle-plugin automatically creates publications
// We just need to configure the POM metadata
publishing {
    publications {
        withType<MavenPublication> {
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("Kodama Gradle Plugin")
                description.set("Gradle plugin for Kodama - Type-safe SQL query builder code generation")
                url.set("https://github.com/obabichev/kodama")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("obabichev")
                        name.set("Oleg Babichev")
                        email.set("obabichev@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/obabichev/kodama.git")
                    developerConnection.set("scm:git:ssh://github.com/obabichev/kodama.git")
                    url.set("https://github.com/obabichev/kodama")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }

        maven {
            name = "staging"
            url = uri(file("../build/maven-staging"))
        }

        maven {
            name = "OSSRH"
            // New Central Portal endpoint (OSSRH was shut down June 30, 2025)
            val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = project.findProperty("mavenCentralUsername") as String? ?: System.getenv("MAVEN_CENTRAL_USERNAME")
                password = project.findProperty("mavenCentralPassword") as String? ?: System.getenv("MAVEN_CENTRAL_PASSWORD")
            }
        }
    }
}

signing {
    useGpgCmd()
    // Sign all publications created by java-gradle-plugin
    sign(publishing.publications)
}

// Fix task dependencies for Gradle plugin marker publications
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}
