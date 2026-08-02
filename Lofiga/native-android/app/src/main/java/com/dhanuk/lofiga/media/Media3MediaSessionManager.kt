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
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dhanuk.lofiga.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class Media3MediaSessionManager(private val context: Context) {

    private var mediaSession: MediaSession? = null
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

        val customLayout = try {
            val nextButton = CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName("Next")
                .setSessionCommand(nextCommand)
                .build()
            val prevButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setDisplayName("Previous")
                .setSessionCommand(prevCommand)
                .build()
            ImmutableList.of(prevButton, nextButton)
        } catch (e: Exception) {
            Log.w("Media3MediaSessionManager", "CommandButton build failed: ${e.message}")
            ImmutableList.of()
        }

        val sessionCallback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_NEXT -> onNextTrack?.invoke()
                    ACTION_PREV -> onPreviousTrack?.invoke()
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
                    session.setCustomLayout(customLayout)
                } catch (e: Exception) {
                    Log.w("Media3MediaSessionManager", "setCustomLayout failed: ${e.message}")
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
