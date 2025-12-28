import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
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
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
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
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("maven-staging"))
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
