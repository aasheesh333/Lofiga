package com.dhanuk.lofiga.media

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.dhanuk.lofiga.audio.AudioEngine

class MediaSessionManager(context: Context) {

    private val mediaSession = MediaSessionCompat(context, "LofigaMediaSession").apply {
        isActive = true
        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = audioEngine?.play()
            override fun onPause() = audioEngine?.pause()
            override fun onStop() {
                audioEngine?.stop()
                isActive = false
            }
            override fun onSeekTo(pos: Long) = audioEngine?.seekTo(pos)
        })
    }

    private var audioEngine: AudioEngine? = null

    val token: MediaSessionCompat.Token get() = mediaSession.sessionToken

    fun attach(engine: AudioEngine) {
        audioEngine = engine
    }

    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
    }

    fun updateMetadata(title: String, artist: String) {
        mediaSession.setMetadata(
            android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, "Lofiga")
                .build()
        )
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }
}
