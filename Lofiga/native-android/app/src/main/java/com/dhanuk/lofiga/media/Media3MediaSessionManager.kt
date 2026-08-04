package com.dhanuk.lofiga.media

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.ConnectionResult
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.dhanuk.lofiga.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class Media3MediaSessionManager(private val context: Context) {

    private var mediaSession: MediaSession? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var onNextTrack: (() -> Unit)? = null
    var onPreviousTrack: (() -> Unit)? = null

    companion object {
        private const val ACTION_NEXT = "ACTION_NEXT"
        private const val ACTION_PREV = "ACTION_PREV"
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

        val nextCommand = SessionCommand(ACTION_NEXT, Bundle.EMPTY)
        val prevCommand = SessionCommand(ACTION_PREV, Bundle.EMPTY)

        // The default media notification provider (1.5.x) shows custom buttons
        // from MediaSession#setMediaButtonPreferences and only when the custom
        // commands are granted to the connecting controller via onConnect.
        val sessionCommands = SessionCommands.Builder()
            .add(nextCommand)
            .add(prevCommand)
            .build()

        val mediaButtonPreferences = try {
            ImmutableList.of(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setDisplayName("Previous")
                    .setSessionCommand(prevCommand)
                    .setEnabled(true)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setDisplayName("Next")
                    .setSessionCommand(nextCommand)
                    .setEnabled(true)
                    .build()
            )
        } catch (e: Exception) {
            Log.w("Media3MediaSessionManager", "CommandButton build failed: ${e.message}")
            ImmutableList.of()
        }

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ConnectionResult {
                // Grant the custom next/prev commands alongside the player's own
                // commands so the media notification and Android 13+ System UI
                // can send them.
                return ConnectionResult.accept(sessionCommands, session.player.availableCommands)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    // Dispatch asynchronously: the callback chain (nextTrack ->
                    // loadTrack -> session reconnect) may rebuild this session,
                    // which is illegal from inside the session's own callback.
                    ACTION_NEXT -> mainHandler.post { onNextTrack?.invoke() }
                    ACTION_PREV -> mainHandler.post { onPreviousTrack?.invoke() }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(context, player)
            .setSessionActivity(pendingIntent)
            .setId("lofiga_media_session")
            .setCallback(sessionCallback)
            .build()
            .also { session ->
                try {
                    session.setMediaButtonPreferences(mediaButtonPreferences)
                } catch (e: Exception) {
                    Log.w("Media3MediaSessionManager", "setMediaButtonPreferences failed: ${e.message}")
                }
            }
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
                Log.w("Media3MediaSessionManager", "session release failed: ${e.message}")
            }
        }
        mediaSession = null
    }
}
