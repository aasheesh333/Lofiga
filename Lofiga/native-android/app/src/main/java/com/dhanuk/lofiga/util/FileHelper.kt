package com.dhanuk.lofiga.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Helper for file operations like saving exported tracks.
 */
object FileHelper {

    /**
     * Get the best export directory for the current Android version.
     */
    fun getExportDirectory(context: Context, customPath: String? = null): File {
        if (!customPath.isNullOrEmpty()) {
            val dir = File(customPath)
            if (dir.exists() || dir.mkdirs()) return dir
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+, use the Music directory
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Lofiga"
            ).also { it.mkdirs() }
        } else {
            // On older versions, use Downloads
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Lofiga"
            ).also { it.mkdirs() }
        }
    }

    /**
     * Register a file in the MediaStore on Android 10+.
     */
    fun registerInMediaStore(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Lofiga")
                put(MediaStore.Audio.Media.IS_MUSIC, true)
            }
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }
    }
}