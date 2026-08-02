package com.dhanuk.lofiga.export

import android.content.ContentValues
import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dhanuk.lofiga.model.PresetValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ExportService {

    private val activeExports = ConcurrentHashMap<UUID, AtomicBoolean>()

    private const val MAX_CACHE_ENTRIES = 4

    // LRU cache for atmosphere PCM data - bounded to prevent memory leaks
    private object AtmosphereCache {
        private val cache = object : java.util.LinkedHashMap<String, ShortArray>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShortArray>): Boolean =
                size > MAX_CACHE_ENTRIES
        }

        @Synchronized fun get(key: String): ShortArray? = cache[key]
        @Synchronized fun put(key: String, value: ShortArray) { cache[key] = value }
        @Synchronized fun clear() { cache.clear() }
    }

    // Chunk size for streaming processing — ~1 second of stereo 44.1kHz 16-bit audio
    private const val CHUNK_FRAMES = 44100
    private const val CHUNK_SHORTS = CHUNK_FRAMES * 2

    suspend fun exportTrack(
        context: Context,
        inputUri: Uri,
        inputPath: String?,
        fileName: String,
        preset: PresetValues,
        format: String = "m4a",
        bitrate: String = "192k",
        outputDir: String? = null,
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val exportId = UUID.randomUUID()
        val cancelFlag = AtomicBoolean(false)
        activeExports[exportId] = cancelFlag

        val cleanName = fileName.substringBeforeLast(".").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val outputDirPath = outputDir ?: if (musicDir != null) {
            File(musicDir, "Lofiga").absolutePath
        } else {
            File(context.filesDir, "Lofiga").absolutePath
        }
        File(outputDirPath).mkdirs()

        val outputFile = File(outputDirPath, "${cleanName}_lofi.$format")

        try {
            val sourceUri = if (inputPath != null && File(inputPath).exists()) {
                Uri.fromFile(File(inputPath))
            } else {
                inputUri
            }

            if (format == "wav") {
                exportAsWavStreaming(context, sourceUri, preset, outputFile, cancelFlag, onProgress)
            } else {
                exportWithMediaCodecStreaming(context, sourceUri, preset, outputFile, format, bitrate, cancelFlag, onProgress)
            }

            if (cancelFlag.get()) {
                outputFile.delete()
                return@withContext null
            }

            if (outputFile.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, outputFile.name)
                        put(MediaStore.Audio.Media.MIME_TYPE,
                            if (format == "wav") "audio/wav" else "audio/mp4")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Lofiga")
                        put(MediaStore.Audio.Media.IS_MUSIC, true)
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                    )
                    if (uri != null) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                outputFile.inputStream().use { it.copyTo(out) }
                            }
                        } catch (e: Exception) {
                            Log.e("ExportService", "Failed to publish export to MediaStore, removing entry", e)
                            runCatching { context.contentResolver.delete(uri, null, null) }
                        }
                    }
                } else {
                    val mime = if (format == "wav") "audio/wav" else "audio/mp4"
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputFile.absolutePath),
                        arrayOf(mime)
                    ) { _, _ -> }
                }
            }

            return@withContext outputFile.absolutePath
        } catch (e: OutOfMemoryError) {
            Log.e("ExportService", "Export ran out of memory", e)
            if (outputFile.exists()) outputFile.delete()
            throw RuntimeException(
                "Export ran out of memory. Try a shorter track or fewer atmosphere layers.",
                e
            )
        } catch (e: Exception) {
            Log.e("ExportService", "Export failed: ${e.message}", e)
            if (outputFile.exists()) outputFile.delete()
            throw e
        } finally {
            activeExports.remove(exportId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STREAMING EXPORT — processes audio in ~1s chunks to keep memory flat
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Holds state that persists across chunks during streaming processing.
     * This is where reverb tails, delay lines, and filter states live.
     */
    private class StreamingEffectState(preset: PresetValues, sampleRate: Int, channels: Int) {
        val delayWet = preset.delay
        val reverbWet = preset.reverb
        val bassStrength = preset.bass
        val trebleCut = preset.trebleCut
        val tempo = preset.tempo
        val pitch = preset.pitch
        val rainVolume = preset.rainVolume
        val vinylVolume = preset.vinylVolume
        val windVolume = preset.windVolume
        val tapeVolume = preset.tapeVolume

        // Delay line buffer (circular)
        val delaySamples = if (delayWet > 0.01f) ((100 + delayWet * 400).toInt() * sampleRate / 1000) * channels else 0
        val delayBuf = if (delaySamples > 0) ShortArray(delaySamples) else null
        var delayPos = 0

        // Reverb comb filter buffer (circular)
        val revDelaySamples = if (reverbWet > 0.01f) (((30 + reverbWet * 100).toInt() * sampleRate / 1000).coerceAtLeast(1)) else 0
        val revBufSize = revDelaySamples * channels
        val revBuf = if (revBufSize > 0) ShortArray(revBufSize) else null
        var revPos = 0

        // Bass lowpass state
        val bassAlpha = 0.94f
        var bassLpL = 0f
        var bassLpR = 0f

        // Treble lowpass state
        val trebleAlpha = if (trebleCut > 0.01f) {
            val maxFreq = sampleRate / 2f
            val cf = maxFreq * (1f - trebleCut * 0.9f) + 1f
            (cf / (cf + (sampleRate / Math.PI))).toFloat().coerceIn(0.01f, 0.99f)
        } else 0f
        var trebleLpL = 0f
        var trebleLpR = 0f

        // Atmosphere layers (loaded once, looped)
        var atmosphereLayers: List<Pair<ShortArray, Int>> = emptyList()

        fun loadAtmospheres(context: Context) {
            val layers = mutableListOf<Pair<ShortArray, Int>>()
            listOf("rain" to rainVolume, "vinyl" to vinylVolume, "wind" to windVolume, "tape" to tapeVolume)
                .filter { it.second > 0.01f }
                .forEach { (key, vol) ->
                    readAtmospherePcm(context, key, 44100)?.let { pcm ->
                        layers.add(pcm to (vol * 0.8f * 32768f).toInt())
                    }
                }
            atmosphereLayers = layers
        }
    }

    /**
     * Process a single chunk of PCM through all active effects.
     */
    private fun processChunk(
        chunk: ShortArray,
        state: StreamingEffectState,
        sampleRate: Int,
        channels: Int
    ): ShortArray {
        var result = chunk

        if (state.tempo != 1.0f || state.pitch != 0f) {
            result = applySpeedPitchChunk(result, state.tempo, state.pitch, channels)
        }

        if (state.delayWet > 0.01f || state.reverbWet > 0.01f ||
            state.bassStrength > 0.01f || state.trebleCut > 0.01f) {
            result = applyCombinedEffectsChunk(result, state, channels)
        }

        if (state.atmosphereLayers.isNotEmpty()) {
            result = mixAtmosphereChunk(result, state.atmosphereLayers, channels)
        }

        return result
    }

    private fun applySpeedPitchChunk(pcm: ShortArray, tempo: Float, semitones: Float, channels: Int): ShortArray {
        if (tempo == 1.0f && semitones == 0f) return pcm
        var result = pcm

        val safeTempo = tempo.coerceIn(0.25f, 2.5f)
        if (safeTempo != 1.0f) {
            result = timeStretchChunk(result, safeTempo, channels)
        }

        if (semitones != 0f) {
            val pitchFactor = Math.pow(2.0, (semitones / 12.0)).toFloat().coerceIn(0.25f, 4.0f)
            result = pitchShiftChunk(result, pitchFactor, channels)
        }
        return result
    }

    private fun timeStretchChunk(pcm: ShortArray, tempo: Float, channels: Int): ShortArray {
        if (tempo == 1.0f) return pcm
        val inFrames = pcm.size / channels
        if (inFrames < 2048) {
            return resampleFramesChunk(pcm, (inFrames / tempo).toInt().coerceAtLeast(1), channels)
        }
        val outFrames = (inFrames / tempo).toInt().coerceAtLeast(1)
        val grain = 2048
        val hopOut = grain / 2
        val hopIn = hopOut * tempo
        val out = FloatArray(outFrames * channels)
        val norm = FloatArray(outFrames)

        var outPos = 0
        var inPos = 0f
        val window = FloatArray(grain) { i ->
            (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (grain - 1))).toFloat()
        }

        while (outPos + grain <= outFrames && inPos + grain <= inFrames) {
            val baseIn = inPos.toInt()
            for (g in 0 until grain) {
                val srcIdx = (baseIn + g) * channels
                val dstFrame = outPos + g
                val w = window[g]
                for (ch in 0 until channels) {
                    out[dstFrame * channels + ch] += pcm[srcIdx + ch].toFloat() * w
                }
                norm[dstFrame] += w
            }
            outPos += hopOut
            inPos += hopIn
        }

        val result = ShortArray(outFrames * channels)
        for (f in 0 until outFrames) {
            val n = if (norm[f] > 1e-4f) norm[f] else 1f
            for (ch in 0 until channels) {
                val v = (out[f * channels + ch] / n).toInt()
                result[f * channels + ch] = v.coerceIn(-32768, 32767).toShort()
            }
        }
        return result
    }

    private fun pitchShiftChunk(pcm: ShortArray, pitchFactor: Float, channels: Int): ShortArray {
        if (pitchFactor == 1.0f) return pcm
        val inFrames = pcm.size / channels
        val resampledFrames = (inFrames / pitchFactor).toInt().coerceAtLeast(1)
        var result = resampleFramesChunk(pcm, resampledFrames, channels)
        val durationRatio = resampledFrames.toFloat() / inFrames.toFloat()
        if (durationRatio != 1.0f) {
            result = timeStretchChunk(result, durationRatio, channels)
        }
        return result
    }

    private fun resampleFramesChunk(pcm: ShortArray, targetFrames: Int, channels: Int): ShortArray {
        val inFrames = pcm.size / channels
        if (targetFrames == inFrames || targetFrames <= 0) return pcm
        val output = ShortArray(targetFrames * channels)
        val step = inFrames.toFloat() / targetFrames.toFloat()
        for (frame in 0 until targetFrames) {
            val srcPos = frame * step
            val srcFrame = srcPos.toInt().coerceIn(0, inFrames - 2)
            val frac = srcPos - srcFrame
            val baseIn = srcFrame * channels
            val baseOut = frame * channels
            for (ch in 0 until channels) {
                val cur = pcm[baseIn + ch].toInt()
                val next = pcm[baseIn + channels + ch].toInt()
                val sample = (cur * (1f - frac) + next * frac).toInt()
                output[baseOut + ch] = sample.coerceIn(-32768, 32767).toShort()
            }
        }
        return output
    }

    private fun applyCombinedEffectsChunk(
        pcm: ShortArray,
        state: StreamingEffectState,
        channels: Int
    ): ShortArray {
        val output = ShortArray(pcm.size)

        for (i in pcm.indices) {
            var sample = pcm[i].toFloat() / 32768f
            val ch = i and 1

            // Delay
            if (state.delayWet > 0.01f && state.delayBuf != null) {
                val delayed = state.delayBuf[state.delayPos].toFloat() / 32768f
                state.delayBuf[state.delayPos] = pcm[i]
                state.delayPos = (state.delayPos + 1) % state.delaySamples
                sample = sample * (1 - state.delayWet * 0.3f) + delayed * state.delayWet * 0.3f
            }

            // Reverb
            if (state.reverbWet > 0.01f && state.revBuf != null) {
                val wetSample = state.revBuf[state.revPos].toFloat() / 32768f
                val drySample = sample
                state.revBuf[state.revPos] = ((drySample * (1 - 0.4f * state.reverbWet) + wetSample * 0.3f) * 32768f)
                    .toInt().coerceIn(-32768, 32767).toShort()
                state.revPos = (state.revPos + 1) % state.revBufSize
                sample = drySample * (1 - state.reverbWet * 0.5f) + wetSample * state.reverbWet * 0.5f
            }

            // Bass
            if (state.bassStrength > 0.01f) {
                if (ch == 0) {
                    state.bassLpL = state.bassAlpha * state.bassLpL + (1 - state.bassAlpha) * sample
                    sample += state.bassStrength * 2.5f * state.bassLpL
                } else {
                    state.bassLpR = state.bassAlpha * state.bassLpR + (1 - state.bassAlpha) * sample
                    sample += state.bassStrength * 2.5f * state.bassLpR
                }
            }

            // Treble cut
            if (state.trebleCut > 0.01f) {
                if (ch == 0) {
                    state.trebleLpL = state.trebleAlpha * state.trebleLpL + (1 - state.trebleAlpha) * sample
                    sample = state.trebleLpL
                } else {
                    state.trebleLpR = state.trebleAlpha * state.trebleLpR + (1 - state.trebleAlpha) * sample
                    sample = state.trebleLpR
                }
            }

            output[i] = (sample * 32768f).toInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    private fun mixAtmosphereChunk(
        pcm: ShortArray,
        layers: List<Pair<ShortArray, Int>>,
        channels: Int
    ): ShortArray {
        val result = ShortArray(pcm.size)
        for (i in result.indices) {
            var sample = pcm[i].toInt()
            for ((layerPcm, scaledVol) in layers) {
                val loopSize = layerPcm.size
                if (loopSize > 0) {
                    sample += (layerPcm[i % loopSize].toInt() * scaledVol) shr 15
                }
            }
            result[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STREAMING DECODE + PROCESS + ENCODE PIPELINE
    // ═══════════════════════════════════════════════════════════════════════════

    private class ChunkedPcmSource(
        context: Context,
        sourceUri: Uri,
        private val cancelFlag: AtomicBoolean,
        private val onProgress: ((Float) -> Unit)?
    ) {
        private val extractor = MediaExtractor()
        private var decoder: MediaCodec? = null
        private var inputSampleRate = 44100
        private var inputChannels = 2
        private var inputDuration = 0L
        private var audioTrackIndex = -1
        private val appContext = context

        fun open(): Boolean {
            extractor.setDataSource(appContext, sourceUri, null)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) return false

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            inputDuration = if (inputFormat.containsKey(MediaFormat.KEY_DURATION))
                inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
            return true
        }

        fun close() {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }

        fun nextChunk(): ShortArray? {
            if (cancelFlag.get()) return null

            val decoder = this.decoder ?: return null
            val accumulator = ShortArray(CHUNK_SHORTS)
            var accumulated = 0
            var sawInputEOS = false
            var sawOutputEOS = false

            while (accumulated < CHUNK_SHORTS && !sawOutputEOS) {
                if (cancelFlag.get()) return null

                if (!sawInputEOS) {
                    val inIdx = decoder.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf == null) {
                            try { decoder.queueInputBuffer(inIdx, 0, 0, 0, 0) } catch (_: Exception) {}
                            continue
                        }
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val sampleTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                            if (inputDuration > 0) {
                                val progress = (sampleTimeUs.toFloat() / inputDuration.toFloat())
                                    .coerceIn(0f, 1f) * 0.5f
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                }

                val decInfo = MediaCodec.BufferInfo()
                var decIdx = decoder.dequeueOutputBuffer(decInfo, 10000)
                while (decIdx >= 0) {
                    if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                    if (decInfo.size > 0) {
                        val outBuf = decoder.getOutputBuffer(decIdx)
                        if (outBuf != null) {
                            outBuf.position(decInfo.offset)
                            outBuf.limit(decInfo.offset + decInfo.size)
                            val shortCount = decInfo.size / 2
                            val temp = ShortArray(shortCount)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(temp, 0, shortCount)

                            val toCopy = minOf(shortCount, CHUNK_SHORTS - accumulated)
                            System.arraycopy(temp, 0, accumulator, accumulated, toCopy)
                            accumulated += toCopy
                        }
                    }
                    decoder.releaseOutputBuffer(decIdx, false)
                    decIdx = decoder.dequeueOutputBuffer(decInfo, 0)
                }
            }

            if (accumulated == 0) return null

            var chunk = if (accumulated < CHUNK_SHORTS) accumulator.copyOf(accumulated) else accumulator

            if (inputChannels == 1) {
                chunk = monoToStereoChunk(chunk)
            }

            if (inputSampleRate != 44100) {
                chunk = resamplePcmChunk(chunk, inputSampleRate, 44100, 2)
            }

            return chunk
        }
    }

    private fun exportWithMediaCodecStreaming(
        context: Context,
        sourceUri: Uri,
        preset: PresetValues,
        outputFile: File,
        format: String,
        bitrate: String,
        cancelFlag: AtomicBoolean,
        onProgress: ((Float) -> Unit)?
    ) {
        val source = ChunkedPcmSource(context, sourceUri, cancelFlag, onProgress)
        if (!source.open()) {
            onProgress?.invoke(1.0f)
            return
        }

        val effectState = StreamingEffectState(preset, 44100, 2)
        effectState.loadAtmospheres(context)

        val mime = "audio/mp4a-latm"
        val bitrateInt = when (bitrate) {
            "128k" -> 128000; "192k" -> 192000; "256k" -> 256000; "320k" -> 320000; else -> 192000
        }
        val outputFormat = MediaFormat.createAudioFormat(mime, 44100, 2).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateInt)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        var sawEncoderEOS = false
        var totalFramesEncoded = 0L

        try {
            while (!sawEncoderEOS && !cancelFlag.get()) {
                val inputIdx = encoder.dequeueInputBuffer(10000)
                if (inputIdx >= 0) {
                    val chunk = source.nextChunk()
                    if (chunk == null || chunk.isEmpty()) {
                        encoder.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val processed = processChunk(chunk, effectState, 44100, 2)
                        val bytes = ByteArray(processed.size * 2)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(processed)
                        val inBuf = encoder.getInputBuffer(inputIdx)
                        if (inBuf != null && inBuf.capacity() >= bytes.size) {
                            inBuf.clear()
                            inBuf.put(bytes)
                            val pts = (totalFramesEncoded * 1000000L) / 44100
                            encoder.queueInputBuffer(inputIdx, 0, bytes.size, pts, 0)
                            totalFramesEncoded += processed.size / 2
                        } else {
                            encoder.queueInputBuffer(inputIdx, 0, 0, 0, 0)
                        }
                    }
                }

                val bufInfo = MediaCodec.BufferInfo()
                var outIdx = encoder.dequeueOutputBuffer(bufInfo, 10000)
                while (outIdx >= 0) {
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufInfo.size = 0
                    }
                    if (bufInfo.size > 0) {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if (outBuf != null) {
                            val safeSize = minOf(bufInfo.size, outBuf.capacity() - bufInfo.offset)
                            if (safeSize > 0) {
                                outBuf.position(bufInfo.offset)
                                outBuf.limit(bufInfo.offset + safeSize)
                                bufInfo.size = safeSize
                                try {
                                    muxer.writeSampleData(trackIndex, outBuf, bufInfo)
                                } catch (e: Exception) {
                                    Log.e("ExportService", "writeSampleData failed: ${e.message}")
                                    throw e
                                }
                            }
                        }
                    }
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawEncoderEOS = true
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    outIdx = encoder.dequeueOutputBuffer(bufInfo, 0)
                }
            }

            // Drain remaining output
            while (!cancelFlag.get()) {
                val bufInfo = MediaCodec.BufferInfo()
                val outIdx = encoder.dequeueOutputBuffer(bufInfo, 5000)
                if (outIdx < 0) break
                if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufInfo.size = 0
                }
                if (bufInfo.size > 0 && muxerStarted) {
                    val outBuf = encoder.getOutputBuffer(outIdx)
                    if (outBuf != null) {
                        val safeSize = minOf(bufInfo.size, outBuf.capacity() - bufInfo.offset)
                        if (safeSize > 0) {
                            outBuf.position(bufInfo.offset)
                            outBuf.limit(bufInfo.offset + safeSize)
                            bufInfo.size = safeSize
                            try {
                                muxer.writeSampleData(trackIndex, outBuf, bufInfo)
                            } catch (e: Exception) {
                                Log.e("ExportService", "writeSampleData (drain) failed: ${e.message}")
                                throw e
                            }
                        }
                    }
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } finally {
            source.close()
            if (muxerStarted) try { muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            try { encoder.stop(); encoder.release() } catch (_: Exception) {}
        }
        onProgress?.invoke(1.0f)
    }

    private fun exportAsWavStreaming(
        context: Context,
        sourceUri: Uri,
        preset: PresetValues,
        outputFile: File,
        cancelFlag: AtomicBoolean,
        onProgress: ((Float) -> Unit)?
    ) {
        val source = ChunkedPcmSource(context, sourceUri, cancelFlag, onProgress)
        if (!source.open()) {
            onProgress?.invoke(1.0f)
            return
        }

        val effectState = StreamingEffectState(preset, 44100, 2)
        effectState.loadAtmospheres(context)

        try {
            val headerPlaceholder = ByteArray(44)
            val raf = java.io.RandomAccessFile(outputFile, "rw")
            raf.write(headerPlaceholder)

            var totalDataSize = 0L
            val chunkBuffer = ByteBuffer.allocate(CHUNK_SHORTS * 2).order(ByteOrder.LITTLE_ENDIAN)

            while (!cancelFlag.get()) {
                val chunk = source.nextChunk() ?: break
                val processed = processChunk(chunk, effectState, 44100, 2)

                chunkBuffer.clear()
                chunkBuffer.asShortBuffer().put(processed)
                raf.write(chunkBuffer.array(), 0, processed.size * 2)
                totalDataSize += processed.size * 2
            }

            raf.seek(0)
            writeWavHeader(raf, 44100, 2, totalDataSize)
            raf.close()

            onProgress?.invoke(1.0f)
        } finally {
            source.close()
        }
    }

    private fun writeWavHeader(raf: java.io.RandomAccessFile, sampleRate: Int, channels: Int, dataSize: Long) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val fileSize = 36 + dataSize

        raf.write("RIFF".toByteArray())
        raf.write(intToByteArrayLE(fileSize.toInt()))
        raf.write("WAVE".toByteArray())
        raf.write("fmt ".toByteArray())
        raf.write(intToByteArrayLE(16))
        raf.write(shortToByteArrayLE(1))
        raf.write(shortToByteArrayLE(channels.toShort()))
        raf.write(intToByteArrayLE(sampleRate))
        raf.write(intToByteArrayLE(byteRate))
        raf.write(shortToByteArrayLE(blockAlign.toShort()))
        raf.write(shortToByteArrayLE(bitsPerSample.toShort()))
        raf.write("data".toByteArray())
        raf.write(intToByteArrayLE(dataSize.toInt()))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun isPresetActive(preset: PresetValues): Boolean {
        return preset.tempo != 1.0f || preset.pitch != 0f || preset.reverb > 0.01f ||
                preset.delay > 0.01f || preset.bass > 0.01f || preset.trebleCut > 0.01f ||
                preset.rainVolume > 0.01f || preset.vinylVolume > 0.01f ||
                preset.windVolume > 0.01f || preset.tapeVolume > 0.01f
    }

    private fun monoToStereoChunk(mono: ShortArray): ShortArray {
        val stereo = ShortArray(mono.size * 2)
        for (i in mono.indices) {
            stereo[i * 2] = mono[i]
            stereo[i * 2 + 1] = mono[i]
        }
        return stereo
    }

    private fun resamplePcmChunk(pcm: ShortArray, fromRate: Int, toRate: Int, channels: Int): ShortArray {
        if (fromRate == toRate) return pcm
        val ratio = toRate.toFloat() / fromRate
        val inFrames = pcm.size / channels
        val outFrames = (inFrames * ratio).toInt()
        val output = ShortArray(outFrames * channels)
        for (frame in 0 until outFrames) {
            val srcPos = frame / ratio
            val srcFrame = srcPos.toInt()
            val frac = srcPos - srcFrame
            val baseIn = (srcFrame * channels).coerceIn(0, pcm.size - channels)
            val baseOut = frame * channels
            for (ch in 0 until channels) {
                val cur = pcm[baseIn + ch]
                val nextIdx = minOf(baseIn + channels + ch, pcm.size - 1)
                val next = if (nextIdx < pcm.size) pcm[nextIdx] else cur
                val sample = (cur * (1 - frac) + next * frac).toInt()
                output[baseOut + ch] = sample.coerceIn(-32768, 32767).toShort()
            }
        }
        return output
    }

    private fun readAtmospherePcm(context: Context, key: String, targetRate: Int): ShortArray? {
        val assetPath = getAtmosphereAssetPath(key) ?: return null

        val cacheKey = "${key}_${targetRate}"
        val cached = AtmosphereCache.get(cacheKey)
        if (cached != null) return cached

        return try {
            val input = context.assets.open(assetPath)
            val allBytes = input.readBytes()
            input.close()

            var offset = 12
            var channels = 2
            var fileRate = 44100
            var bitsPerSample = 16
            var dataSize = 0
            var dataOffset = 0

            while (offset + 8 <= allBytes.size) {
                val chunkId = String(allBytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = ((allBytes[offset + 7].toInt() and 0xFF) shl 24) or
                        ((allBytes[offset + 6].toInt() and 0xFF) shl 16) or
                        ((allBytes[offset + 5].toInt() and 0xFF) shl 8) or
                        (allBytes[offset + 4].toInt() and 0xFF)

                when (chunkId) {
                    "fmt " -> {
                        if (offset + 24 <= allBytes.size) {
                            channels = ((allBytes[offset + 11].toInt() and 0xFF) shl 8) or (allBytes[offset + 10].toInt() and 0xFF)
                            fileRate = ((allBytes[offset + 15].toInt() and 0xFF) shl 24) or
                                    ((allBytes[offset + 14].toInt() and 0xFF) shl 16) or
                                    ((allBytes[offset + 13].toInt() and 0xFF) shl 8) or
                                    (allBytes[offset + 12].toInt() and 0xFF)
                            bitsPerSample = ((allBytes[offset + 23].toInt() and 0xFF) shl 8) or (allBytes[offset + 22].toInt() and 0xFF)
                        }
                    }
                    "data" -> {
                        dataSize = chunkSize
                        dataOffset = offset + 8
                        break
                    }
                }
                offset += 8 + chunkSize
                if (chunkSize % 2 != 0) offset++
            }

            if (dataSize <= 0) return null

            val dataEnd = minOf(dataOffset + dataSize, allBytes.size)
            val pcmBytes = allBytes.copyOfRange(dataOffset, dataEnd)

            val sampleBytes = if (bitsPerSample == 16) 2 else 1
            val shortCount = pcmBytes.size / sampleBytes
            var pcm = ShortArray(shortCount)
            if (bitsPerSample == 16) {
                ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
            } else {
                for (i in pcmBytes.indices) pcm[i] = ((pcmBytes[i].toInt() - 128) shl 8).toShort()
            }

            if (channels == 1) pcm = monoToStereoChunk(pcm)
            if (fileRate != targetRate) pcm = resamplePcmChunk(pcm, fileRate, targetRate, 2)

            AtmosphereCache.put(cacheKey, pcm)

            pcm
        } catch (e: Exception) {
            Log.e("ExportService", "Failed to read atmosphere PCM for '$key': ${e.message}", e)
            null
        }
    }

    private fun getAtmosphereAssetPath(key: String): String? = when (key) {
        "rain" -> "atmosphere/rain_loop.wav"
        "vinyl" -> "atmosphere/vinyl_crackle.wav"
        "wind" -> "atmosphere/wind_blow.wav"
        "tape" -> "atmosphere/tape_hiss.wav"
        else -> null
    }

    private fun intToByteArrayLE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArrayLE(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }

    fun cancelExport(exportId: UUID? = null) {
        if (exportId != null) {
            activeExports[exportId]?.set(true)
        } else {
            activeExports.values.forEach { it.set(true) }
        }
    }
}
