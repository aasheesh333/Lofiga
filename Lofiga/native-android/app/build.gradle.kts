plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.2.20-2.0.2"
}

android {
    namespace = System.getenv("PACKAGE_NAME") ?: project.findProperty("PACKAGE_NAME")?.toString() ?: "com.dhanuk.lofiga"
    compileSdk = 36

    val admobAppId = System.getenv("ADMOB_APP_ID") ?: project.findProperty("ADMOB_APP_ID")?.toString() ?: ""
    val admobBannerId = System.getenv("ADMOB_BANNER_ID") ?: project.findProperty("ADMOB_BANNER_ID")?.toString() ?: ""
    val admobInterstitialId = System.getenv("ADMOB_INTERSTITIAL_ID") ?: project.findProperty("ADMOB_INTERSTITIAL_ID")?.toString() ?: ""
    val admobRewardedId = System.getenv("ADMOB_REWARDED_ID") ?: project.findProperty("ADMOB_REWARDED_ID")?.toString() ?: ""

    defaultConfig {
        applicationId = namespace
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull() ?: 3
        versionName = System.getenv("VERSION_NAME") ?: project.findProperty("VERSION_NAME")?.toString() ?: "2.0.0"

        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
        manifestPlaceholders["ONESIGNAL_APP_ID"] = System.getenv("ONESIGNAL_APP_ID") ?: project.findProperty("ONESIGNAL_APP_ID")?.toString() ?: ""
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${System.getenv("ONESIGNAL_APP_ID") ?: project.findProperty("ONESIGNAL_APP_ID")?.toString() ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: project.findProperty("KEYSTORE_PATH")?.toString() ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD")?.toString() ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS")?.toString() ?: "mykey"
            keyPassword = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD")?.toString() ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        // Media3's @UnstableApi APIs trigger UnsafeOptInUsageError false
        // positives on Kotlin code even with class-level @OptIn(UnstableApi::class)
        // (all three Media3-using classes annotate correctly). The Kotlin
        // compiler enforces opt-in at error level at build time, so any code
        // that compiles is safe; this lint check adds nothing.
        disable += "UnsafeOptInUsageError"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val composeBomVersion = "2025.03.01"
val roomVersion = "2.7.1"
val lifecycleVersion = "2.8.7"
val coroutinesVersion = "1.10.1"

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.datastore:datastore-preferences:1.1.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    implementation("com.google.android.gms:play-services-ads:24.2.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    // Pin to a fixed version for reproducible builds (was a floating range).
    implementation("com.onesignal:OneSignal:5.1.31")

    // Media3: modern playback, session, and offline export (Transformer).
    // 1.11.0 stable: newer Sonic time-stretch fixes the mid-playback
    // ArrayIndexOutOfBounds / buffer overflow crashes that killed the player
    // during live effect adjustments (only atmosphere kept playing).
    val media3Version = "1.11.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    implementation("io.coil-kt.coil3:coil-compose:3.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
