package com.dhanuk.lofiga

import android.content.Context
import androidx.annotation.NonNull
import io.flutter.app.FlutterApplication

class LofigaApplication : FlutterApplication() {
    companion object {
        // Keep a static reference so native/JNI code can access the classloader
        // even when Thread.currentThread().getContextClassLoader() returns null.
        var appClassLoader: ClassLoader? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        // Guard 2: Set classloader when Application.onCreate runs.
        ensureClassLoader()
        // Pre-register commonly used Flutter/plugin Java classes so that JNI
        // FindClass can find them even when the thread context classloader is null.
        preloadCommonClasses()
    }

    override fun attachBaseContext(@NonNull base: Context) {
        super.attachBaseContext(base)
        // Guard 1: Set classloader at the earliest possible point.
        ensureClassLoader()
        preloadCommonClasses()
    }

    private fun ensureClassLoader() {
        val cl = javaClass.classLoader
        appClassLoader = cl
        Thread.currentThread().setContextClassLoader(cl)
        // Also propagate to any already-running Flutter-internal threads.
        propagateClassLoader(cl)
    }

    private fun propagateClassLoader(cl: ClassLoader?) {
        if (cl == null) return
        try {
            val threads = arrayOfNulls<Thread>(Thread.activeCount() * 2)
            Thread.enumerate(threads)
            for (t in threads) {
                if (t != null) {
                    try {
                        t.setContextClassLoader(cl)
                    } catch (_: SecurityException) {
                        // Can't set on some threads
                    }
                }
            }
        } catch (_: Exception) {
            // Best effort
        }
    }

    private fun preloadCommonClasses() {
        val classLoader = appClassLoader ?: return
        val classes = arrayOf(
            // Flutter engine classes
            "io.flutter.embedding.engine.FlutterEngine",
            "io.flutter.embedding.android.FlutterActivity",
            "io.flutter.plugin.common.MethodChannel",
            "io.flutter.plugin.common.BinaryMessenger",
            // just_audio / ExoPlayer
            "android.media.MediaExtractor",
            "android.media.MediaCodec",
            "android.media.MediaFormat",
            "android.media.MediaMetadataRetriever",
            // Audio-related
            "android.media.AudioManager",
            "android.media.AudioTrack",
            "android.media.AudioAttributes",
            // File / IO
            "android.content.ContentResolver",
            "android.net.Uri",
            "android.database.Cursor",
            "android.provider.MediaStore",
            "android.os.Environment",
            // Plugin-specific
            "com.dhanuk.lofiga.MainActivity",
            "com.dhanuk.lofiga.LofigaApplication"
        )
        for (className in classes) {
            try {
                Class.forName(className, false, classLoader)
            } catch (_: ClassNotFoundException) {
                // Not all classes may exist on all devices
            }
        }
    }
}