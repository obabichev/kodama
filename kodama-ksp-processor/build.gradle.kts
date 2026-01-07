import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    kotlin("jvm") version "2.0.21"
    `maven-publish`
    signing
}

// Read version and group from parent gradle.properties
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
    implementation(project(":kodama-core"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.27")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
            artifactId = "kodama-ksp-processor"

            pom {
                name.set("Kodama KSP Processor")
                description.set("KSP processor for Kodama - Discovers table definitions at compile time")
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
