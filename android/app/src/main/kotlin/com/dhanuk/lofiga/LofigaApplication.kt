package com.dhanuk.lofiga

import android.content.Context
import androidx.annotation.NonNull
import io.flutter.app.FlutterApplication

class LofigaApplication : FlutterApplication() {
    override fun onCreate() {
        super.onCreate()
        // Guard 2: Set classloader when Application.onCreate runs (after attachBaseContext).
        Thread.currentThread().setContextClassLoader(javaClass.classLoader)
    }

    override fun attachBaseContext(@NonNull base: Context) {
        super.attachBaseContext(base)
        // Guard 1: Set classloader at the earliest possible point in the Application
        // lifecycle, before any Flutter engine or plugin initialization.
        // On Xiaomi Android 9 the thread context classloader can be null at startup,
        // causing a SIGSEGV in FindClassUnchecked when native/Dart code calls FindClass.
        Thread.currentThread().setContextClassLoader(javaClass.classLoader)
    }
}