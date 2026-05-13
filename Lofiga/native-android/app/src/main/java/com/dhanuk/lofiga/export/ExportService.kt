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
import java.util.concurrent.atomic.AtomicBoolean

object ExportService {

    private val isCancelled = AtomicBoolean(false)

    private val SUPPORTED_FORMATS = listOf("m4a", "wav")
    private val SUPPORTED_BITRATES = listOf("128k", "192k", "256k", "320k")
    private const val MAX_PCM_SAMPLES = 20_000_000

    // Cache for atmosphere PCM data to avoid re-reading WAV files on every export
    private val atmospherePcmCache = mutableMapOf<String, ShortArray?>()

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
        isCancelled.set(false)

        val cleanName = fileName.substringBeforeLast(".").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        // Use app-specific external directory to avoid permission issues on Android 11+
        val outputDirPath = outputDir
            ?: File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Lofiga").absolutePath
        File(outputDirPath).mkdirs()

        val outputFile = File(outputDirPath, "${cleanName}_lofi.$format")

        try {
            val sourceUri = if (inputPath != null && File(inputPath).exists()) {
                Uri.fromFile(File(inputPath))
            } else {
                inputUri
            }

            if (format == "wav") {
                exportAsWav(context, sourceUri, preset, outputFile, onProgress)
            } else {
                exportWithMediaCodec(context, sourceUri, preset, outputFile, format, bitrate, onProgress)
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
                    context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                }
            }

            if (isCancelled.get()) {
                outputFile.delete()
                return@withContext null
            }

