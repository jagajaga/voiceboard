plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ai.voiceboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.voiceboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // No secrets in the APK — key is entered by the user in-app and stored in SharedPreferences
    }

    // Stable signing: reads keystore from env vars injected by CI.
    // Falls back to the default debug keystore when env vars are absent (local dev).
    val keystorePath  = System.getenv("KEYSTORE_PATH")
    val storePassword = System.getenv("STORE_PASSWORD")
    val keyPassword   = System.getenv("KEY_PASSWORD")
    val keyAlias      = System.getenv("KEY_ALIAS") ?: "voiceboard"

    signingConfigs {
        if (keystorePath != null && storePassword != null && keyPassword != null) {
            create("release") {
                storeFile     = file(keystorePath)
                this.storePassword = storePassword
                this.keyPassword   = keyPassword
                this.keyAlias      = keyAlias
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.findByName("release")
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
