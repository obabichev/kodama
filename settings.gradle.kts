rootProject.name = "kodama"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


include("kodama-core")
include("kodama-ksp")
include("kodama-tests")

pluginManagement {
    includeBuild("kodama-compiler-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}


plugins {
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
}

toolchainManagement {
    jvm {
        javaRepositories {
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}

