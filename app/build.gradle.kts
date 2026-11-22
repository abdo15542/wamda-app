plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.wamdalibya.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.wamdalibya.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // Two separate apps from one codebase — one per site, never mixed.
    flavorDimensions += "language"
    productFlavors {
        create("ar") {
            dimension = "language"
            // Same package as the first build, so it updates on the phone.
            applicationId = "net.wamdalibya.app"
            versionCode = 2
            versionName = "1.1.0"
            buildConfigField("String", "SITE_URL", "\"https://wamdalibya.netlify.app/\"")
        }
        create("en") {
            dimension = "language"
            applicationId = "net.libyapulse.app"
            versionCode = 1
            versionName = "1.0.0"
            buildConfigField("String", "SITE_URL", "\"https://abdo15542.github.io/libyapulse/\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
