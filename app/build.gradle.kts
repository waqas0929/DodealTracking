import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dodeal.deviceagent"
    compileSdk = 35

    // Produces DodealTracking-release.apk (spec §1, §26).
    base { archivesName.set("DodealTracking") }

    defaultConfig {
        applicationId = "com.dodeal.deviceagent"
        minSdk = 26
        targetSdk = 35
        // Bumped from the team's 1.0.0 (code 1). Their build and this one are materially
        // different apps — command contract, policy-driven cadence, permissions, UI — and a build
        // that cannot be told apart from the one it replaces is a support problem waiting to
        // happen. A higher versionCode is also what makes any future in-place update possible.
        versionCode = 2
        versionName = "1.1.0"
    }

    // Production release signing — Dodeal Tracking has its OWN permanent signing identity (separate
    // from the Updater and every other Dodeal app). Values come from keystore.properties (never
    // committed) OR environment variables (CI). The debug key is NEVER used for release.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val signingProps = Properties().apply { if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream()) }
    fun signingValue(fileKey: String, envKey: String): String? =
        (signingProps.getProperty(fileKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

    signingConfigs {
        create("release") {
            val store = signingValue("tracking.storeFile", "DODEAL_TRACKING_STORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("tracking.storePassword", "DODEAL_TRACKING_STORE_PASSWORD")
                keyAlias = signingValue("tracking.keyAlias", "DODEAL_TRACKING_KEY_ALIAS")
                keyPassword = signingValue("tracking.keyPassword", "DODEAL_TRACKING_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Test APK talks to STAGING (local mock) and allows cleartext via the debug manifest.
            buildConfigField("String", "AGENT_ENVIRONMENT", "\"STAGING\"")
            isMinifyEnabled = false
        }
        release {
            buildConfigField("String", "AGENT_ENVIRONMENT", "\"PRODUCTION\"")
            isDebuggable = false
            // Kept off so kotlinx.serialization / reflection-free DI never break silently; the
            // serialization keep-rules are in place if you later enable it.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // ALWAYS the production release signing config — never the debug key.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Fail fast if a RELEASE artifact is built without production signing configured, instead of
// silently emitting an unsigned/debug-signed APK. Applies only to release assemble/bundle tasks.
run {
    val releaseSigning = android.signingConfigs.getByName("release")
    tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        doFirst {
            if (releaseSigning.storeFile == null) {
                throw GradleException(
                    "Release build requires the Dodeal production signing key, but none was found.\n" +
                        "Create keystore.properties (see keystore.properties.template) or set env vars\n" +
                        "DODEAL_STORE_FILE / DODEAL_STORE_PASSWORD / DODEAL_KEY_ALIAS / DODEAL_KEY_PASSWORD.\n" +
                        "Never ship a debug-signed APK to production."
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    // Networking — confined to the data/http + adapter layers only.
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Secure token storage (isolated behind SecureTokenStorage).
    implementation(libs.androidx.security.crypto)

    // Reserved for later milestones (heartbeat / offline flush scheduling).
    implementation(libs.androidx.work.runtime.ktx)

    // Location engine (milestone 2).
    implementation(libs.play.services.location)

    // Unit tests run on the JVM: no emulator, no Android framework, so the whole
    // suite is one fast command. Anything needing a device belongs in
    // androidTest, deliberately kept empty for now.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
