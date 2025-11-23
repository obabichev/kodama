import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("kapt") version "2.1.0"
    `java-gradle-plugin`
}

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
