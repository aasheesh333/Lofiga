package com.dhanuk.lofiga.model

import android.net.Uri

/**
 * Represents a song from the device's media store or a file on disk.
 */
data class AudioTrack(
    val id: Long = 0,
    val title: String = "Unknown",
    val artist: String = "Unknown Artist",
    val uri: Uri? = null,
    val dataPath: String = "",
    val durationMs: Long = 0,
    val dateAdded: Long = 0,
    val fileSize: Long = 0,
    val isMusic: Boolean = true
) {
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }

    val formattedSize: String
        get() {
            if (fileSize <= 0) return ""
            val mb = fileSize / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb) else "%d KB".format(fileSize / 1024)
        }
}

/**
 * Configuration saved for a project (recent edits).
 */
data class SavedConfig(
    val id: String = "",
    val fileName: String = "",
    val filePath: String = "",
    val savedAt: Long = System.currentTimeMillis(),
    val values: PresetValues = PresetValues()
)