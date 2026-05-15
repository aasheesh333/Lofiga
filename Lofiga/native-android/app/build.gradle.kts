plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.2.20-2.0.2"
    id("com.google.gms.google-services")
    id("com.onesignal.androidsdk.onesignal-gradle-plugin")
}

android {
    namespace = "com.dhanuk.lofiga"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dhanuk.lofiga"
        minSdk = 26
        targetSdk = 35
        versionCode = project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull() ?: 2
        versionName = project.findProperty("VERSION_NAME")?.toString() ?: "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["onesignal_app_id"] = ""
    }

    signingConfigs {
        create("demo") {
            storeFile = file("demo.keystore")
            storePassword = "android"
            keyAlias = "demo_key"
            keyPassword = "android"
        }
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null && keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "lofiga"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (System.getenv("KEYSTORE_PASSWORD") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("demo")
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["onesignal_app_id"] = "placeholder-dev-id"
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ONESIGNAL_APP_ID", "\"placeholder-dev-id\"")
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        create("prod") {
            dimension = "environment"
            manifestPlaceholders["onesignal_app_id"] = System.getenv("ONESIGNAL_APP_ID") ?: ""
            manifestPlaceholders["ADMOB_APP_ID"] = System.getenv("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ONESIGNAL_APP_ID", "\"${System.getenv("ONESIGNAL_APP_ID") ?: ""}\"")
            buildConfigField("String", "ADMOB_APP_ID", "\"${System.getenv("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"}\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${System.getenv("ADMOB_BANNER_ID") ?: "ca-app-pub-3940256099942544/6300978111"}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${System.getenv("ADMOB_INTERSTITIAL_ID") ?: "ca-app-pub-3940256099942544/1033173712"}\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"${System.getenv("ADMOB_REWARDED_ID") ?: "ca-app-pub-3940256099942544/5224354917"}\"")
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

    implementation("com.onesignal:OneSignal:[5.0.0, 5.99.99]")

    debugImplementation("androidx.compose.ui:ui-tooling")
}