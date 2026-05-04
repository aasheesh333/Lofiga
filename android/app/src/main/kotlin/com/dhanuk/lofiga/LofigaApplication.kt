package com.dhanuk.lofiga

import android.content.Context
import androidx.annotation.NonNull
import io.flutter.app.FlutterApplication

class LofigaApplication : FlutterApplication() {
    override fun attachBaseContext(@NonNull base: Context) {
        // Fix for Android 9 JNI FindClass crash on some devices (e.g. Xiaomi MIUI):
        // Set the thread context classloader as early as possible, before any Flutter
        // engine or plugin initialization takes place. On affected devices the thread
        // context classloader can be null at startup, causing a SIGSEGV in native code
        // when Flutter plugins attempt JNI FindClass calls.
        val contextClassLoader = javaClass.classLoader
        Thread.currentThread().setContextClassLoader(contextClassLoader)
        super.attachBaseContext(base)
    }
}