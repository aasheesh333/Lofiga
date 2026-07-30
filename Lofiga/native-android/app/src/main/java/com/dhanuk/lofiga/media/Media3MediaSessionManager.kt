package com.dhanuk.lofiga.media

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.Token as MediaSessionToken
import com.dhanuk.lofiga.MainActivity

/**
 * Bridges the in-process [com.dhanuk.lofiga.audio.AudioEngine]'s ExoPlayer with a
 * Media3 [MediaSession] for background-playback controls.
 *
 * Replaces the legacy androidx MediaSessionCompat + hand-rolled MediaStyle
 * notification. The notification itself is produced by the Media3
 * [androidx.media3.session.PlayerNotificationManager] wired inside the hosting
 * [MediaSessionService]. Removing the legacy stack also removes the
 * static-mutable-state in [MediaPlaybackService.companion] that broke across
 * process death.
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
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(context, 0, sessionActivityIntent, flags)
        mediaSession = MediaSession.Builder(context, player)
            .setSessionActivity(pendingIntent)
            .setId("lofiga_media_session")
            .build()
    }

    /** The underlying Media3 session instance. */
    val session: MediaSession? get() = mediaSession

    /** Token (for notification wiring / controller registration). */
    val sessionToken: MediaSessionToken? get() = mediaSession?.token

    fun isActive(): Boolean = mediaSession != null

    fun ensureChannel(channelId: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = android.app.NotificationChannel(
                    channelId, name, NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows current track and playback controls"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun release() {
        mediaSession?.run {
            try { release() } catch (e: Exception) {
                android.util.Log.w("Media3MediaSessionManager", "session release failed: ${e.message}")
            }
        }
        mediaSession = null
    }
}
