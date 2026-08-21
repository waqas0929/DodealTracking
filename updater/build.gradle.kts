import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dodeal.updater"
    compileSdk = 35

    base { archivesName.set("DodealUpdater") }

    defaultConfig {
        applicationId = "com.dodeal.updater"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Production release signing — Dodeal Updater has its OWN permanent signing identity, separate
    // from Tracking and every other Dodeal app. From keystore.properties OR env vars. Never debug.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val signingProps = Properties().apply { if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream()) }
    fun signingValue(fileKey: String, envKey: String): String? =
        (signingProps.getProperty(fileKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

    signingConfigs {
        create("release") {
            val store = signingValue("updater.storeFile", "DODEAL_UPDATER_STORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("updater.storePassword", "DODEAL_UPDATER_STORE_PASSWORD")
                keyAlias = signingValue("updater.keyAlias", "DODEAL_UPDATER_KEY_ALIAS")
                keyPassword = signingValue("updater.keyPassword", "DODEAL_UPDATER_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "UPDATER_CHANNEL", "\"staging\"")
            isMinifyEnabled = false
        }
        release {
            buildConfigField("String", "UPDATER_CHANNEL", "\"production\"")
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Fail fast if a RELEASE artifact is built without production signing configured.
run {
    val releaseSigning = android.signingConfigs.getByName("release")
    tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        doFirst {
            if (releaseSigning.storeFile == null) {
                throw GradleException(
                    "Release build requires the Dodeal production signing key (keystore.properties or " +
                        "DODEAL_STORE_FILE env). Never ship a debug-signed APK."
                )
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
}
