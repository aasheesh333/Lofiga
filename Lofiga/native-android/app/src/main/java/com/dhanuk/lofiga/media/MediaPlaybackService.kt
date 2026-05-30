package com.dhanuk.lofiga.media

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dhanuk.lofiga.R

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
        // A service started via startForegroundService() MUST call startForeground()
        // within ~5s, even when it is about to stop. Otherwise Android throws
        // ForegroundServiceDidNotStartInTimeException and crashes the app.
        if (intent == null) {
            // Sticky restart with no intent (and possibly no managers): satisfy the
            // foreground contract, then tear down safely.
            startForegroundCompat(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager?.dismiss()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                val isPlaying = intent.getBooleanExtra("is_playing", true)
                startForegroundCompat(isPlaying)
            }
            ACTION_STOP -> {
                // Promote to foreground first to honor the startForegroundService
                // contract, then immediately stop.
                startForegroundCompat(false)
                sessionManager?.stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager?.dismiss()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                sessionManager?.playPlayback()
                startForegroundCompat(true)
            }
            ACTION_PAUSE -> {
                sessionManager?.pausePlayback()
                startForegroundCompat(false)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun showAsForeground(isPlaying: Boolean) = startForegroundCompat(isPlaying)

    private fun startForegroundCompat(isPlaying: Boolean) {
        val nm = notificationManager
        val sm = sessionManager
        val notification = if (nm != null && sm != null) {
            nm.buildNotification(sm.token, currentTitle, currentArtist, isPlaying)
        } else {
            // Fallback notification so we can always satisfy the foreground-service
            // contract even if the managers were not initialized (e.g. a process
            // restart without the UI). The channel is created in LofigaApplication.
            NotificationCompat.Builder(this, MediaNotificationManager.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (currentTitle.isNotEmpty()) currentTitle else "Lofiga")
                .setOngoing(false)
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
    }

    override fun onDestroy() {
        notificationManager?.dismiss()
        super.onDestroy()
    }
}
