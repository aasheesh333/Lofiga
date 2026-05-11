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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

object ExportService {

    private val isCancelled = AtomicBoolean(false)

    private val SUPPORTED_FORMATS = listOf("m4a", "wav")
    private val SUPPORTED_BITRATES = listOf("128k", "192k", "256k", "320k")
    private const val MAX_PCM_SAMPLES = 40_000_000

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
        val outputDirPath = outputDir
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lofiga").absolutePath
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

            val outputStream = java.io.ByteArrayOutputStream()
            val bufInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var processedBytes = 0L

            while (!sawOutputEOS) {
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
                            processedBytes += sampleSize
                            if (inputDuration > 0) {
                                onProgress?.invoke((processedBytes.toFloat() / inputDuration).coerceIn(0f, 0.3f))
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
                        val bytes = ByteArray(decInfo.size)
                        outBuf.position(decInfo.offset)
                        outBuf.get(bytes, 0, decInfo.size)
                        outputStream.write(bytes)
                    }
                    decoder.releaseOutputBuffer(decIdx, false)
                    decIdx = decoder.dequeueOutputBuffer(decInfo, 0)
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

val pcmBytes = outputStream.toByteArray()
var pcmShorts = ShortArray(pcmBytes.size / 2)
ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmShorts)

if (pcmShorts.isEmpty()) return pcmShorts

                if (pcmShorts.size > MAX_PCM_SAMPLES) {
                    pcmShorts = pcmShorts.copyOf(MAX_PCM_SAMPLES)
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

    private fun applyPresetEffects(pcm: ShortArray, sampleRate: Int, channels: Int, preset: PresetValues): ShortArray {
        var result = pcm
        result = applySpeedPitch(result, preset.tempo, preset.pitch, channels)
        result = applyDelay(result, preset.delay, sampleRate, channels)
        result = applyReverb(result, preset.reverb, sampleRate, channels)
        result = applyBassBoost(result, preset.bass)
        result = applyTrebleCut(result, preset.trebleCut, sampleRate)
        return result
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

    private fun applyReverb(pcm: ShortArray, wet: Float, sampleRate: Int, channels: Int): ShortArray {
        if (wet <= 0.01f) return pcm
        val delayMs = (30 + wet * 100).toInt()
        val delaySamples = (delayMs * sampleRate / 1000)
        val output = ShortArray(pcm.size)
        val delayBuf = ShortArray(delaySamples * channels)
        var delayPos = 0
        for (i in pcm.indices) {
            val wetSample = delayBuf[delayPos]
            delayBuf[delayPos] = (pcm[i] * (1 - 0.4f * wet) + wetSample * 0.3f).toInt()
                .coerceIn(-32768, 32767).toShort()
            delayPos = (delayPos + 1) % delayBuf.size
            output[i] = (pcm[i] * (1 - wet * 0.5f) + wetSample * wet * 0.5f).toInt()
                .coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    private fun applyDelay(pcm: ShortArray, wet: Float, sampleRate: Int, channels: Int): ShortArray {
        if (wet <= 0.01f) return pcm
        val delayMs = (100 + wet * 400).toInt()
        val delaySamples = (delayMs * sampleRate / 1000) * channels
        val output = ShortArray(pcm.size)
        for (i in output.indices) {
            val delayed = if (i >= delaySamples) pcm[i - delaySamples] else 0
            output[i] = (pcm[i] * (1 - wet * 0.3f) + delayed * wet * 0.3f).toInt()
                .coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    private fun applyBassBoost(pcm: ShortArray, strength: Float): ShortArray {
        if (strength <= 0.01f) return pcm
        val alpha = 0.94f
        var lp = 0f
        val output = ShortArray(pcm.size)
        for (i in pcm.indices) {
            val sample = pcm[i] / 32768f
            lp = alpha * lp + (1 - alpha) * sample
            output[i] = ((sample + strength * 2.5f * lp) * 32768f).toInt()
                .coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    private fun applyTrebleCut(pcm: ShortArray, cutoffFactor: Float, sampleRate: Int): ShortArray {
        if (cutoffFactor <= 0.01f) return pcm
        val maxFreq = sampleRate / 2f
        val cutoffFreq = maxFreq * (1f - cutoffFactor * 0.9f) + 1f
        val alpha = (cutoffFreq / (cutoffFreq + (sampleRate / Math.PI))).toFloat().coerceIn(0.01f, 0.99f)
        var lp = 0f
        val output = ShortArray(pcm.size)
        for (i in pcm.indices) {
            val sample = pcm[i] / 32768f
            lp = alpha * lp + (1 - alpha) * sample
            output[i] = (lp * 32768f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

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
        return try {
            val input = context.assets.open(assetPath)
            val header = ByteArray(44)
            var read = 0
            while (read < 44) { val r = input.read(header, read, 44 - read); if (r < 0) break; read += r }
            if (read < 44) { input.close(); return null }

            val channels = ((header[23].toInt() and 0xFF) shl 8) or (header[22].toInt() and 0xFF)
            val fileRate = ((header[27].toInt() and 0xFF) shl 24) or
                    ((header[26].toInt() and 0xFF) shl 16) or
                    ((header[25].toInt() and 0xFF) shl 8) or
                    (header[24].toInt() and 0xFF)
            val dataSize = ((header[43].toInt() and 0xFF) shl 24) or
                    ((header[42].toInt() and 0xFF) shl 16) or
                    ((header[41].toInt() and 0xFF) shl 8) or
                    (header[40].toInt() and 0xFF)
            val bitsPerSample = ((header[35].toInt() and 0xFF) shl 8) or (header[34].toInt() and 0xFF)

            val pcmBytes = ByteArray(dataSize.coerceIn(0, 50_000_000))
            var totalRead = 0
            while (totalRead < pcmBytes.size) {
                val r = input.read(pcmBytes, totalRead, pcmBytes.size - totalRead)
                if (r < 0) break
                totalRead += r
            }
            input.close()

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