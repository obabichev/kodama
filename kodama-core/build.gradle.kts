import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("reflect"))
    api(libs.kotlinx.coroutines)
    api(libs.slf4j)

    // TODO move it from the core module
    api(libs.postgre)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "8"
}

// Maven publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "kodama-core"

            pom {
                name.set("Kodama Core")
                description.set("Type-safe SQL query builder for Kotlin and PostgreSQL - Core library")
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
//     sign(publishing.publications["maven"])
// }
