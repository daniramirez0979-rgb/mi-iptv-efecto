plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ar.com.efectolimpio.vendedor"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.com.efectolimpio.vendedor"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.8"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
