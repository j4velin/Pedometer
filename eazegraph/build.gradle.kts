plugins {
    alias(libs.plugins.androidLibrary)
}

// Vendored copy of https://github.com/j4velin/EazeGraph (fork of blackfizz/EazeGraph, Apache 2.0),
// as the published 1.0.3 artifact was only available on JCenter
android {
    namespace = "org.eazegraph.lib"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    implementation(libs.nineoldandroids)
}
