package com.dhanuk.lofiga.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import com.dhanuk.lofiga.model.PresetValues
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

    data class WaveformSnapshot(val data: List<Float>, val seq: Long)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    var currentTrackTitle: String = ""
    var currentTrackArtist: String = ""
    var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null

    private var mainPlayer: MediaPlayer? = null
    private val atmospherePlayers = ConcurrentHashMap<String, MediaPlayer>()
    private val atmosphereVolumes = ConcurrentHashMap<String, Float>()

    private var reverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var precomputedFrames = mutableListOf<List<Float>>()
    private val framesLock = Any()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var positionJob: Job? = null
    private var fftJob: Job? = null
    @Volatile private var fftCancelled = false
    @Volatile private var wasPlayingBeforeFocusLoss = false
    @Volatile private var autoPlayOnPrepared = false

    // Animation for visualization while FFT is computing
    private var visualAnimSeq = 0L
    @Volatile private var animatingWaveform = false

    private var audioFocusRequest: AudioFocusRequest? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent focus loss - pause playback
                wasPlayingBeforeFocusLoss = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary focus loss (e.g., phone call) - pause and remember state
                wasPlayingBeforeFocusLoss = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck the volume temporarily
                mainPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained
                mainPlayer?.setVolume(1.0f, 1.0f)
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
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
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
    }

    fun loadTrack(uri: Uri, autoPlay: Boolean = false): Boolean {
        return loadTrackInternal(
            autoPlay = autoPlay,
            setupSource = { player -> player.setDataSource(context, uri) },
            initFft = { precomputeFFTFast(context, uri) },
            errorPrefix = "Failed to load track"
        )
    }

    fun loadTrackFromFile(filePath: String, autoPlay: Boolean = false): Boolean {
        return loadTrackInternal(
            autoPlay = autoPlay,
            setupSource = { player -> player.setDataSource(filePath) },
            initFft = { precomputeFFTFast(filePath) },
            errorPrefix = "Failed to load file"
        )
    }

    private fun loadTrackInternal(
        autoPlay: Boolean,
        setupSource: (MediaPlayer) -> Unit,
        initFft: () -> Unit,
        errorPrefix: String
    ): Boolean {
        _isPlaying.value = false
        releaseMainPlayer()
        releaseEffects()
        _error.value = null
        _position.value = 0
        _duration.value = 0
        resetWaveformData()
        fftJob?.cancel()
        fftCancelled = true
        animatingWaveform = false
        autoPlayOnPrepared = autoPlay

        if (!requestAudioFocus()) {
            _error.value = "Cannot get audio focus"
            return false
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setupSource(this)
            }
            mainPlayer = player
            
            // CRITICAL FIX: Initialize effects on background thread BEFORE prepareAsync()!
            // Calling attachAuxEffect after prepare/start causes fatal MediaPlayer crashes on many OEM ROMs.
            scope.launch(Dispatchers.IO) {
                try {
                    initEffects(player.audioSessionId)
                    // Attach aux effect safely in INITIALIZED state
                    reverb?.let { r ->
                        player.attachAuxEffect(r.id)
                        player.setAuxEffectSendLevel(pendingReverb.coerceIn(0f, 1f))
                    }
                } catch (e: Exception) {
                    Log.e("AudioEngine", "Effects init failed: ${e.message}")
                }
                
                // Once effects are attached safely, move to Main Thread to prepare and start
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    player.setOnPreparedListener { mediaPlayer ->
                        val dur = mediaPlayer.duration.toLong()
                        _duration.value = dur
                        if (autoPlayOnPrepared) {
                            try {
                                mediaPlayer.start()
                                _isPlaying.value = true
                                _position.value = mediaPlayer.currentPosition.toLong()
                                startPositionPolling()
                                syncAtmospheres()
                                applyStoredPlaybackParams()
                                onPlaybackStateChanged?.invoke(true)
                                Log.i("AudioEngine", "Auto-play started, position: ${_position.value}")
                            } catch (e: Exception) {
                                Log.e("AudioEngine", "Auto-play failed: ${e.message}")
                            }
                        }
                        
                        fftJob = scope.launch(Dispatchers.IO) {
                            fftCancelled = false
                            initFft()
                        }
                        startAnimatingWaveform()
                        Log.i("AudioEngine", "Track loaded, duration: ${dur}ms")
                    }
                    player.setOnCompletionListener {
                        _isPlaying.value = false
                        positionJob?.cancel()
                        pauseAtmospheres()
                        onPlaybackStateChanged?.invoke(false)
                        Log.i("AudioEngine", "Track playback completed")
                    }
                    try {
                        player.prepareAsync()
                    } catch (e: Exception) {
                        Log.e("AudioEngine", "prepareAsync failed", e)
                    }
                }
            }
            storedTempo = pendingTempo
            storedPitch = pendingPitch
            return true
        } catch (e: Exception) {
            _error.value = "$errorPrefix: ${e.message}"
            abandonAudioFocus()
            Log.e("AudioEngine", "$errorPrefix: ${e.message}", e)
            return false
        }
    }

    fun isReady(): Boolean = mainPlayer != null

    fun clearError() {
        _error.value = null
    }

    fun play() {
        val player = mainPlayer
        if (player == null) {
            _error.value = "No track loaded"
            return
        }
        try {
            if (!player.isPlaying) {
                player.start()
                _isPlaying.value = true
                
                // Immediately set current position
                try {
                    _position.value = player.currentPosition.toLong()
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
        val player = mainPlayer
        if (player == null) return
        wasPlayingBeforeFocusLoss = false
        try {
            if (player.isPlaying) {
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
        val player = mainPlayer
        if (player == null) {
            _error.value = "No track loaded"
            return
        }
        try {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                positionJob?.cancel()
                pauseAtmospheres()
                onPlaybackStateChanged?.invoke(false)
            } else {
                player.start()
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
            mainPlayer?.seekTo(millis.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            _position.value = millis
        } catch (e: Exception) {
            Log.e("AudioEngine", "Seek failed: ${e.message}", e)
        }
    }

    fun setLooping(loop: Boolean) {
        _isLooping.value = loop
        mainPlayer?.isLooping = loop
    }

    fun toggleLoop() {
        setLooping(!_isLooping.value)
    }

    fun stop() {
        _isPlaying.value = false
        wasPlayingBeforeFocusLoss = false
        pauseAtmospheres()
        mainPlayer?.let { player ->
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
        positionJob?.cancel()
        scope.cancel()
        releaseMainPlayer()
        releaseAtmospherePlayers()
        releaseEffects()
    }
    // Stored playback params to apply after player starts
    private var pendingTempo: Float = 1.0f
    private var pendingPitch: Float = 0f
    private var storedTempo: Float = 1.0f
    private var storedPitch: Float = 0f

    private fun applyStoredPlaybackParams() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mainPlayer?.let { player ->
                try {
                    // Only apply if player is actually playing or at least started
                    val tempo = storedTempo
                    val semitones = storedPitch
                    val pitchFactor = if (semitones != 0f) {
                        Math.pow(2.0, (semitones / 12.0).toDouble()).toFloat()
                    } else 1f
                    val params = PlaybackParams()
                        .setSpeed(tempo.coerceIn(0.1f, 3.0f))
                        .setPitch(pitchFactor.coerceIn(0.1f, 3.0f))
                        .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
                    player.playbackParams = params
                } catch (e: Exception) {
                    Log.e("AudioEngine", "Playback error: ${e.message}", e)
                }
            }
        }
    }

    // --- Speed & Pitch (INDEPENDENT) ---

    fun setSpeedAndPitch(tempo: Float, semitones: Float) {
        pendingTempo = tempo
        pendingPitch = semitones
        storedTempo = tempo
        storedPitch = semitones
        // If player is already playing, apply immediately
        val player = mainPlayer
        if (player != null && player.isPlaying) {
            applyStoredPlaybackParams()
        }
    }

    // --- Audio Effects ---

    private fun initEffects(audioSessionId: Int) {
        try {
            reverb = PresetReverb(EFFECT_PRIORITY, audioSessionId).apply {
                enabled = true
                preset = PresetReverb.PRESET_SMALLROOM
            }

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

    fun setReverb(wet: Float) {
        reverb?.let { r ->
            try {
                r.enabled = wet > 0.01f
                if (wet > 0.01f) {
                    r.preset = when {
                        wet < 0.25f -> PresetReverb.PRESET_SMALLROOM
                        wet < 0.5f -> PresetReverb.PRESET_MEDIUMROOM
                        wet < 0.75f -> PresetReverb.PRESET_LARGEHALL
                        else -> PresetReverb.PRESET_PLATE
                    }
                }
            } catch (e: Exception) { Log.e("AudioEngine", "Reverb error: ${e.message}", e) }
        } ?: run {
            // Store for later application when effects are initialized
            pendingReverb = wet
        }
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
            try {
                val combined = (reverbWet + delayWet * 1.0f).coerceIn(0f, 1f)
                r.enabled = combined > 0.01f
                if (combined > 0.01f) {
                    r.preset = when {
                        combined < 0.06f -> PresetReverb.PRESET_NONE
                        combined < 0.15f -> PresetReverb.PRESET_SMALLROOM
                        combined < 0.30f -> PresetReverb.PRESET_MEDIUMROOM
                        combined < 0.55f -> PresetReverb.PRESET_LARGEHALL
                        else -> PresetReverb.PRESET_PLATE
                    }
                }
            } catch (e: Exception) { Log.e("AudioEngine", "Reverb error: ${e.message}", e) }
        }
    }

    // --- Fast FFT Visualization (lower resolution, much faster) ---

    private fun startAnimatingWaveform() {
        animatingWaveform = true
        // Show a gentle pulsing animation while FFT is computing
        scope.launch {
            var phase = 0f
            while (animatingWaveform && isActive) {
                // Produce a subtle animated pattern
                phase += 0.15f
                val animated = List(16) { i ->
                    val base = 0.05f + 0.15f * (kotlin.math.sin(phase + i * 0.5f) * 0.5f + 0.5f)
                    base.coerceIn(0f, 1f)
                }
                synchronized(framesLock) {
                    if (precomputedFrames.isEmpty()) {
                        _fftData.value = animated
                        _waveformData.value = WaveformSnapshot(animated, ++waveformSeq)
                    }
                }
                delay(80)
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
     * Fast FFT precomputation using larger hop and simpler FFT.
     * Processes only ~1/4 the frames for much faster computation.
     */
    private fun precomputeFFTFast(context: Context, uri: Uri): Boolean {
        return try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                decodeAndComputeFFTFast(extractor) { newFrames ->
                    synchronized(framesLock) {
                        precomputedFrames.addAll(newFrames)
                    }
                }
            } finally {
                extractor.release()
            }
            animatingWaveform = false
            android.util.Log.i("AudioEngine", "Fast FFT done: ${precomputedFrames.size} frames")
            true
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Fast FFT failed: ${e.message}")
            false
        } finally {
            animatingWaveform = false
        }
    }

    private fun precomputeFFTFast(filePath: String): Boolean {
        return try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(filePath)
                decodeAndComputeFFTFast(extractor) { newFrames ->
                    synchronized(framesLock) {
                        precomputedFrames.addAll(newFrames)
                    }
                }
            } finally {
                extractor.release()
            }
            animatingWaveform = false
            android.util.Log.i("AudioEngine", "Fast FFT done: ${precomputedFrames.size} frames")
            true
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Fast FFT failed: ${e.message}")
            false
        } finally {
            animatingWaveform = false
        }
    }

    private fun decodeAndComputeFFTFast(extractor: MediaExtractor, onBatch: (List<List<Float>>) -> Unit) {
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
            val batch = mutableListOf<List<Float>>()
            var sawInputEOS = false
            var sawOutputEOS = false
            var frameCount = 0

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(5000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
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
                    if (size > 0) {
                        val shorts = ShortArray(size / 2)
                        outputBuffer.rewind()
                        outputBuffer.asShortBuffer().get(shorts)
                        // Use larger window and hop for speed
                        val windowSize = 512
                        val hopSize = windowSize * 2  // Bigger hop = 4x fewer FFTs than before
                        var start = 0
                        while (start + windowSize <= shorts.size) {
                            val window = shorts.sliceArray(start until start + windowSize)
                            batch.add(computeFFTMagnitudesFast(window))
                            start += hopSize
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

    private fun computeFFTMagnitudesFast(samples: ShortArray): List<Float> {
        val n = samples.size
        // Simple RMS-based magnitude estimation for each band - much faster than full FFT
        val bands = 16
        val samplesPerBand = n / bands
        val result = MutableList(bands) { 0f }
        for (band in 0 until bands) {
            val start = band * samplesPerBand
            val end = minOf(start + samplesPerBand, n)
            if (start >= end) continue
            var sumSq = 0f
            for (k in start until end) {
                val v = samples[k].toFloat() / 32768f
                sumSq += v * v
            }
            val rms = kotlin.math.sqrt((sumSq / (end - start)).toDouble()).toFloat()
            result[band] = (rms * 3.0f).coerceIn(0f, 1f)
        }
        return result
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
            } catch (_: Exception) {}
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
        player.setVolume(vol, vol)
        // Start atmosphere player whenever volume > 0, regardless of main track playing state
        // The atmosphere is an independent layer
        if (vol > 0.01f && !player.isPlaying) {
            try {
                player.seekTo(0)
                player.start()
            } catch (_: Exception) {}
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
                } catch (_: Exception) {}
            } else if (vol <= 0.01f && player.isPlaying) {
                player.pause()
            }
        }
    }

    private fun pauseAtmospheres() {
        atmospherePlayers.values.forEach {
            try {
                if (it.isPlaying) it.pause()
            } catch (_: Exception) {}
        }
    }

    // --- Position Polling ---

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                mainPlayer?.let { player ->
                    try {
                        val pos = player.currentPosition.toLong()
                        if (pos >= 0) {
                            _position.value = pos
                        }
                        val dur = _duration.value
                        synchronized(framesLock) {
                            val frameCount = precomputedFrames.size
                            if (frameCount > 0 && dur > 0 && pos >= 0) {
                                val maxFrame = ((pos.toFloat() / dur.toFloat()) * frameCount).toInt()
                                    .coerceIn(0, frameCount - 1)
                                val frame = precomputedFrames[maxFrame]
                                _fftData.value = frame
                                _waveformData.value = WaveformSnapshot(frame, ++waveformSeq)
                            }
                        }
                    } catch (_: Exception) {}
                }
                delay(100) // Faster polling for smoother position updates
            }
        }
    }

    // --- Cleanup ---

    private fun releaseMainPlayer() {
        _isPlaying.value = false
        positionJob?.cancel()
        mainPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (_: Exception) {}
        }
        mainPlayer = null
        abandonAudioFocus()
    }

    /**
     * Ensure main audio session is established before initializing effects.
     */
    fun ensureAudioSessionReady(): Boolean {
        val player = mainPlayer ?: return false
        return try {
            player.audioSessionId > 0
        } catch (_: Exception) {
            false
        }
    }

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