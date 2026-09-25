plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kiroland.gallery.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kiroland.gallery.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.7"
    }

    buildTypes {
        release {
            // Shrunk build for sideloading; signed with the debug key until there is a real one.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.material.icons)
    // Weather icons only; R8 strips the rest in release builds.
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.nanohttpd)
}