            return@withContext outputFile.absolutePath
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            throw e
        }
    }

    private fun exportWithMediaCodec(
        context: Context,
        sourceUri: Uri,
        preset: PresetValues,
        outputFile: File,
        format: String,
        bitrate: String,
        onProgress: ((Float) -> Unit)?
    ) {
        val pcmShorts = collectAndProcessPcm(context, sourceUri, preset, onProgress)
        if (pcmShorts == null || pcmShorts.isEmpty() || isCancelled.get()) {
            if (!isCancelled.get()) onProgress?.invoke(1.0f)
            return
        }
        encodePcmToAac(outputFile, pcmShorts, 44100, 2, bitrate, onProgress)
    }

    private fun encodePcmToAac(
        outputFile: File,
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        bitrate: String,
        onProgress: ((Float) -> Unit)?
    ) {
        val mime = "audio/mp4a-latm"
        val bitrateInt = when (bitrate) {
            "128k" -> 128000; "192k" -> 192000; "256k" -> 256000; "320k" -> 320000; else -> 192000
        }
        val outputFormat = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateInt)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        var sawEOS = false

        val totalFrames = pcm.size / channels
        val framesPerChunk = 1024
        var frameOffset = 0

        while (!sawEOS && !isCancelled.get()) {
            val inputIdx = encoder.dequeueInputBuffer(10000)
            if (inputIdx >= 0) {
                val inBuf = encoder.getInputBuffer(inputIdx)!!
                inBuf.clear()
                val remaining = totalFrames - frameOffset
                val frames = minOf(framesPerChunk, remaining)
                if (frames <= 0) {
                    encoder.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                val sampleCount = frames * channels
                val bytes = ByteArray(sampleCount * 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().apply {
                    put(pcm, frameOffset * channels, sampleCount)
                }
                inBuf.put(bytes)
                val pts = (frameOffset * 1000000L) / sampleRate
                encoder.queueInputBuffer(inputIdx, 0, bytes.size, pts, 0)
                frameOffset += frames
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
                    val outBuf = encoder.getOutputBuffer(outIdx)!!
                    outBuf.position(bufInfo.offset)
                    outBuf.limit(bufInfo.offset + bufInfo.size)
                    muxer.writeSampleData(trackIndex, outBuf, bufInfo)
                }
                if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawEOS = true
                }
                encoder.releaseOutputBuffer(outIdx, false)
                outIdx = encoder.dequeueOutputBuffer(bufInfo, 0)
            }

            if (totalFrames > 0) {
                onProgress?.invoke(0.5f + (frameOffset.toFloat() / totalFrames) * 0.45f)
            }
        }

        if (muxerStarted) try { muxer.stop() } catch (_: Exception) {}
        try { muxer.release() } catch (_: Exception) {}
        try { encoder.stop(); encoder.release() } catch (_: Exception) {}
        onProgress?.invoke(1.0f)
    }

    private fun exportAsWav(
        context: Context,
        sourceUri: Uri,
        preset: PresetValues,
        outputFile: File,
        onProgress: ((Float) -> Unit)?
    ) {
        val pcmShorts = collectAndProcessPcm(context, sourceUri, preset, onProgress)
        if (pcmShorts != null && pcmShorts.isNotEmpty() && !isCancelled.get()) {
            writeWavFile(outputFile, pcmShorts, 44100, 2)
            onProgress?.invoke(1.0f)
        } else if (!isCancelled.get()) {
            onProgress?.invoke(1.0f)
        }
    }

    /**
     * Memory-efficient growable buffer for PCM shorts.
     * Avoids ByteArrayOutputStream + toByteArray() double-copy.
     */
    private class ShortArrayBuffer(initialCapacity: Int = 65536) {
        var data = ShortArray(initialCapacity)
            private set
        var size = 0
            private set

        fun addAll(shorts: ShortArray, count: Int) {
            val needed = size + count
            if (needed > data.size) {
                var newSize = data.size
                while (newSize < needed) newSize = (newSize * 3) / 2
                data = data.copyOf(newSize)
            }
            System.arraycopy(shorts, 0, data, size, count)
            size += count
        }

        fun toShortArray(): ShortArray = data.copyOf(size)
        fun isEmpty(): Boolean = size == 0
        val isFull: Boolean get() = size >= MAX_PCM_SAMPLES
    }

    private fun collectAndProcessPcm(
        context: Context,
        sourceUri: Uri,
        preset: PresetValues,
        onProgress: ((Float) -> Unit)?
    ): ShortArray? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            extractor.setDataSource(context, sourceUri, null)
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) return null

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val inputDuration = if (inputFormat.containsKey(MediaFormat.KEY_DURATION))
                inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Direct short accumulation — no ByteArrayOutputStream intermediate!
            val accumulator = ShortArrayBuffer()
            var sawInputEOS = false
            var sawOutputEOS = false
            var totalInputBytes = 0L
            var wasTruncated = false

            while (!sawOutputEOS && !accumulator.isFull) {
                if (isCancelled.get()) { decoder.stop(); decoder.release(); extractor.release(); return null }

                if (!sawInputEOS) {
                    val inIdx = decoder.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            totalInputBytes += sampleSize
                            if (inputDuration > 0) {
                                onProgress?.invoke((totalInputBytes.toFloat() / inputDuration).coerceIn(0f, 0.3f))
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
                        val outBuf = decoder.getOutputBuffer(decIdx)!!
                        outBuf.position(decInfo.offset)
                        outBuf.limit(decInfo.offset + decInfo.size)

                        // Read shorts directly from the decoder output buffer
                        val shortCount = decInfo.size / 2
                        val tempShorts = ShortArray(shortCount)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(tempShorts, 0, shortCount)

                        if (accumulator.size + shortCount > MAX_PCM_SAMPLES) {
                            // Partial copy to stay within limit
                            val allowed = MAX_PCM_SAMPLES - accumulator.size
                            if (allowed > 0) {
                                accumulator.addAll(tempShorts, allowed)
                            }
                            wasTruncated = true
                            sawOutputEOS = true // Stop decoding once we hit the limit
                        } else {
                            accumulator.addAll(tempShorts, shortCount)
                        }
                    }
                    decoder.releaseOutputBuffer(decIdx, false)
                    decIdx = decoder.dequeueOutputBuffer(decInfo, 0)
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            var pcmShorts = accumulator.toShortArray()
            if (pcmShorts.isEmpty()) return pcmShorts

            if (wasTruncated) {
                android.util.Log.w("ExportService", "Track truncated to $MAX_PCM_SAMPLES samples (~${MAX_PCM_SAMPLES / 44100 / 2}s of audio)")
            }

            if (inputChannels == 1) {
                pcmShorts = monoToStereo(pcmShorts)
            }

            if (inputSampleRate != 44100) {
                onProgress?.invoke(0.4f)
                pcmShorts = resamplePcm(pcmShorts, inputSampleRate, 44100, 2)
            }

            onProgress?.invoke(0.5f)
            if (isPresetActive(preset)) {
                val processed = applyPresetEffects(pcmShorts, 44100, 2, preset)
                onProgress?.invoke(0.8f)
                val withAtmo = mixAtmosphereLayers(context, processed, 44100, 2, preset)
                onProgress?.invoke(0.95f)
                withAtmo
            } else {
                pcmShorts
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun writeWavFile(file: File, pcmShorts: ShortArray, sampleRate: Int, channels: Int) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmShorts.size * 2
        val fileSize = 36 + dataSize

        file.outputStream().use { out ->
            out.write("RIFF".toByteArray())
            out.write(intToByteArrayLE(fileSize))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToByteArrayLE(16))
            out.write(shortToByteArrayLE(1))
            out.write(shortToByteArrayLE(channels.toShort()))
            out.write(intToByteArrayLE(sampleRate))
            out.write(intToByteArrayLE(byteRate))
            out.write(shortToByteArrayLE(blockAlign.toShort()))
            out.write(shortToByteArrayLE(bitsPerSample.toShort()))
            out.write("data".toByteArray())
            out.write(intToByteArrayLE(dataSize))
            val buffer = ByteBuffer.allocate(pcmShorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            buffer.asShortBuffer().put(pcmShorts)
            out.write(buffer.array())
        }
    }

    private fun isPresetActive(preset: PresetValues): Boolean {
        return preset.tempo != 1.0f || preset.pitch != 0f || preset.reverb > 0.01f ||
                preset.delay > 0.01f || preset.bass > 0.01f || preset.trebleCut > 0.01f ||
                preset.rainVolume > 0.01f || preset.vinylVolume > 0.01f ||
                preset.windVolume > 0.01f || preset.tapeVolume > 0.01f
    }

    private fun monoToStereo(mono: ShortArray): ShortArray {
        val stereo = ShortArray(mono.size * 2)
        for (i in mono.indices) {
            stereo[i * 2] = mono[i]
            stereo[i * 2 + 1] = mono[i]
        }
        return stereo
    }

    private fun resamplePcm(pcm: ShortArray, fromRate: Int, toRate: Int, channels: Int): ShortArray {
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

    /**
     * Apply all preset effects with optimized single-pass processing where possible.
     * Speed/pitch must be separate as it changes array length, but all other effects
     * (delay, reverb, bass, treble) are combined into one loop to reduce allocations.
     */
    private fun applyPresetEffects(pcm: ShortArray, sampleRate: Int, channels: Int, preset: PresetValues): ShortArray {
        var result = pcm
        // Speed/pitch changes array length - must be separate
        if (preset.tempo != 1.0f || preset.pitch != 0f) {
            result = applySpeedPitch(result, preset.tempo, preset.pitch, channels)
        }
        // Apply all other effects in a single pass to minimize allocations
        if (preset.delay > 0.01f || preset.reverb > 0.01f || preset.bass > 0.01f || preset.trebleCut > 0.01f) {
            result = applyCombinedEffects(result, preset.delay, preset.reverb, preset.bass, preset.trebleCut, sampleRate, channels)
        }
        return result
    }

    /**
     * Combined single-pass application of delay, reverb, bass, and treble cut.
     * Significantly reduces memory allocations and improves cache performance
     * compared to sequential array allocations.
     */
    private fun applyCombinedEffects(
        pcm: ShortArray,
        delayWet: Float,
        reverbWet: Float,
        bassStrength: Float,
        trebleCut: Float,
        sampleRate: Int,
        channels: Int
    ): ShortArray {
        if (delayWet <= 0.01f && reverbWet <= 0.01f && bassStrength <= 0.01f && trebleCut <= 0.01f) return pcm

        val output = ShortArray(pcm.size)

        // Delay line
        val delayMs = (100 + delayWet * 400).toInt()
        val delaySamples = (delayMs * sampleRate / 1000) * channels

        // Reverb delay line (comb filter)
        val revDelayMs = (30 + reverbWet * 100).toInt()
        val revDelaySamples = (revDelayMs * sampleRate / 1000).coerceAtLeast(1)
        val revBufSize = revDelaySamples * channels
        val revBuf = ShortArray(revBufSize)
        var revPos = 0

        // Bass lowpass state (per channel)
        val bassAlpha = 0.94f
        var bassLpL = 0f
        var bassLpR = 0f

        // Treble lowpass state (per channel)
        val trebleAlpha = if (trebleCut > 0.01f) {
            val maxFreq = sampleRate / 2f
            val cf = maxFreq * (1f - trebleCut * 0.9f) + 1f
            (cf / (cf + (sampleRate / Math.PI))).toFloat().coerceIn(0.01f, 0.99f)
        } else 0f
        var trebleLpL = 0f
        var trebleLpR = 0f

        // Single pass over all samples (order: delay -> reverb -> bass -> treble)
        // to match the original sequential processing order
        for (i in pcm.indices) {
            var sample = pcm[i].toFloat() / 32768f
            val ch = i and 1  // 0 for left, 1 for right

            // Delay effect
            if (delayWet > 0.01f && i >= delaySamples) {
                val delayed = pcm[i - delaySamples].toFloat() / 32768f
                sample = sample * (1 - delayWet * 0.3f) + delayed * delayWet * 0.3f
            }

            // Reverb effect (comb filter with feedback)
            if (reverbWet > 0.01f) {
                val wetSample = revBuf[revPos].toFloat() / 32768f
                val drySample = sample
                revBuf[revPos] = ((drySample * (1 - 0.4f * reverbWet) + wetSample * 0.3f) * 32768f)
                    .toInt().coerceIn(-32768, 32767).toShort()
                revPos = (revPos + 1) % revBufSize
                sample = drySample * (1 - reverbWet * 0.5f) + wetSample * reverbWet * 0.5f
            }

            // Bass boost (low shelf filter)
            if (bassStrength > 0.01f) {
                if (ch == 0) {
                    bassLpL = bassAlpha * bassLpL + (1 - bassAlpha) * sample
                    sample += bassStrength * 2.5f * bassLpL
                } else {
                    bassLpR = bassAlpha * bassLpR + (1 - bassAlpha) * sample
                    sample += bassStrength * 2.5f * bassLpR
                }
            }

            // Treble cut (lowpass filter)
            if (trebleCut > 0.01f) {
                if (ch == 0) {
                    trebleLpL = trebleAlpha * trebleLpL + (1 - trebleAlpha) * sample
                    sample = trebleLpL
                } else {
                    trebleLpR = trebleAlpha * trebleLpR + (1 - trebleAlpha) * sample
                    sample = trebleLpR
                }
            }

            // Write output
            output[i] = (sample * 32768f).toInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    private fun applySpeedPitch(pcm: ShortArray, tempo: Float, semitones: Float, channels: Int): ShortArray {
        if (tempo == 1.0f && semitones == 0f) return pcm
        val pitchFactor = Math.pow(2.0, (semitones / 12.0)).toFloat()
        val ratio = 1.0f / (tempo.coerceIn(0.25f, 2.0f) * pitchFactor.coerceIn(0.25f, 4.0f))
        val inFrames = pcm.size / channels
        val outFrames = (inFrames * ratio).toInt().coerceIn(1, pcm.size)
        val output = ShortArray(outFrames * channels)
        for (frame in 0 until outFrames) {
            val srcPos = frame / ratio
            val srcFrame = srcPos.toInt().coerceIn(0, inFrames - 2)
            val frac = (srcPos - srcFrame).toFloat().coerceIn(0f, 1f)
            val baseIn = srcFrame * channels
            val baseOut = frame * channels
            for (ch in 0 until channels) {
                val cur = pcm[baseIn + ch]
                val next = pcm[baseIn + channels + ch]
                val sample = (cur * (1 - frac) + next * frac).toInt()
                output[baseOut + ch] = sample.coerceIn(-32768, 32767).toShort()
            }
        }
        return output
    }

    // Previous individual effect functions (applyReverb, applyDelay, applyBassBoost, applyTrebleCut)
    // have been replaced by the combined single-pass applyCombinedEffects for better performance.

    private fun mixAtmosphereLayers(context: Context, pcm: ShortArray, sampleRate: Int, channels: Int, preset: PresetValues): ShortArray {
        if (preset.rainVolume <= 0.01f && preset.vinylVolume <= 0.01f &&
            preset.windVolume <= 0.01f && preset.tapeVolume <= 0.01f) return pcm
        val output = pcm.copyOf()
        listOf("rain" to preset.rainVolume, "vinyl" to preset.vinylVolume,
            "wind" to preset.windVolume, "tape" to preset.tapeVolume)
            .filter { it.second > 0.01f }
            .forEach { (key, vol) ->
                val atmosPcm = readAtmospherePcm(context, key, sampleRate, output.size)
                if (atmosPcm != null) {
                    for (i in output.indices) {
                        if (i < atmosPcm.size) {
                            output[i] = (output[i] + atmosPcm[i] * vol * 0.8f).toInt()
                                .coerceIn(-32768, 32767).toShort()
                        }
                    }
                }
            }
        return output
    }

    private fun readAtmospherePcm(context: Context, key: String, targetRate: Int, targetLength: Int): ShortArray? {
        val assetPath = getAtmosphereAssetPath(key) ?: return null

        // Check cache first
        val cached = atmospherePcmCache[key]
        if (cached != null) {
            // Resample if needed and create properly sized array
            val pcmAtTargetRate = if (targetRate != 44100) {
                resamplePcm(cached, 44100, targetRate, 2)
            } else cached
            val result = ShortArray(targetLength)
            var pos = 0
            val copySize = pcmAtTargetRate.size
            while (pos < targetLength) {
                val len = minOf(copySize, targetLength - pos)
                System.arraycopy(pcmAtTargetRate, 0, result, pos, len)
                pos += len
            }
            return result
        }

        return try {
            val input = context.assets.open(assetPath)
            val allBytes = input.readBytes()
            input.close()

            // WAV: search for "data" chunk properly (skip variable-length headers)
            var offset = 12 // Skip RIFF header (12 bytes: RIFF + size + WAVE)
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
                // Pad to even boundary if needed
                if (chunkSize % 2 != 0) offset++
            }

            if (dataSize <= 0) return null

            // Extract PCM data from the data chunk
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

            if (channels == 1) pcm = monoToStereo(pcm)
            if (fileRate != targetRate) pcm = resamplePcm(pcm, fileRate, targetRate, 2)

            // Cache the processed atmosphere at 44100/2ch for next time
            if (targetRate == 44100) {
                atmospherePcmCache[key] = pcm.copyOf()
            }

            val result = ShortArray(targetLength)
            var pos = 0
            val copySize = pcm.size
            while (pos < targetLength) {
                val len = minOf(copySize, targetLength - pos)
                System.arraycopy(pcm, 0, result, pos, len)
                pos += len
            }
            result
        } catch (_: Exception) { null }
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

    fun cancelExport() {
        isCancelled.set(true)
    }
}