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
