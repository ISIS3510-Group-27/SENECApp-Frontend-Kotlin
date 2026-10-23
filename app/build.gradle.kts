import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase sign-in requires app/google-services.json from the senecapp project.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Command-line/Gradle properties override the optional, git-ignored local configuration.
val backendProperties = Properties().apply {
    val configFile = rootProject.file("backend.local.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
val apiBaseUrl = providers.gradleProperty("apiBaseUrl").orNull
    ?: backendProperties.getProperty("apiBaseUrl")
    ?: "https://35-170-175-16.sslip.io/api/v1"
val normalizedApiBaseUrl = apiBaseUrl.trim().trimEnd('/')
val backendUri = URI(normalizedApiBaseUrl)
require(backendUri.scheme in setOf("https", "http") && !backendUri.host.isNullOrBlank() &&
    backendUri.rawUserInfo == null && backendUri.rawQuery == null && backendUri.rawFragment == null) {
    "apiBaseUrl must be an HTTP(S) API base URL without credentials, query parameters or a fragment."
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
        // Shared by debug and release. Authentication always uses Firebase ID tokens.
        buildConfigField("String", "API_BASE_URL", "\"${backendUri.toASCIIString()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

    // Existing unit tests cover Firebase session handling, token renewal and profile JSON.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20240303")
}
