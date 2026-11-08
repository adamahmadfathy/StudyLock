plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The build number comes from the cloud build (-Pvc=<n>) so every release is newer than the last.
val buildNumber = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 1
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.studylock.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.studylock.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.3.$buildNumber"
    }
    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = "studylock"
                keyAlias = "studylock"
                keyPassword = "studylock"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
