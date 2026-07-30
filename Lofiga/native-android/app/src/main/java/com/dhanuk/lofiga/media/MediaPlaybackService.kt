package com.dhanuk.lofiga.media

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSessionService

/**
 * Foreground service that hosts the Media3 [MediaSession] for background-playback
 * controls and lock-screen/notification media buttons.
 *
 * Replaces the legacy hand-rolled Service + static-companion state. Because this
 * is a [MediaSessionService], the framework produces the media notification and
 * binds controller connections here automatically; playback itself still lives in
 * [com.dhanuk.lofiga.audio.AudioEngine]'s ExoPlayer, owned by MainViewModel.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? {
        // The session is owned by MediaSessionManagerHolder, set up once the
        // ViewModel creates the player.
        return MediaSessionManagerHolder.mediaSession
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
        // Don't release the player here — AudioEngine owns it via the ViewModel.
        super.onDestroy()
    }
}

/**
 * Process-wide holder for the current Media3 [MediaSession]. Set by MainViewModel
 * once the player/session is created. Far smaller surface than the prior static
 * state holding titles/managers byte-for-byte.
 */
object MediaSessionManagerHolder {
    @Volatile var mediaSession: MediaSession? = null
}
