// Top-level build file.
plugins {
    id("com.android.application") version "8.3.2" apply false
    // Kotlin 1.9.22 pairs with Compose compiler 1.5.10 (see app/build.gradle.kts).
    // Kept at 1.9.22 (not 1.9.24): AGP 8.3.2 predates Kotlin 1.9.24 and its
    // Compose-compiler table does not cover compiler 1.5.14.
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // Firebase google-services plugin (4.4.2 pairs with AGP 8.3.2).
    id("com.google.gms.google-services") version "4.4.2" apply false
}
