package com.dhanuk.lofiga.media

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dhanuk.lofiga.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures

@OptIn(UnstableApi::class)
class Media3MediaSessionManager(private val context: Context) {

    private var mediaSession: MediaSession? = null
    var onNextTrack: (() -> Unit)? = null
    var onPreviousTrack: (() -> Unit)? = null

    companion object {
        private val COMMAND_NEXT = SessionCommand("ACTION_NEXT", Bundle.EMPTY)
        private val COMMAND_PREV = SessionCommand("ACTION_PREV", Bundle.EMPTY)
    }

    fun connect(player: Player) {
        release()
        val sessionActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(context, 0, sessionActivityIntent, flags)

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .addSessionCommand(COMMAND_NEXT)
                    .addSessionCommand(COMMAND_PREV)
                    .build()
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                return MediaSession.ConnectionResult(
                    sessionCommands, playerCommands
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    "ACTION_NEXT" -> onNextTrack?.invoke()
                    "ACTION_PREV" -> onPreviousTrack?.invoke()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(context, player)
            .setSessionActivity(pendingIntent)
            .setId("lofiga_media_session")
            .setCallback(callback)
            .build()
    }

    val session: MediaSession? get() = mediaSession

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
