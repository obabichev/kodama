import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("kapt") version "2.1.0"
    `java-gradle-plugin`
    `maven-publish`
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

// Maven publishing configuration
// Note: The java-gradle-plugin automatically creates publications
// We just need to configure the POM metadata
publishing {
    publications {
        withType<MavenPublication> {
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

        // Uncomment for publishing to Maven Central via Sonatype OSSRH
        // maven {
        //     name = "OSSRH"
        //     val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        //     val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        //     url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        //     credentials {
        //         username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
        //         password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
        //     }
        // }
    }
}

// Optional: Signing configuration for Maven Central
// Uncomment when ready to publish to Maven Central
// signing {
//     publishing.publications.withType<MavenPublication>().configureEach {
//         sign(this)
//     }
// }
