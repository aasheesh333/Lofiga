package com.dhanuk.lofiga.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSessionService
import com.dhanuk.lofiga.MainActivity
import com.dhanuk.lofiga.R

@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        // Custom provider renders exactly [Prev] [Play/Pause] [Next] — the
        // default 1.5.x provider duplicated prev/next (its built-in transport
        // row plus the custom command buttons).
        setMediaNotificationProvider(LofigaNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? {
        return MediaSessionManagerHolder.mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val firstStart = !isRunning
        isRunning = true
        // Post the placeholder ONLY on the first start of the service lifetime.
        // While playing, the Media3 notification manager shows its rich
        // notification by calling startForeground() itself, which triggers
        // ContextCompat.startForegroundService(startSelfIntent) — a second
        // onStartCommand whose startForeground would otherwise re-post this
        // bare placeholder (id 1001) over the controls-bearing notification.
        // While paused, media3 updates via NotificationManagerCompat.notify()
        // (no service restart), so the controls survive regardless.
        if (firstStart) {
            // The placeholder shares DefaultMediaNotificationProvider's default
            // notification id (1001), so the real controls-bearing media
            // notification replaces it once the notification manager's
            // controller connects — no second notification. It also satisfies
            // the 5s foreground-start window while that controller connects.
            val notification = buildPlaceholderNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    PLACEHOLDER_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(PLACEHOLDER_NOTIFICATION_ID, notification)
            }
        }
        // A plain startService intent has no media action, so MediaSessionService
        // never routes it to onGetSession — the session would never reach the
        // MediaNotificationManager and no controls notification would appear.
        // Register the live session explicitly so the notification manager
        // connects its controller, then onConnect grants the next/prev commands
        // and the real notification (id 1001) replaces the placeholder.
        try {
            MediaSessionManagerHolder.mediaSession?.let { addSession(it) }
        } catch (e: Exception) {
            android.util.Log.w("MediaPlaybackService", "addSession failed: ${e.message}")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildPlaceholderNotification(): Notification {
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val contentIntent = PendingIntent.getActivity(this, 0, sessionActivityIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Lofiga")
            .setContentText("Playing music…")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = MediaSessionManagerHolder.mediaSession
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            isRunning = false
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val PLACEHOLDER_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lofiga_playback"

        /** True while the service is running. Lets the ViewModel avoid
         *  re-issuing startForegroundService on every play()/resume and lets
         *  [onStartCommand] post the placeholder notification only on the
         *  service's first start — otherwise every onStartCommand (including
         *  the Media3 notification manager's own startSelfIntent starts while
         *  playing) re-posts the bare placeholder (id 1001) over the rich
         *  Media3 notification, making the controls vanish while playing.
         *  Reset whenever the service actually dies (onTaskRemoved/
         *  onDestroy), so a later play() starts it again. */
        @Volatile var isRunning = false
    }
}

object MediaSessionManagerHolder {
    @Volatile var mediaSession: MediaSession? = null
}
