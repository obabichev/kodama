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

// Task to publish both core and compiler plugin
// The compiler plugin is an included build, so we need to invoke it explicitly
tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes all modules (including compiler plugin) to Maven Local"

    dependsOn(gradle.includedBuild("kodama-compiler-plugin").task(":publishToMavenLocal"))
    dependsOn(":kodama-core:publishToMavenLocal")
}

tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes all modules (including compiler plugin) to configured repositories"

    dependsOn(gradle.includedBuild("kodama-compiler-plugin").task(":publish"))
    dependsOn(":kodama-core:publish")
}
