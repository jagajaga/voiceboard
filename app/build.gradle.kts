import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load local.properties (for local dev); CI injects via env
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun prop(key: String): String =
    System.getenv(key) ?: localProps.getProperty(key) ?: ""

android {
    namespace = "ai.voiceboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.voiceboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "OPENAI_API_KEY", "\"${prop("OPENAI_API_KEY")}\"")
        buildConfigField("String", "WHISPER_MODEL",  "\"${prop("WHISPER_MODEL").ifEmpty { "gpt-4o-transcribe" }}\"")
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
