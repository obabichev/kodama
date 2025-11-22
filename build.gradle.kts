plugins {
    alias(libs.plugins.jvm) apply true
}

dependencies {
}

repositories {
    mavenLocal()
    mavenCentral()
}

subprojects {
    if (name == "kodama-bom") return@subprojects

    apply(plugin = rootProject.libs.plugins.jvm.get().pluginId)
}
