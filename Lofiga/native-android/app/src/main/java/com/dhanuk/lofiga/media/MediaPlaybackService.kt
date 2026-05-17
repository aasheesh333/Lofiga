package com.dhanuk.lofiga.media

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
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun showAsForeground(isPlaying: Boolean) {
        val nm = notificationManager ?: return
        val sm = sessionManager ?: return

        val notification = nm.buildNotification(
            sm.token,
            currentTitle,
            currentArtist,
            isPlaying
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                MediaNotificationManager.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        notificationManager?.dismiss()
        super.onDestroy()
    }
}
