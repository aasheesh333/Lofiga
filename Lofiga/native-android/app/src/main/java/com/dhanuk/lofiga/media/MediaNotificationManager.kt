package com.dhanuk.lofiga.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.dhanuk.lofiga.MainActivity
import com.dhanuk.lofiga.R

class MediaNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "lofiga_playback"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current track and playback controls"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildNotification(
        token: android.support.v4.media.session.MediaSessionCompat.Token,
        title: String,
        artist: String,
        isPlaying: Boolean
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                createMediaButtonIntent(MediaPlaybackService.ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                createMediaButtonIntent(MediaPlaybackService.ACTION_PLAY)
            )
        }

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Stop",
            createMediaButtonIntent(MediaPlaybackService.ACTION_STOP)
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(createMediaButtonIntent(MediaPlaybackService.ACTION_STOP))
            .addAction(stopAction)
            .addAction(playPauseAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun show(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun dismiss() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createMediaButtonIntent(action: String): PendingIntent {
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
