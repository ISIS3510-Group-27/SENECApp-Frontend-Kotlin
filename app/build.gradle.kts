plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Google Services plugin fails the build when google-services.json is missing, and that file
// comes from a Firebase project the team has not created yet. Applying it only when the file is
// there keeps everyone compiling today and turns Firebase on by itself the day it lands.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.senecapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.senecapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
                .getOrElse("http://10.0.2.2:8000/api/v1")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "DEV_TOKEN", "\"dev:s.arango@uniandes.edu.co\"")
            // Which TokenProvider strategy to install: "dev" or "firebase". Mirrors the backend's
            // own AUTH_PROVIDER setting, which has to be switched at the same time.
            // Override without editing this file: ./gradlew -PauthProvider=firebase ...
            val authProvider = providers.gradleProperty("authProvider").getOrElse("dev")
            buildConfigField("String", "AUTH_PROVIDER", "\"$authProvider\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"\"")
            buildConfigField("String", "DEV_TOKEN", "\"\"")
            buildConfigField("String", "AUTH_PROVIDER", "\"firebase\"")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")

    // Authentication (e): Firebase issues the ID token that TokenProvider puts on every request.
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-auth")

    // Sensor (a): Google's code scanner. Runs its capture UI inside Play Services, so the app
    // needs no CAMERA permission and no CameraX preview of its own.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
