plugins {
    kotlin("jvm") apply true
    alias(libs.plugins.ksp)  // Required for Kodama to discover tables
    id("com.obabichev.kodama")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Context parameters require Kotlin 2.2+
        // freeCompilerArgs.add("-Xcontext-parameters")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.debug)

    implementation(kotlin("test-junit"))

    implementation(project(":kodama-core"))
    // For internal development, explicitly add KSP processor as project dependency
    // KSP will automatically scan schema files and generate kodama-ksp-metadata.json
    ksp(project(":kodama-ksp-processor"))

    implementation(libs.slf4j)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    testImplementation(libs.kotlinx.coroutines.test)

    testCompileOnly(libs.postgre)
}

// Ensure Kodama code generation runs after KSP discovers tables
tasks.named("generateKodamaTableMetadata") {
    dependsOn("kspKotlin")
}

// Ensure test compilation depends on query extensions generation
tasks.named("compileTestKotlin") {
    dependsOn("generateKodamaQueryExtensions")
}