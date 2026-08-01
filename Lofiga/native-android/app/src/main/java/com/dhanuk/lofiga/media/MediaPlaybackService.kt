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

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? {
        return MediaSessionManagerHolder.mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val PLACEHOLDER_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lofiga_playback"
    }
}

object MediaSessionManagerHolder {
    @Volatile var mediaSession: MediaSession? = null
}
