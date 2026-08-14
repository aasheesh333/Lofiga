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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.dhanuk.lofiga.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class Media3MediaSessionManager(private val context: Context) {

    private var mediaSession: MediaSession? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var onNextTrack: (() -> Unit)? = null
    var onPreviousTrack: (() -> Unit)? = null

    companion object {
        // Shared with LofigaNotificationProvider so the buttons it renders and
        // the session commands granted in onConnect always match by name.
        const val ACTION_NEXT = LofigaNotificationProvider.ACTION_NEXT
        const val ACTION_PREV = LofigaNotificationProvider.ACTION_PREV
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

        // The custom LofigaNotificationProvider renders its own
        // [Previous] [Play/Pause] [Next] row via getMediaButtons(), so no
        // setMediaButtonPreferences() is needed. The session commands below
        // are still granted to connecting controllers (SystemUI, notification
        // manager) so the custom next/prev actions can be dispatched.
        val sessionCommands = SessionCommands.Builder()
            .add(nextCommand)
            .add(prevCommand)
            .build()

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ConnectionResult {
                // Pre/next are handled by the two custom SessionCommandButtons
                // that LofigaNotificationProvider renders. If the player's own
                // COMMAND_SEEK_TO_NEXT/PREVIOUS stayed in availableCommands,
                // SystemUI's transport row would double the buttons. Trim those
                // four so only the provider's row appears.
                val playerCommands = session.player.availableCommands.buildUpon()
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return ConnectionResult.accept(sessionCommands, playerCommands)
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
