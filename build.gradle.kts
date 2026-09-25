import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// key.properties points at the real keystore; without it (or its keystore) every build is signed
// with the sample keystore that is checked in.
val keyProps = Properties().apply {
    val keyFile = file("key.properties")
    if (keyFile.exists()) keyFile.inputStream().use { load(it) }
    if (getProperty("keyStore")?.let { file(it).exists() } != true) {
        logger.warn("key.properties or its keystore not found - using sample keystore!!")
        clear()
        file("key.properties.sample").inputStream().use { load(it) }
    }
}

android {
    namespace = "de.j4velin.pedometer"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.j4velin.pedometer"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2000
        versionName = "2.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            storeFile = file(keyProps.getProperty("keyStore"))
            storePassword = keyProps.getProperty("keyStorePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyAliasPassword")
        }
    }

    flavorDimensions += "main"

    productFlavors {
        create("play") {
            dimension = "main"
        }
        create("fdroid") {
            dimension = "main"
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation")
        // flags the Compose compiler plugin, which is pinned on purpose (see libs.versions.toml)
        disable += "NewerVersionAvailable"
    }

    testOptions {
        // Robolectric needs the merged resources and manifest
        unitTests.isIncludeAndroidResources = true
        // Robolectric's SDK 36 sandbox reaches into jdk.internal.access, which JDK 17+ hides
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-project.txt")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.core)
    implementation(libs.glance)
    "playImplementation"(libs.playGames)
    // Play Games brings an old androidx.fragment, which breaks the Activity Result API
    "playImplementation"(libs.fragment)

    implementation(libs.coroutines)
    implementation(libs.activity)
    implementation(libs.lifecycleRuntime)
    implementation(libs.lifecycleViewmodel)

    // Compose. The BOM pins every androidx.compose artifact to one consistent set.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycleRuntime)
    implementation(libs.compose.lifecycleViewmodel)
    implementation(libs.compose.navigation)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.test.junit4)
    testImplementation(libs.glance.testing)
}
