package com.dhanuk.lofiga

import android.content.Context
import androidx.annotation.NonNull
import io.flutter.app.FlutterApplication

class LofigaApplication : FlutterApplication() {
    companion object {
        // Keep a static reference so native/JNI code can access the classloader
        // even when Thread.currentThread().getContextClassLoader() returns null.
        @JvmStatic
        var appClassLoader: ClassLoader? = null
        
        // Static initializer to set classloader as early as possible
        init {
            try {
                // Set classloader immediately when class is loaded
                val cl = LofigaApplication::class.java.classLoader
                appClassLoader = cl
                if (cl != null) {
                    Thread.currentThread().setContextClassLoader(cl)
                }
            } catch (e: Exception) {
                // Ignore any exceptions during static initialization
            }
        }
    }
    
    override fun onCreate() {
        // Ensure classloader is set before any Flutter code runs
        forceSetClassLoader()
        super.onCreate()
        ensureClassLoader()
    }

    override fun attachBaseContext(@NonNull base: Context) {
        // Set classloader as early as possible
        forceSetClassLoader()
        
        super.attachBaseContext(base)
        
        // Force load native libraries immediately
        forceLoadNativeLibraries()
        
        // Preload critical classes
        preloadCriticalClasses()
        
        // Ensure classloader is set
        ensureClassLoader()
    }

    private fun forceSetClassLoader() {
        try {
            val cl = javaClass.classLoader ?: return
            appClassLoader = cl
            Thread.currentThread().setContextClassLoader(cl)
        } catch (e: Exception) {
            // Ignore exceptions
        }
    }

    private fun forceLoadNativeLibraries() {
        try {
            // Load libraries in specific order to ensure proper initialization
            System.loadLibrary("flutter")
        } catch (_: UnsatisfiedLinkError) { }
        
        try {
            System.loadLibrary("dartjni")
        } catch (_: UnsatisfiedLinkError) { }
    }

    private fun preloadCriticalClasses() {
        val classLoader = appClassLoader ?: return
        // Only preload the most critical classes that are likely to be accessed
        val criticalClasses = arrayOf(
            "io.flutter.embedding.engine.FlutterEngine",
            "io.flutter.embedding.android.FlutterActivity",
            "io.flutter.plugin.common.MethodChannel",
            "android.app.Activity",
            "android.content.Context"
        )
        
        for (className in criticalClasses) {
            try {
                Class.forName(className, true, classLoader)
            } catch (_: ClassNotFoundException) { } 
            catch (_: LinkageError) { }
        }
    }

    private fun ensureClassLoader() {
        forceSetClassLoader()
    }

    // Public method for MainActivity to call
    fun ensureProperClassLoader() {
        forceSetClassLoader()
    }
}
