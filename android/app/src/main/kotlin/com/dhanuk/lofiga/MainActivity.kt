package com.dhanuk.lofiga

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.dhanuk.lofiga/audio_query"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set classloader BEFORE calling super.onCreate()
        forceSetClassLoader()
        super.onCreate(savedInstanceState)
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        // Set classloader BEFORE Flutter engine configuration
        forceSetClassLoader()
        
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

    private fun forceSetClassLoader() {
        try {
            // Force set the classloader immediately
            val cl = javaClass.classLoader
            if (cl != null) {
                Thread.currentThread().setContextClassLoader(cl)
            }
            
            // Also try to get application classloader
            val app = application
            if (app is LofigaApplication) {
                app.ensureProperClassLoader()
            }
        } catch (e: Exception) {
            // Fallback to default classloader if app context is not available
            try {
                val cl = javaClass.classLoader
                Thread.currentThread().setContextClassLoader(cl)
            } catch (inner: Exception) {
                // If even that fails, we keep the existing classloader
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
