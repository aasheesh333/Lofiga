package com.dhanuk.lofiga.media

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MediaPlaybackService : Service() {

    companion object {
        var notificationManager: MediaNotificationManager? = null
        var sessionManager: MediaSessionManager? = null
        var currentTitle: String = ""
        var currentArtist: String = ""

        const val ACTION_START = "com.dhanuk.lofiga.START"
        const val ACTION_STOP = "com.dhanuk.lofiga.STOP"
        const val ACTION_PLAY = "com.dhanuk.lofiga.PLAY"
        const val ACTION_PAUSE = "com.dhanuk.lofiga.PAUSE"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val isPlaying = intent.getBooleanExtra("is_playing", true)
                showAsForeground(isPlaying)
            }
            ACTION_STOP -> {
                sessionManager?.stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager?.dismiss()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                sessionManager?.playPlayback()
                showAsForeground(true)
            }
            ACTION_PAUSE -> {
                sessionManager?.pausePlayback()
                showAsForeground(false)
            }
            else -> {
                // Null intent (system restart) or unrecognized action.
                // We use START_NOT_STICKY so this shouldn't normally happen,
                // but guard against ANR by stopping immediately.
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun showAsForeground(isPlaying: Boolean) {
        val nm = notificationManager
        val sm = sessionManager

        val notification: Notification = if (nm != null && sm != null) {
            nm.buildNotification(
                sm.token,
                currentTitle,
                currentArtist,
                isPlaying
            )
        } else {
            // Managers not yet initialized — build a minimal fallback notification
            // so startForeground() is still called, avoiding an ANR on Android 12+.
            NotificationCompat.Builder(this, MediaNotificationManager.CHANNEL_ID)
                .setContentTitle("Lofiga")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                MediaNotificationManager.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
        }

        // If managers were null we showed a fallback just to satisfy startForeground;
        // stop immediately since we can't do anything useful without them.
        if (nm == null || sm == null) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        notificationManager?.dismiss()
        super.onDestroy()
    }
}
