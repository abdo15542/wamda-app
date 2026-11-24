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
        compose = true
    }

    composeOptions {
        // Pairs with Kotlin 1.9.22 (top-level build file) and
        // Compose BOM 2024.02.00. Era-matched to AGP 8.3.2: its
        // Compose-compiler table predates compiler 1.5.14, so do not bump
        // any of the three without also upgrading AGP.
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    // Two separate native apps from one codebase — one per brand, never mixed.
    flavorDimensions += "language"
    productFlavors {
        create("ar") {
            dimension = "language"
            // Same package as the first build, so it updates on the phone.
            applicationId = "net.wamdalibya.app"
            versionCode = 9
            versionName = "1.3.0"
            // Native content feed (JSON) — the app reads articles straight
            // from GitHub Pages; no website/WebView involved.
            buildConfigField("String", "FEED_BASE_URL", "\"https://abdo15542.github.io/wamdalibya/\"")
            buildConfigField("boolean", "IS_ARABIC", "true")
            buildConfigField("String", "FACEBOOK_PAGE_URL", "\"https://www.facebook.com/profile.php?id=61596420762599\"")
            // Real AdMob IDs (Abdoo's account, pub-6317687886565811).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6317687886565811~6841921039"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-6317687886565811/7459586873\"")
        }
        create("en") {
            dimension = "language"
            applicationId = "net.libyapulse.app"
            versionCode = 7
            versionName = "1.2.0"
            buildConfigField("String", "FEED_BASE_URL", "\"https://abdo15542.github.io/techpulse/\"")
            buildConfigField("boolean", "IS_ARABIC", "false")
            buildConfigField("String", "FACEBOOK_PAGE_URL", "\"https://www.facebook.com/profile.php?id=61596701491859\"")
            // Real AdMob IDs (Abdoo's account, pub-6317687886565811).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6317687886565811~4758683178"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-6317687886565811/3445601506\"")
        }
    }

    // Release signing — ONE stable keystore for every build, so updates
    // always install in place. The keystore itself is NOT in the repo:
    // CI decodes it from the KEYSTORE_BASE64 repo secret into
    // app/release.keystore (see .github/workflows/build-apk.yml) and
    // passes the passwords via env vars. Never commit the keystore or
    // any password. Debug builds are unaffected (they use the debug key).
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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

    // Jetpack Compose — BOM 2024.02.00 pairs with Kotlin 1.9.22 and
    // Compose compiler 1.5.10 (see composeOptions above).
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material") // pull-to-refresh
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // WorkManager — background update check (every 15 min) that posts a
    // system notification when a newer build is published. 2.9.0 pairs
    // with Kotlin 1.9.x; do not bump without checking the toolchain.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // core-ktx — FileProvider (in-app APK install) + NotificationCompat.
    implementation("androidx.core:core-ktx:1.12.0")

    // Coil — article images. 2.4.0 is era-matched to this toolchain
    // (same AsyncImage API as later 2.x releases).
    implementation("io.coil-kt:coil-compose:2.4.0")

    // AdMob — one small adaptive banner at the bottom only (real IDs,
    // per flavor, in the productFlavors blocks above).
    // Pinned to 23.6.0: 24.x needs Kotlin 2.1+ (project is on Kotlin 1.9.x)
    // and a newer compileSdk; 23.6.0 builds with this toolchain. Do not bump
    // without also upgrading Kotlin and compileSdk.
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
