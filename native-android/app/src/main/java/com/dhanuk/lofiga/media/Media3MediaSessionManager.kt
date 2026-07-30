package com.dhanuk.lofiga.media

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dhanuk.lofiga.MainActivity

/**
 * Bridges the in-process [com.dhanuk.lofiga.audio.AudioEngine]'s ExoPlayer with a
 * Media3 [MediaSession] and a [MediaPlaybackService] (a [MediaSessionService]).
 *
 * Replaces the legacy androidx MediaSessionCompat + hand-rolled MediaStyle
 * notification. The notification is now produced by
 * [androidx.media3.session.PlayerNotificationManager] (via the service), which
 * automatically mirrors the ExoPlayer's play state, metadata and transport
 * actions. Removing the legacy stack also removes the static-mutable-state in
 * [MediaPlaybackService.companion] that broke across process death.
 */
@OptIn(UnstableApi::class)
class Media3MediaSessionManager(private val context: Context) {

    private var mediaSession: MediaSession? = null

    /**
     * Creates and connects a Media3 [MediaSession] for the given ExoPlayer-backed
     * [Player]. The session lets system media controllers (lock screen, Bluetooth
     * media buttons, assistant) drive the player. Safe to call again on a new
     * player — the previous session is released first.
     */
    fun connect(player: Player) {
        release()
        val sessionActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            sessionActivityIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                    android.app.PendingIntent.FLAG_IMMUTABLE else 0
        )
        mediaSession = MediaSession.Builder(context, player)
            .setSessionActivity(pendingIntent)
            .setId("lofiga_media_session")
            .build()
    }

    val sessionToken: MediaSession.Token? get() = mediaSession?.token

    /** Whether the session is actively listening to media controls. */
    fun isActive(): Boolean = mediaSession != null

    /**
     * Release the session; called when the player is replaced or on app teardown.
     */
    fun release() {
        mediaSession?.run {
            try {
                release()
            } catch (e: Exception) {
                android.util.Log.w("Media3MediaSessionManager", "session release failed: ${e.message}")
            }
        }
        mediaSession = null
    }

    fun ensureChannel(context: Context, channelId: String, name: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    name,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows current track and playback controls"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}
