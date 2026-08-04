package com.dhanuk.lofiga.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dhanuk.lofiga.model.MoodTag
import com.dhanuk.lofiga.model.PresetValues
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
class AudioEngine(private val context: Context) {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var waveformSeq = 0L
    private val _waveformData = MutableStateFlow(WaveformSnapshot(List(16) { 0f }, 0L))
    val waveformData: StateFlow<WaveformSnapshot> = _waveformData.asStateFlow()

    private val _fftData = MutableStateFlow(List(16) { 0f })
    val fftData: StateFlow<List<Float>> = _fftData.asStateFlow()

    // --- Mood tags (C.2) ---
    // Per-track MoodTag, keyed by the same AudioTrack.id (Long) used by
    // MainViewModel's filteredSongs. Updated after a track's FFT precompute
    // completes — the mood is derived from the same spectrum bins we already
    // have, so there's no extra decode work.
    private val _moodTags = MutableStateFlow<Map<Long, MoodTag>>(emptyMap())
    val moodTags: StateFlow<Map<Long, MoodTag>> = _moodTags.asStateFlow()

    data class WaveformSnapshot(val data: List<Float>, val seq: Long)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    var currentTrackTitle: String = ""
    var currentTrackArtist: String = ""
    var albumArtUri: Uri? = null
    /** MediaStore id (or 0 for file-picked tracks) of the currently-loaded
     *  track. Used as the key into [moodTags] so the FFT precompute can stash
     *  a mood classification once it has the spectrum in hand. */
    var currentTrackId: Long = 0
    var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null

    private var exoPlayer: ExoPlayer? = null
    private val atmospherePlayers = ConcurrentHashMap<String, MediaPlayer>()
    private val atmosphereVolumes = ConcurrentHashMap<String, Float>()

    private var reverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
        data class FrameData(val timeMs: Long, val magnitudes: List<Float>)
    private var precomputedFrames = mutableListOf<FrameData>()
    private val framesLock = Any()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var positionJob: Job? = null
    private var fftJob: Job? = null
    /** Per-loaded-track generation token. The FFT loop bails if the live
     *  generation changes, so rapid back-to-back loads can't interleave their
     *  decoded frames (a flat `cancelled` flag races the new job's reset). */
    @Volatile private var fftGeneration = 0L
    @Volatile private var wasPlayingBeforeFocusLoss = false
    @Volatile private var isDucked = false
    @Volatile private var autoPlayOnPrepared = false

