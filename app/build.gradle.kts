plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.jaafar.remoteconfig"
    compileSdk = 36

    // PLAY_VERSION_CODE (set by CI's next_play_version_code.py, only for builds that will
    // actually be uploaded to Play) is Play's own current highest versionCode + 1 -- the real
    // source of truth, immune to which workflow builds it or when, since it always asks Play
    // rather than trusting a local clock or counter.
    //
    // Builds without that env var (local/debug/fork-PR builds with no Play credentials) fall
    // back to a synthetic scheme that's still guaranteed to stay ahead of every versionCode
    // this app has ever published, and grows slowly enough to never matter: Play caps
    // versionCode at 2,100,000,000, and GITHUB_RUN_NUMBER restarts per workflow (so it can't
    // be trusted to stay ahead of a different workflow's last build) while raw epoch seconds
    // grows 1/sec forever and would reach the cap by ~2036. Counting minutes-since-anchor
    // instead cuts that growth 60x, buying centuries of headroom, with versionCodeBase chosen
    // comfortably above every versionCode ever published (highest so far: ~1.79B as of 2026-09).
    val versionCodeBase = 1_800_000_000L
    val versionCodeAnchorEpochSeconds = 1_735_689_600L // 2025-01-01T00:00:00Z
    val fallbackVersionCode = (versionCodeBase + (System.currentTimeMillis() / 1_000L - versionCodeAnchorEpochSeconds) / 60L).toInt()
    val buildVersionCode = System.getenv("PLAY_VERSION_CODE")?.toIntOrNull() ?: fallbackVersionCode
    val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

    defaultConfig {
        applicationId = "com.mjaafar.fontcreator"
        minSdk = 24
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = ciBuildNumber?.let { "1.0.$it" } ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Plain "billing", not "billing-ktx": BillingManager only uses the callback-based API
    // (matching this codebase's existing non-coroutine style), never the KTX suspend
    // extensions -- and billing-ktx's compiled Kotlin metadata needs a newer Kotlin compiler
    // than this project's 2.0.21 can read, which plain "billing" (a Java-only artifact,
    // carrying no Kotlin metadata to be incompatible) sidesteps entirely.
    implementation("com.android.billingclient:billing:9.1.0")
    // Plain "app-update", not "app-update-ktx", for the same reason as billing above: this app's
    // Kotlin compiler (2.0.21) can't reliably read newer -ktx artifacts' Kotlin metadata. The
    // ActivityResultLauncher-accepting startUpdateFlowForResult() overload AppUpdateHelper uses
    // is part of this plain Java artifact already, so no KTX extension is needed for it.
    implementation("com.google.android.play:app-update:2.1.0")
    // AnalyticsHelper deliberately calls only the plain Java surface
    // (FirebaseAnalytics.getInstance(context).logEvent(String, Bundle)) rather than the Kotlin
    // "Firebase.analytics" extension property or the logEvent(name) { param(...) } DSL builder --
    // those are Kotlin-specific sugar with their own compiled metadata, and this project's Kotlin
    // compiler (2.0.21) isn't guaranteed to read metadata from a newer Kotlin version, same
    // constraint as billing/app-update above. A moderately-recent (not bleeding-edge) BOM further
    // reduces the odds of that mismatch, since it was built against an older Kotlin toolchain.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
