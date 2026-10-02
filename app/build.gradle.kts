plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/api/v1\"")
            buildConfigField("String", "DEV_TOKEN", "\"dev:s.arango@uniandes.edu.co\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"\"")
            buildConfigField("String", "DEV_TOKEN", "\"\"")
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
    debugImplementation("androidx.compose.ui:ui-tooling")
}
