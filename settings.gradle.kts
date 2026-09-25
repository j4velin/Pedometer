pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Lets Gradle download the JDK 21 toolchain (jvmToolchain(21) in build.gradle.kts) on a machine
// that does not have one.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // colorpicker is only published on JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "Pedometer"
include(":eazegraph")
