package com.example.lofiga

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.lofiga/audio_query"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        // Fix for Android 9 JNI FindClass crash on some devices (e.g. Xiaomi MIUI):
        // Ensure the thread context classloader is set before plugins are initialized.
        // Without this, native libraries loaded by Flutter plugins may fail to find
        // Java classes through JNI, causing a SIGSEGV in FindClassUnchecked.
        val contextClassLoader = javaClass.classLoader
        Thread.currentThread().setContextClassLoader(contextClassLoader)

        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
            call, result ->
            if (call.method == "querySongs") {
                val songs = querySongs()
                result.success(songs)
            } else {
                result.notImplemented()
            }
        }
    }

    private fun querySongs(): List<Map<String, Any>> {
        val songList = mutableListOf<Map<String, Any>>()

        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC
        )

        // Sort by date added descending
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, null, sortOrder)

            if (cursor != null && cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                do {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val data = cursor.getString(dataColumn) ?: ""
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    if (data.isNotEmpty()) {
                        val songMap = mapOf(
                            "id" to id,
                            "title" to title,
                            "artist" to artist,
                            "data" to data,
                            "duration" to duration,
                            "date_added" to dateAdded
                        )
                        songList.add(songMap)
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return songList
    }
}
