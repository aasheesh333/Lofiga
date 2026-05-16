plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.2.20-2.0.2"
}

android {
    namespace = System.getenv("PACKAGE_NAME") ?: project.findProperty("PACKAGE_NAME")?.toString() ?: "com.dhanuk.lofiga"
    compileSdk = 35

    val admobAppId = System.getenv("ADMOB_APP_ID") ?: project.findProperty("ADMOB_APP_ID")?.toString() ?: "ca-app-pub-3940256099942544~3347511713"
    val admobBannerId = System.getenv("ADMOB_BANNER_ID") ?: project.findProperty("ADMOB_BANNER_ID")?.toString() ?: "ca-app-pub-3940256099942544/6300978111"
    val admobInterstitialId = System.getenv("ADMOB_INTERSTITIAL_ID") ?: project.findProperty("ADMOB_INTERSTITIAL_ID")?.toString() ?: "ca-app-pub-3940256099942544/1033173712"
    val admobRewardedId = System.getenv("ADMOB_REWARDED_ID") ?: project.findProperty("ADMOB_REWARDED_ID")?.toString() ?: "ca-app-pub-3940256099942544/5224354917"

    defaultConfig {
        applicationId = namespace
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull() ?: 2
        versionName = System.getenv("VERSION_NAME") ?: project.findProperty("VERSION_NAME")?.toString() ?: "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
    }

    signingConfigs {
        create("demo") {
            storeFile = file("demo.keystore")
            storePassword = "android"
            keyAlias = "demo_key"
            keyPassword = "android"
        }
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: project.findProperty("KEYSTORE_PATH")?.toString()
            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
            } else {
                storeFile = file("demo.keystore")
            }
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD")?.toString() ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS")?.toString() ?: "mykey"
            keyPassword = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD")?.toString() ?: "android"
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
            signingConfig = signingConfigs.getByName("demo")
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
        abortOnError = false
    }

    packaging {
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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
