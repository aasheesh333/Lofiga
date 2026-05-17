package com.dhanuk.lofiga.media

import android.app.Service
import android.content.Intent
import android.os.IBinder

class MediaPlaybackService : Service() {

    companion object {
        var notificationManager: MediaNotificationManager? = null
        var sessionManager: MediaSessionManager? = null
        var currentTitle: String = ""
        var currentArtist: String = ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            "play" -> sessionManager?.let { sm ->
                sm.updatePlaybackState(true, 0, 0)
                notifyChanged(true)
            }
            "pause" -> sessionManager?.let { sm ->
                sm.updatePlaybackState(false, 0, 0)
                notifyChanged(false)
            }
            "stop" -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager?.dismiss()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notifyChanged(isPlaying: Boolean) {
        val nm = notificationManager ?: return
        val sm = sessionManager ?: return
        val notification = nm.buildNotification(
            sm.token,
            currentTitle,
            currentArtist,
            isPlaying
        )
        nm.show(notification)
    }

    override fun onDestroy() {
        notificationManager?.dismiss()
        super.onDestroy()
    }
}
