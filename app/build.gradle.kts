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
            versionCode = 5
            versionName = "1.1.3"
            buildConfigField("String", "SITE_URL", "\"https://abdo15542.github.io/wamdalibya/\"")
            // Real AdMob IDs (Abdoo's account, pub-6317687886565811).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6317687886565811~6841921039"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-6317687886565811/7459586873\"")
        }
        create("en") {
            dimension = "language"
            applicationId = "net.libyapulse.app"
            versionCode = 3
            versionName = "1.0.2"
            buildConfigField("String", "SITE_URL", "\"https://abdo15542.github.io/libyapulse/\"")
            // Real AdMob IDs (Abdoo's account, pub-6317687886565811).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6317687886565811~4758683178"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-6317687886565811/3445601506\"")
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
    // AdMob — one small adaptive banner at the bottom only (real IDs,
    // per flavor, in the productFlavors blocks above).
    // Pinned to 23.6.0: 24.x needs Kotlin 2.1+ (project is on Kotlin 1.9.22)
    // and a newer compileSdk; 23.6.0 builds with this toolchain. Do not bump
    // without also upgrading Kotlin and compileSdk.
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
