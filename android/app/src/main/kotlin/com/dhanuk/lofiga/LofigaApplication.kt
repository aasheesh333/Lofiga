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
    }

    override fun onCreate() {
        super.onCreate()
        ensureClassLoader()
    }

    override fun attachBaseContext(@NonNull base: Context) {
        super.attachBaseContext(base)
        // On Xiaomi MIUI Android 9, dart:jni's FindClassUnchecked crashes because
        // libdartjni.so's JNI_OnLoad may not have properly captured the JavaVM*
        // when the library is loaded implicitly. Pre-loading both libflutter.so
        // and libdartjni.so here forces their JNI_OnLoad to run early on the main
        // thread, ensuring the JNI environment is properly set up.
        try { System.loadLibrary("flutter") } catch (_: UnsatisfiedLinkError) { }
        try { System.loadLibrary("dartjni") } catch (_: java.lang.UnsatisfiedLinkError) { }
        ensureClassLoader()
        preloadCommonClasses()
    }

    private fun ensureClassLoader() {
        val cl = javaClass.classLoader
        appClassLoader = cl
        Thread.currentThread().setContextClassLoader(cl)
    }

    private fun preloadCommonClasses() {
        val classLoader = appClassLoader ?: return
        val classes = arrayOf(
            // Flutter engine classes
            "io.flutter.embedding.engine.FlutterEngine",
            "io.flutter.embedding.android.FlutterActivity",
            "io.flutter.plugin.common.MethodChannel",
            "io.flutter.plugin.common.BinaryMessenger",
            // Android framework classes that might be needed
            "android.media.MediaExtractor",
            "android.media.MediaCodec",
            "android.media.MediaFormat", 
            "android.media.MediaMetadataRetriever",
            "android.media.AudioManager",
            "android.media.AudioTrack",
            "android.media.AudioAttributes",
            "android.content.ContentResolver",
            "android.net.Uri",
            "android.database.Cursor", 
            "android.provider.MediaStore",
            "android.os.Environment",
            "android.content.Context",
            "android.app.Activity",
            // Plugin-specific classes
            "com.dhanuk.lofiga.MainActivity",
            "com.dhanuk.lofiga.LofigaApplication"
        )
        for (className in classes) {
            try {
                Class.forName(className, false, classLoader)
            } catch (_: ClassNotFoundException) {
                // Not all classes may exist on all devices
            } catch (_: LinkageError) {
                // Class might not be available on all API levels
            }
        }
    }
    
    // Add a method to ensure classloader is properly set
    fun ensureProperClassLoader() {
        val cl = appClassLoader ?: javaClass.classLoader
        Thread.currentThread().setContextClassLoader(cl)
    }
}