    // Animation for visualization while FFT is computing
    @Volatile private var animatingWaveform = false

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent focus loss - pause playback and forget the focus
                // we previously held, so the next user-initiated play() will
                // re-request it rather than silently play without focus.
                wasPlayingBeforeFocusLoss = _isPlaying.value
                hasAudioFocus = false
                audioFocusRequest = null
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary focus loss (e.g., phone call) - pause and remember state.
                // Focus is still nominally ours; we'll get GAIN when it's back.
                wasPlayingBeforeFocusLoss = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck both the main track AND the atmosphere layers temporarily
                isDucked = true
                exoPlayer?.volume = 0.3f
                duckAtmospheres(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained - restore full volume on everything
                hasAudioFocus = true
                isDucked = false
                exoPlayer?.volume = 1.0f
                duckAtmospheres(false)
                if (wasPlayingBeforeFocusLoss) {
                    play()
                }
            }
        }
    }

    companion object {
        val ATMOSPHERE_KEYS = listOf("rain", "vinyl", "wind", "tape")
        val ATMOSPHERE_FILES = mapOf(
            "rain" to "atmosphere/rain_loop.wav",
            "vinyl" to "atmosphere/vinyl_crackle.wav",
            "wind" to "atmosphere/wind_blow.wav",
            "tape" to "atmosphere/tape_hiss.wav"
        )
        val ATMOSPHERE_LABELS = mapOf(
            "rain" to "Rain",
            "vinyl" to "Vinyl",
            "wind" to "Wind",
            "tape" to "Tape"
        )
        // Higher priority for audio effects to ensure they aren't dropped
        private const val EFFECT_PRIORITY = 1
    }

    suspend fun init() {
        withContext(Dispatchers.IO) {
            ATMOSPHERE_KEYS.forEach { key ->
                createAtmospherePlayer(key)
            }
        }
    }

    // --- Main Track ---

    private fun requestAudioFocus(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioFocusRequest = request
            val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasAudioFocus = granted
            granted
        } else {
            @Suppress("DEPRECATION")
            val granted = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasAudioFocus = granted
            granted
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        audioFocusRequest = null
        hasAudioFocus = false
    }

    fun loadTrack(uri: Uri, autoPlay: Boolean = false): Boolean {
        return loadTrackInternal(
            autoPlay = autoPlay,
            sourceUri = uri,
            filePath = null,
            initFft = { precomputeFFT(context, uri) },
            errorPrefix = "Failed to load track"
        )
    }

    fun loadTrackFromFile(filePath: String, autoPlay: Boolean = false): Boolean {
        return loadTrackInternal(
            autoPlay = autoPlay,
            sourceUri = Uri.fromFile(File(filePath)),
            filePath = filePath,
            initFft = { precomputeFFT(filePath) },
            errorPrefix = "Failed to load file"
        )
    }

    private var pendingInitFft: () -> Unit = {}
    private var pendingErrorPrefix: String = "Failed to load track"

    /** Creates the single ExoPlayer instance reused across track loads. The
     *  Media3 session stays bound to this player for the app's lifetime;
     *  recreating the player per track releases the session's controller and
     *  kills the media notification's controls on every track change. */
    private fun createPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false // we manage focus ourselves below
            )
            // Pause when headphones unplug or audio routes to a device the
            // user doesn't expect (BT disconnect etc.) — Android UX/PPlay
            // convention for media apps. handleAudioBecomingNoisy is
            // independent of handleAudioFocus (which we manage manually).
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val current = exoPlayer ?: return
                if (state == Player.STATE_READY) {
                    _duration.value = current.duration.coerceAtLeast(0L)
                    if (autoPlayOnPrepared) {
                        try {
                            current.play()
                            _isPlaying.value = true
                            _position.value = current.currentPosition
                            startPositionPolling()
                            syncAtmospheres()
                            applyStoredPlaybackParams()
                            onPlaybackStateChanged?.invoke(true)
                            Log.i("AudioEngine", "Auto-play started, position: ${_position.value}")
                        } catch (e: Exception) {
                            Log.e("AudioEngine", "Auto-play failed: ${e.message}")
                        }
                    }
                    // Initialize the audiofx chain on ExoPlayer's audio session.
                    // Run synchronously on the player callback thread (this
                    // is the ExoPlayer application-thread) — the audiofx
                    // constructors are not slow enough to justify a
                    // background coroutine, and running them inline avoids
                    // the race where a rapid track-switch would
                    // releaseEffects() while a previous initEffects()
                    // coroutine was still half-building. Removing the
                    // launch also fixes the half-builtin audiofx leak
                    // the audit flagged as F3.
                    try {
                        initEffects(current.audioSessionId)
                    } catch (e: Exception) {
                        Log.e("AudioEngine", "Effects init failed: ${e.message}")
                    }
                    val myGen = fftGeneration
                    fftJob = scope.launch(Dispatchers.IO) {
                        if (myGen == fftGeneration) pendingInitFft()
                    }
                    startAnimatingWaveform()
                    Log.i("AudioEngine", "Track loaded, duration: ${current.duration}ms")
                } else if (state == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    positionJob?.cancel()
                    pauseAtmospheres()
                    onPlaybackStateChanged?.invoke(false)
                    Log.i("AudioEngine", "Track playback completed")
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _error.value = "${pendingErrorPrefix}: ${error.message}"
                Log.e("AudioEngine", "Player error: ${error.message}", error)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // ExoPlayer fires this when its audio-sink session id changes —
                // typically on a routing change (BT disconnect, new device).
                // audiofx (PresetReverb / BassBoost / Equalizer) are bound to
                // an audio session id; binding to the old session id quietly
                // no-ops the effects. Re-init on the new session so the
                // user's effects stay applied across the route change. This
                // is the audit's F4 issue.
                Log.i("AudioEngine", "Audio session id changed -> $audioSessionId; re-initializing effects")
                try {
                    initEffects(audioSessionId)
                } catch (e: Exception) {
                    Log.e("AudioEngine", "Effects re-init on session change failed: ${e.message}")
                }
            }
        })
        return player
    }

    private fun loadTrackInternal(
        autoPlay: Boolean,
        sourceUri: Uri,
        filePath: String?,
        initFft: () -> Unit,
        errorPrefix: String
    ): Boolean {
        _isPlaying.value = false
        val player = exoPlayer ?: createPlayer().also { exoPlayer = it }
        pendingInitFft = initFft
        pendingErrorPrefix = errorPrefix
        // Deterministic start: playback begins only from the STATE_READY
        // handler when autoPlayOnPrepared is set.
        player.playWhenReady = false
        player.stop()
        releaseEffects()
        _error.value = null
        _position.value = 0
        _duration.value = 0
        resetWaveformData()
        fftJob?.cancel()
        fftGeneration++           // any in-flight decode loop will see this and bail
        animatingWaveform = false
        autoPlayOnPrepared = autoPlay

        if (!requestAudioFocus()) {
            _error.value = "Cannot get audio focus"
            return false
        }

        return try {
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(currentTrackTitle.ifBlank { "Unknown Track" })
                        .setArtist(currentTrackArtist.ifBlank { "Unknown Artist" })
                        .setArtworkUri(albumArtUri)
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            storedTempo = pendingTempo
            storedPitch = pendingPitch
            true
        } catch (e: Exception) {
            _error.value = "$errorPrefix: ${e.message}"
            abandonAudioFocus()
            Log.e("AudioEngine", "$errorPrefix: ${e.message}", e)
            false
        }
    }

    fun isReady(): Boolean = exoPlayer != null

    fun clearError() {
        _error.value = null
    }

    fun play() {
        val player = exoPlayer
        if (player == null) {
            _error.value = "No track loaded"
            return
        }
        // If we previously lost permanent audio focus (e.g. another media app
        // took it), abandon cleared hasAudioFocus. Re-request before resuming
        // play — otherwise the OS will silently re-duck us the instant the
        // other app plays. Skip on the very first play() since loadTrack*
        // already requested focus on our behalf.
        if (!hasAudioFocus) {
            if (!requestAudioFocus()) {
                _error.value = "Another app is using audio"
                Log.w("AudioEngine", "play(): audio focus denied")
                return
            }
        }
        try {
            if (!_isPlaying.value) {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player.play()
                _isPlaying.value = true

                // Immediately set current position
                try {
                    _position.value = player.currentPosition
                } catch (e: Exception) {}

                startPositionPolling()
                syncAtmospheres()
                applyStoredPlaybackParams()
                onPlaybackStateChanged?.invoke(true)
                android.util.Log.i("AudioEngine", "Play started, position: ${_position.value}, duration: ${_duration.value}")
            }
        } catch (e: Exception) {
            _error.value = "Playback error: ${e.message}"
            Log.e("AudioEngine", "Playback error: ${e.message}", e)
        }
    }


    fun pause() {
        val player = exoPlayer
        if (player == null) return
        wasPlayingBeforeFocusLoss = false
        try {
            if (_isPlaying.value) {
                player.pause()
            }
            _isPlaying.value = false
            positionJob?.cancel()
            pauseAtmospheres()
            onPlaybackStateChanged?.invoke(false)
        } catch (e: Exception) {
            _error.value = "Pause error: ${e.message}"
            Log.e("AudioEngine", "Playback error: ${e.message}", e)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer
        if (player == null) {
            _error.value = "No track loaded"
            return
        }
        try {
            if (_isPlaying.value) {
                player.pause()
                _isPlaying.value = false
                positionJob?.cancel()
                pauseAtmospheres()
                onPlaybackStateChanged?.invoke(false)
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player.play()
                _isPlaying.value = true
                startPositionPolling()
                syncAtmospheres()
                onPlaybackStateChanged?.invoke(true)
            }
        } catch (e: Exception) {
            _error.value = "Play/Pause error: ${e.message}"
            Log.e("AudioEngine", "Playback error: ${e.message}", e)
        }
    }


    fun seekTo(millis: Long) {
        try {
            exoPlayer?.seekTo(millis.coerceIn(0L, _duration.value.coerceAtLeast(0L)))
            _position.value = millis
            // Animate only while there is genuinely no FFT data at all. When
            // frames exist, the polling loop (or the self-terminating check
            // inside startAnimatingWaveform) keeps live data on screen.
            val framesEmpty = synchronized(framesLock) { precomputedFrames.isEmpty() }
            if (framesEmpty && !animatingWaveform && _isPlaying.value) {
                startAnimatingWaveform()
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Seek failed: ${e.message}", e)
        }
    }

    fun setLooping(loop: Boolean) {
        _isLooping.value = loop
        exoPlayer?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun toggleLoop() {
        setLooping(!_isLooping.value)
    }

    fun stop() {
        _isPlaying.value = false
        wasPlayingBeforeFocusLoss = false
        pauseAtmospheres()
        exoPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}
        }
        _position.value = 0
        positionJob?.cancel()
        abandonAudioFocus()
        onPlaybackStateChanged?.invoke(false)
    }

    fun release() {
        fftGeneration++            // any in-flight FFT decode loop will see this and bail
        positionJob?.cancel()
        scope.cancel()
        releaseExoPlayer()
        releaseAtmospherePlayers()
        releaseEffects()
    }
    // Stored playback params to apply after player starts
    private var pendingTempo: Float = 1.0f
    private var pendingPitch: Float = 0f
    private var storedTempo: Float = 1.0f
    private var storedPitch: Float = 0f

    private fun applyStoredPlaybackParams() {
        exoPlayer?.let { player ->
            try {
                val tempo = storedTempo.coerceIn(0.1f, 3.0f)
                val semitones = storedPitch
                val pitchFactor = if (semitones != 0f) {
                    Math.pow(2.0, (semitones / 12.0).toDouble()).toFloat()
                } else 1f
                // Media3 Sonic tempo/pitch are INDEPENDENT — tempo changes speed
                // without changing pitch, pitch changes pitch without changing
                // duration. This matches the offline export (Phase A2 fix) and
                // fixes the preview/export mismatch at the source.
                player.setPlaybackParameters(
                    PlaybackParameters(tempo, pitchFactor.coerceIn(0.1f, 3.0f))
                )
            } catch (e: Exception) {
                Log.e("AudioEngine", "Playback params error: ${e.message}", e)
            }
        }
    }

    // --- Speed & Pitch (INDEPENDENT via Media3 Sonic) ---

    fun setSpeedAndPitch(tempo: Float, semitones: Float) {
        pendingTempo = tempo
        pendingPitch = semitones
        storedTempo = tempo
        storedPitch = semitones
        // If player is already playing, apply immediately
        val player = exoPlayer
        if (player != null && player.isPlaying) {
            applyStoredPlaybackParams()
        }
    }

    // --- Audio Effects ---

    private fun initEffects(audioSessionId: Int) {
        // Release any previous audiofx first — when called via the
        // onAudioSessionIdChanged routing callback, we're re-binding to a new
        // session; when called fresh after a track switch, caller has already
        // invoked releaseEffects() but this is a defensive no-op in that case.
        try { releaseEffects() } catch (_: Exception) {}
        try {
            reverb = PresetReverb(EFFECT_PRIORITY, audioSessionId).apply {
                enabled = true
                preset = PresetReverb.PRESET_SMALLROOM
            }
            
            // MAGIC FIX: Do NOT call attachAuxEffect. It crashes the user's firmware.
            // ExoPlayer does not expose setAuxEffectSendLevel (MediaPlayer does); the
            // PresetReverb inserted on the audio session still applies on its own.
            // Side note: ExoPlayer routes audio through its own AudioSink; the
            // aux-send level workaround was a MediaPlayer-only path.

            bassBoost = BassBoost(EFFECT_PRIORITY, audioSessionId).apply {
                enabled = true
                setStrength(0.toShort())
            }

            equalizer = Equalizer(EFFECT_PRIORITY, audioSessionId).apply {
                enabled = true
                val bands = numberOfBands
                for (i in 0 until bands) {
                    setBandLevel(i.toShort(), 0.toShort())
                }
            }

            android.util.Log.i("AudioEngine", "Effects initialized on session $audioSessionId")
            
            // Apply any pending effect values that were set before effects were initialized
            if (pendingReverb > 0f || pendingDelay > 0f) {
                setReverbAndDelay(pendingReverb, pendingDelay)
            }
            if (pendingBass > 0f) {
                setBassBoost(pendingBass)
            }
            if (pendingTreble > 0f) {
                setTrebleCut(pendingTreble)
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioEngine", "Failed to init effects: ${e.message}")
        }
    }

    /**
     * Set the reverb wet level. This routes through the unified [setReverbAndDelay]
     * so the reverb preset mapping and aux send level stay consistent regardless
     * of which call site is used (previously this had its own divergent thresholds,
     * leaving two conflicting code paths for the same effect).
     */
    fun setReverb(wet: Float) {
        setReverbAndDelay(wet, pendingDelay)
    }

    private var pendingReverb: Float = 0f
    private var pendingDelay: Float = 0f
    private var pendingBass: Float = 0f
    private var pendingTreble: Float = 0f

    fun setBassBoost(strength: Float) {
        pendingBass = strength
        bassBoost?.let {
            try {
                val s = (strength * 1000).toInt().coerceIn(0, 1000).toShort()
                it.setStrength(s)
                it.enabled = strength > 0.01f
            } catch (e: Exception) { Log.e("AudioEngine", "Reverb error: ${e.message}", e) }
        }
    }

    fun setTrebleCut(cutoffFactor: Float) {
        pendingTreble = cutoffFactor
        equalizer?.let { eq ->
            try {
                if (cutoffFactor > 0.01f) {
                    val bands = eq.numberOfBands
                    for (i in 0 until bands) {
                        val freq = eq.getCenterFreq(i.toShort())
                        if (freq > 2_000_000) {
                            val gain = (-(cutoffFactor * 1500f)).toInt().coerceIn(-1500, 0).toShort()
                            eq.setBandLevel(i.toShort(), gain)
                        } else {
                            eq.setBandLevel(i.toShort(), 0.toShort())
                        }
                    }
                    eq.enabled = true
                } else {
                    val bands = eq.numberOfBands
                    for (i in 0 until bands) {
                        eq.setBandLevel(i.toShort(), 0.toShort())
                    }
                    eq.enabled = false
                }
            } catch (e: Exception) { Log.e("AudioEngine", "Reverb error: ${e.message}", e) }
        }
    }

    fun setDelay(wet: Float) {
        // No-op: delay is combined with reverb via setReverbAndDelay()
        // to prevent overriding the reverb setting.
    }

    /**
     * Combines both reverb and delay into a single reverb effect since
     * Android's AudioEffect framework only provides PresetReverb.
     */
    fun setReverbAndDelay(reverbWet: Float, delayWet: Float) {
        pendingReverb = reverbWet
        pendingDelay = delayWet
        reverb?.let { r ->
            // Reverb changes are IPC calls. Running them on UI thread causes instant ANR when dragging sliders!
            scope.launch(Dispatchers.IO) {
                try {
                    val combined = (reverbWet + delayWet * 1.0f).coerceIn(0f, 1f)
                    val wasEnabled = r.enabled
                    val shouldEnable = combined > 0.01f
                    if (wasEnabled != shouldEnable) r.enabled = shouldEnable
                    
                    if (shouldEnable) {
                        r.preset = when {
                            combined < 0.06f -> PresetReverb.PRESET_NONE
                            combined < 0.15f -> PresetReverb.PRESET_SMALLROOM
                            combined < 0.30f -> PresetReverb.PRESET_MEDIUMROOM
                            combined < 0.55f -> PresetReverb.PRESET_LARGEHALL
                            else -> PresetReverb.PRESET_PLATE
                        }
                    }
                    // ExoPlayer has no setAuxEffectSendLevel; the reverb preset above
                    // is the effective control. (MediaPlayer-only workaround removed.)
                } catch (e: Exception) { android.util.Log.e("AudioEngine", "Reverb error: ${e.message}", e) }
            }
        }
    }

    // --- Real FFT Visualization (radix-2 FFT, log-spaced bands) ---

    private fun startAnimatingWaveform() {
        // Cancel any previous animation loop first so rapid track switches don't
        // end up with two loops writing to the same flows.
        animatingWaveform = false
        scope.launch {
            // brief yield so any previous loop sees the flag and exits
            delay(20)
            animatingWaveform = true
            try {
                var phase = 0f
                while (animatingWaveform && isActive) {
                    // Hand back to the position-polling loop the moment real FFT
                    // data covers the current position, so the fake pattern can
                    // never outlive the real one (previously it ran forever once
                    // a seek started it, freezing the waveform on fake data).
                    val liveNearby = synchronized(framesLock) {
                        val frames = precomputedFrames
                        if (frames.isEmpty()) return@synchronized false
                        // Read from the position StateFlow (written by the
                        // main-thread polling loop) — accessing the ExoPlayer
                        // from this Dispatchers.Default coroutine throws
                        // IllegalStateException (player accessed on wrong
                        // thread).
                        val pos = _position.value
                        if (pos < 0) return@synchronized false
                        var left = 0
                        var right = frames.size - 1
                        var best = frames[0]
                        while (left <= right) {
                            val mid = (left + right) / 2
                            if (frames[mid].timeMs <= pos) {
                                best = frames[mid]
                                left = mid + 1
                            } else {
                                right = mid - 1
                            }
                        }
                        pos - best.timeMs <= 1500
                    }
                    if (liveNearby) {
                        animatingWaveform = false
                        return@launch
                    }
                    // Produce a clearly-visible animated pattern while waiting for FFT.
                    phase += 0.18f
                    val animated = List(16) { i ->
                        val base = 0.10f + 0.35f * (kotlin.math.sin(phase + i * 0.55f) * 0.5f + 0.5f)
                        base.coerceIn(0f, 1f)
                    }
                    synchronized(framesLock) {
                        if (animatingWaveform) {
                            _fftData.value = animated
                            _waveformData.value = WaveformSnapshot(animated, ++waveformSeq)
                        }
                    }
                    delay(80)
                }
            } finally {
                animatingWaveform = false
            }
        }
    }

    private fun resetWaveformData() {
        synchronized(framesLock) {
            precomputedFrames.clear()
        }
        animatingWaveform = false
        _waveformData.value = WaveformSnapshot(List(16) { 0f }, ++waveformSeq)
        _fftData.value = List(16) { 0f }
    }

    /**
     * FFT precomputation: full-decode the track via MediaExtractor + MediaCodec,
     * then run a real radix-2 FFT over 1024-sample Hann-windowed hops and store
     * 16 log-spaced magnitude bands per timestamp. The polling loop then
     * binary-searches the result by current playback position.
     */
    private fun precomputeFFT(context: Context, uri: Uri): Boolean {
        return try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                decodeAndPrecomputeFFT(extractor) { newFrames ->
                    synchronized(framesLock) {
                        precomputedFrames.addAll(newFrames)
                        animatingWaveform = false // Switch to live data instantly on first batch
                    }
                }
            } finally {
                extractor.release()
            }
            // C.2: derive a mood classification from the spectrum we just built.
            // (Identical block exists in the filePath overload — kept inline rather
            // than extracted to keep the algorithm local to its caller.)
            runCatching { stashMoodTagForCurrentTrack() }
            animatingWaveform = false
            android.util.Log.i("AudioEngine", "FFT precompute done: ${precomputedFrames.size} frames")
            true
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "FFT precompute failed: ${e.message}")
            false
        } finally {
            animatingWaveform = false
        }
    }

    private fun precomputeFFT(filePath: String): Boolean {
        return try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(filePath)
                decodeAndPrecomputeFFT(extractor) { newFrames ->
                    synchronized(framesLock) {
                        precomputedFrames.addAll(newFrames)
                        animatingWaveform = false // Switch to live data instantly on first batch
                    }
                }
            } finally {
                extractor.release()
            }
            // C.2: same mood-classification step as the uri overload.
            // For file-picked tracks currentTrackId == 0, so the tag is keyed
            // under 0 — the library filter (keyed by MediaStore ids only) will
            // simply ignore it, which is fine for now.
            runCatching { stashMoodTagForCurrentTrack() }
            animatingWaveform = false
            android.util.Log.i("AudioEngine", "FFT precompute done: ${precomputedFrames.size} frames")
            true
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "FFT precompute failed: ${e.message}")
            false
        } finally {
            animatingWaveform = false
        }
    }

    /**
     * Mean spectral centroid across all precomputed frames, mapped to a [MoodTag]
     * and stashed in [_moodTags] under [currentTrackId]. Cheap: O(frames * 16)
     * — runs once after the FFT pass. Bails out when there are no frames yet
     * (cancelled before any window was processed).
     */
    private fun stashMoodTagForCurrentTrack() {
        val frames = synchronized(framesLock) { precomputedFrames.toList() }
        if (frames.isEmpty()) return
        var centroidSum = 0f
        for (frame in frames) {
            val mags = frame.magnitudes
            var totalMass = 0f
            for (band in mags.indices) totalMass += mags[band]
            if (totalMass <= 1e-6f) continue
            var cumMass = 0f
            var centroid = 0f
            for (band in mags.indices) {
                cumMass += mags[band]
                if (cumMass >= totalMass * 0.5f) {
                    centroid = band.toFloat()
                    break
                }
            }
            centroidSum += centroid
        }
        val meanCentroid = centroidSum / frames.size
        val tag = MoodTag.fromCentroid(meanCentroid)
        val updated = _moodTags.value + (currentTrackId to tag)
        // Bound the map: mood tags are session-scoped (never persisted) and
        // re-derived on the next play, so keep only the most recent tags.
        _moodTags.value = if (updated.size > MAX_MOOD_TAGS) {
            updated.toList().drop(updated.size - MAX_MOOD_TAGS).toMap()
        } else {
            updated
        }
    }

    /** Max per-track mood tags held in memory (LRU-ish trim above). */
    private val MAX_MOOD_TAGS = 500

    /**
     * Cap on the number of precomputed FFT frames we keep.
     * Beyond this we still decode (to let the codec exit cleanly) but
     * stop adding frames so the binary-search index stays small and
     * memory footprint stays bounded even on multi-hour mixes.
     */
    private val MAX_FFT_FRAMES = 8000

    private fun decodeAndPrecomputeFFT(extractor: MediaExtractor, onBatch: (List<FrameData>) -> Unit) {
        val myGen = fftGeneration  // capture once; bail if a newer load bumps it
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("audio/")) continue

            extractor.selectTrack(i)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            try {
                val bufferInfo = MediaCodec.BufferInfo()
            val batch = mutableListOf<FrameData>()
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            var sawInputEOS = false
            var sawOutputEOS = false
            var frameCount = 0

            val windowSize = 1024  // power of two (radix-2 FFT requirement)

            // Adaptive hop: aim for ~16 frames/s so a 10-min track → ~9600 frames,
            // then widen once we approach MAX_FFT_FRAMES so long tracks stay capped.
            val baseHop = (sampleRate / 16).coerceIn(windowSize, windowSize * 8)

            while (!sawOutputEOS) {
                if (myGen != fftGeneration) break   // a newer load arrived — abandon this decode
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(5000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer == null) {
                            // Re-queue an empty buffer to free the slot, then try again.
                            try {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
                            } catch (_: Exception) {}
                            continue
                        }
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            sawInputEOS = true
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
                if (outputIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: run {
                        codec.releaseOutputBuffer(outputIndex, false)
                        continue
                    }
                    val size = bufferInfo.size
                    if (size > 0 && frameCount < MAX_FFT_FRAMES) {
                        val shorts = ShortArray(size / 2)
                        outputBuffer.rewind()
                        outputBuffer.asShortBuffer().get(shorts)

                        val timeMsBase = bufferInfo.presentationTimeUs / 1000
                        // Adaptive hop: start at baseHop, double it once we're past 3/4 of the frame cap.
                        val hopSize = if (frameCount < (MAX_FFT_FRAMES * 3) / 4) {
                            baseHop
                        } else {
                            baseHop * 2
                        }

                        // Walk the output buffer in mono frames: one window is windowSize frames
                        // (each frame = `channels` interleaved shorts).
                        val frameSizeBytes = channels * 2
                        val totalFrames = shorts.size / channels
                        var frameStart = 0
                        while (frameStart + windowSize <= totalFrames && frameCount < MAX_FFT_FRAMES) {
                            // Downmix the window to mono (average all channels).
                            val mono = ShortArray(windowSize)
                            val srcBase = frameStart * channels
                            for (w in 0 until windowSize) {
                                val baseIdx = srcBase + w * channels
                                var sum = 0
                                for (ch in 0 until channels) {
                                    sum += shorts[baseIdx + ch].toInt()
                                }
                                mono[w] = (sum / channels).coerceIn(-32768, 32767).toShort()
                            }
                            val offsetMs = (frameStart.toFloat() / sampleRate.toFloat() * 1000f).toLong()
                            batch.add(FrameData(timeMsBase + offsetMs, computeFFTMagnitudes(mono)))
                            frameStart += hopSize
                            frameCount++
                        }
                        if (batch.size >= 20) {
                            onBatch(batch.toList())
                            batch.clear()
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
            if (batch.isNotEmpty()) {
                    onBatch(batch.toList())
                }
            } finally {
                try {
                    codec.stop()
                } catch (_: Exception) {}
                codec.release()
            }
        }
    }

    /**
     * Real radix-2 in-place FFT (Cooley–Tukey). Operates on [real] and [imag]
     * arrays of length n (must be a power of two). Bit-reversal permutation,
     * then the standard iterative butterfly. Pure-Kotlin, no allocations
     * beyond the trivial locals — runs once per window during precompute.
     */
    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit-reversal permutation.
        var j = 0
        var i = 1
        while (i < n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = real[i]; real[i] = real[j]; real[j] = t
                t = imag[i]; imag[i] = imag[j]; imag[j] = t
            }
            i++
        }
        // Butterfly.
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angStep = (-2.0 * Math.PI / len).toFloat()
            for (i in 0 until halfLen) {
                val ang = angStep * i
                val wRe = Math.cos(ang.toDouble()).toFloat()
                val wIm = Math.sin(ang.toDouble()).toFloat()
                var k = i
                while (k < n) {
                    val kRe = real[k]
                    val kIm = imag[k]
                    val jRe = real[k + halfLen]
                    val jIm = imag[k + halfLen]
                    val tRe = wRe * jRe - wIm * jIm
                    val tIm = wRe * jIm + wIm * jRe
                    real[k] = kRe + tRe
                    imag[k] = kIm + tIm
                    real[k + halfLen] = kRe - tRe
                    imag[k + halfLen] = kIm - tIm
                    k += len
                }
            }
            len = len shl 1
        }
    }

    /**
     * Compute 16 log-spaced magnitude bands from a window of mono PCM shorts
     * via a real radix-2 FFT.
     *
     * The caller-specified [samples] length is required to be a power of two
     * (we use 1024) so [fftInPlace] works without zero-padding logic.
     * A Hann window is applied to reduce spectral leakage; the magnitude
     * spectrum is then grouped into 16 log-distributed bins (matching how
     * human hearing and typical equalizers divide the spectrum) and
     * normalized to 0..1.
     */
    private fun computeFFTMagnitudes(samples: ShortArray): List<Float> {
        val n = samples.size
        if (n < 2 || n and (n - 1) != 0) {
            // Not a power of two (shouldn't happen — caller pads to 1024);
            // fall back to a flat-zero result so the UI stays safe.
            return List(16) { 0f }
        }

        val real = FloatArray(n)
        val imag = FloatArray(n)
        // Hann window + float normalization, applied together.
        for (i in 0 until n) {
            val w = 0.5f - 0.5f * Math.cos(2.0 * Math.PI * i / (n - 1)).toFloat()
            val v = (samples[i].toFloat() / 32768f) * w
            real[i] = v
            imag[i] = 0f
        }

        fftInPlace(real, imag)

        // Magnitude spectrum — only the first n/2 bins are meaningful (Nyquist).
        val mag = FloatArray(n / 2)
        for (i in 0 until n / 2) {
            val re = real[i]
            val im = imag[i]
            mag[i] = Math.sqrt((re * re + im * im).toDouble()).toFloat()
        }

        // 16 log-spaced bands over the meaningful spectrum.
        // Bin 0 is DC; we start banding from bin 1 to skip it.
        val bands = 16
        val result = MutableList(bands) { 0f }
        // Log scale: each band spans [lowHz..highHz] mapped onto bin indices.
        // Use a logarithmic factor across bin range 1 .. (n/2 - 1).
        val minBin = 1
        val maxBin = n / 2 - 1
        val logMin = Math.log(minBin.toDouble())
        val logMax = Math.log(maxBin.toDouble())
        var peak = 1e-6f
        for (b in 0 until bands) {
            val loLog = logMin + (logMax - logMin) * (b) / bands
            val hiLog = logMin + (logMax - logMin) * (b + 1) / bands
            val lo = Math.exp(loLog).toInt().coerceIn(minBin, maxBin)
            val hi = Math.exp(hiLog).toInt().coerceIn(minBin, maxBin).coerceAtLeast(lo + 1)
            // Take the max magnitude in the band (peak-hold) — visually punchier than mean.
            var bandMax = 0f
            for (k in lo until hi) {
                if (mag[k] > bandMax) bandMax = mag[k]
            }
            // Normalize: divide by n/2 so a full-scale sine at a band's frequency → ~0.5.
            // Then apply a perceptual scale (sqrt) and a mild gain so quiet tracks still show movement.
            val norm = bandMax / (n / 2f)
            val scaled = Math.sqrt(norm.toDouble()).toFloat() * 2.2f
            result[b] = scaled
            if (scaled > peak) peak = scaled
        }
        // Soft ceiling so occasional extreme peaks don't peg everything else to zero.
        // We normalize against the in-window peak if it's above 1, but never attenuate below it.
        if (peak > 1f) {
            val inv = 1f / peak
            for (b in 0 until bands) result[b] *= inv
        }
        return result.map { it.coerceIn(0f, 1f) }
    }

    // --- Atmosphere System ---

    private fun createAtmospherePlayer(key: String) {
        releaseAtmospherePlayer(key)
        try {
            val assetPath = ATMOSPHERE_FILES[key] ?: return
            val fd = context.assets.openFd(assetPath)
            val player = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(0f, 0f)
                prepare()
            }
            fd.close()
            atmospherePlayers[key] = player
            if (!atmosphereVolumes.containsKey(key)) {
                atmosphereVolumes[key] = 0f
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to create atmosphere player '$key'", e)
        }
    }

    private fun releaseAtmospherePlayer(key: String) {
        atmospherePlayers.remove(key)?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                android.util.Log.w("AudioEngine", "releaseAtmospherePlayer('$key') failed: ${e.message}")
            }
        }
    }

    fun recreateAtmospherePlayers() {
        ATMOSPHERE_KEYS.forEach { key ->
            createAtmospherePlayer(key)
        }
    }

    fun setAtmosphereVolume(key: String, volume: Float) {
        atmosphereVolumes[key] = volume
        var player = atmospherePlayers[key]
        if (player == null) {
            createAtmospherePlayer(key)
            player = atmospherePlayers[key]
        }
        if (player == null) return
        val vol = volume.coerceIn(0f, 1f)
        val effective = if (isDucked) vol * 0.3f else vol
        player.setVolume(effective, effective)
        // Only start atmosphere when the main track is actively playing.
        // When stopped, the stored volume is kept so syncAtmospheres() will
        // start this layer alongside the main track on play().
        if (vol > 0.01f && !player.isPlaying && _isPlaying.value) {
            try {
                player.seekTo(0)
                player.start()
            } catch (e: Exception) {
                android.util.Log.w("AudioEngine", "Atmosphere start ('$key') failed: ${e.message}")
            }
        } else if (vol <= 0.01f && player.isPlaying) {
            player.pause()
        }
    }

    fun setAllAtmosphereVolumes(values: PresetValues) {
        setAtmosphereVolume("rain", values.rainVolume)
        setAtmosphereVolume("vinyl", values.vinylVolume)
        setAtmosphereVolume("wind", values.windVolume)
        setAtmosphereVolume("tape", values.tapeVolume)
    }

    private fun syncAtmospheres() {
        atmospherePlayers.forEach { (key, player) ->
            val vol = atmosphereVolumes[key] ?: 0f
            if (vol > 0.01f && !player.isPlaying) {
                try {
                    player.seekTo(0)
                    player.start()
                } catch (e: Exception) {
                    android.util.Log.w("AudioEngine", "syncAtmospheres start ('$key') failed: ${e.message}")
                }
            } else if (vol <= 0.01f && player.isPlaying) {
                player.pause()
            }
        }
    }

    private fun pauseAtmospheres() {
        atmospherePlayers.values.forEach {
            try {
                if (it.isPlaying) it.pause()
            } catch (e: Exception) {
                android.util.Log.w("AudioEngine", "pauseAtmospheres failed: ${e.message}")
            }
        }
    }

    /**
     * Scales all atmosphere layer volumes down (duck = true) or restores them
     * (duck = false). Keeps the stored target volumes untouched; only the
     * applied output level changes.
     */
    private fun duckAtmospheres(duck: Boolean) {
        atmospherePlayers.forEach { (key, player) ->
            try {
                val vol = (atmosphereVolumes[key] ?: 0f).coerceIn(0f, 1f)
                val effective = if (duck) vol * 0.3f else vol
                player.setVolume(effective, effective)
            } catch (e: Exception) {
                android.util.Log.w("AudioEngine", "duckAtmospheres('$key') failed: ${e.message}")
            }
        }
    }

    // --- Position Polling ---

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                exoPlayer?.let { player ->
                    try {
                        val pos = player.currentPosition
                        if (pos >= 0) {
                            _position.value = pos
                        }
                        val dur = _duration.value
                        synchronized(framesLock) {
                            val frameCount = precomputedFrames.size
                            if (!animatingWaveform && frameCount > 0 && dur > 0 && pos >= 0) {
                                var left = 0
                                var right = frameCount - 1
                                var bestIdx = 0
                                while (left <= right) {
                                    val mid = (left + right) / 2
                                    if (precomputedFrames[mid].timeMs <= pos) {
                                        bestIdx = mid
                                        left = mid + 1
                                    } else {
                                        right = mid - 1
                                    }
                                }

                                val bestFrame = precomputedFrames[bestIdx]
                                // Seeked beyond the last precomputed frame: hold
                                // the nearest frame rather than jumping to the
                                // fake animation pattern.
                                _fftData.value = bestFrame.magnitudes
                                _waveformData.value = WaveformSnapshot(bestFrame.magnitudes, ++waveformSeq)
                            } else if (!animatingWaveform && frameCount == 0 && _isPlaying.value) {
                                startAnimatingWaveform()
                            }
                        }
                    } catch (_: Exception) {}
                }
                delay(32)
            }
        }
    }

    // --- Cleanup ---

    private fun releaseExoPlayer() {
        _isPlaying.value = false
        positionJob?.cancel()
        exoPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (_: Exception) {}
        }
        exoPlayer = null
        abandonAudioFocus()
    }

    /**
     * Ensure main audio session is established before initializing effects.
     */
    fun ensureAudioSessionReady(): Boolean {
        val player = exoPlayer ?: return false
        return try {
            player.audioSessionId > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The underlying ExoPlayer-backed [Player], exposed ONLY so the Media3
     * [MediaSession] / [MediaSessionService] can be bound to the same instance.
     * Callers must NOT mutate it directly; all playback control goes through
     * this engine's public methods.
     */
    val playerForSession: androidx.media3.common.Player? get() = exoPlayer

    private fun releaseAtmospherePlayers() {
        atmospherePlayers.values.forEach {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        atmospherePlayers.clear()
    }

    private fun releaseEffects() {
        reverb?.release()
        bassBoost?.release()
        equalizer?.release()
        reverb = null
        bassBoost = null
        equalizer = null
    }
}