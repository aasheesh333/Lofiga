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

    /**
     * Runs [block] on a separate thread with a timeout. If the call blocks
     * longer than [timeoutMs] (e.g., encoder.stop() or muxer.stop() hanging
     * on MIUI/Xiaomi firmware), the thread is abandoned and the export
     * continues instead of stalling forever.
     */
    private fun runTimed(timeoutMs: Long, name: String, block: () -> Unit) {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            executor.submit<Unit> { runCatching { block() } }
                .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            Log.w("ExportService", "$name timed out after ${timeoutMs}ms; abandoning")
        } catch (_: Exception) {
            // execution error — log only
        } finally {
            executor.shutdownNow()
        }
    }

    // ── Export black-box diagnostics ────────────────────────────────────────
    // A stalled export previously produced only a generic "timed out" message
    // with no clue WHERE it hung (decoder dequeue? encoder EOS? MediaStore?).
    // This snapshot is updated at every phase/iteration so a timeout can report
    // the exact stall location. Read by MainViewModel to build the error text.
    @Volatile private var diagPhase: String = "idle"
    @Volatile private var diagProgress: Float = 0f
    @Volatile private var diagFramesEncoded: Long = 0L
    @Volatile private var diagInputExhausted: Boolean = false
    @Volatile private var diagEosSignaled: Boolean = false
    @Volatile private var diagSawEncoderEOS: Boolean = false
    @Volatile private var diagLastActivityMs: Long = 0L
    @Volatile private var diagStartMs: Long = 0L

    private fun diag(phase: String) {
        diagPhase = phase
        diagLastActivityMs = System.currentTimeMillis()
    }

    /** Human-readable snapshot of the last/current export, for error surfaces. */
    fun lastExportDiagnostics(): String {
        val now = System.currentTimeMillis()
        val idle = if (diagLastActivityMs > 0) (now - diagLastActivityMs) / 1000 else -1
        val elapsed = if (diagStartMs > 0) (now - diagStartMs) / 1000 else -1
        return "phase=$diagPhase, progress=${(diagProgress * 100).toInt()}%, " +
            "framesEncoded=$diagFramesEncoded, inputExhausted=$diagInputExhausted, " +
            "eosSignaled=$diagEosSignaled, sawEncoderEOS=$diagSawEncoderEOS, " +
            "idle=${idle}s, elapsed=${elapsed}s"
    }

    // Chunk size for streaming processing — ~1 second of stereo 44.1kHz 16-bit audio
    private const val CHUNK_FRAMES = 44100
    private const val CHUNK_SHORTS = CHUNK_FRAMES * 2

    // Hard ceiling for the whole pipeline, enforced on a dedicated thread. Kept
    // safely below MainViewModel's 10-minute withTimeout so the export always
    // returns a diagnostic error itself instead of the caller's generic timeout.
    private const val EXPORT_HARD_LIMIT_MS = 9L * 60 * 1000

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

        // Decode-phase progress (ChunkedPcmSource, 0..0.45) and encode-phase
        // progress (0.45..0.95) interleave on the same pipeline: nextChunk()
        // runs inside the encode feed loop, so both writers alternate values
        // (58% encode, then 12% decode) — the UI flickered between "1%" and
        // "60%" simultaneously. A max-keeping reporter makes the bar strictly
        // monotonic; the final 1.0f still passes through.
        var lastReported = 0f
        val monotonicProgress: ((Float) -> Unit)? = onProgress?.let { orig ->
            { p -> if (p > lastReported) { lastReported = p; orig(p) } }
        }

        val cleanName = fileName.substringBeforeLast(".").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val outputDirPath = outputDir ?: if (musicDir != null) {
            File(musicDir, "Lofiga").absolutePath
        } else {
            File(context.filesDir, "Lofiga").absolutePath
        }
        File(outputDirPath).mkdirs()

        val outputFile = File(outputDirPath, "${cleanName}_lofi.$format")

        diagStartMs = System.currentTimeMillis()
        diagProgress = 0f
        diagFramesEncoded = 0L
        diagInputExhausted = false
        diagEosSignaled = false
        diagSawEncoderEOS = false
        diag("starting")

        try {
            val sourceUri = if (inputPath != null && File(inputPath).exists()) {
                Uri.fromFile(File(inputPath))
            } else {
                inputUri
            }

            // Hard thread-level ceiling: MediaCodec dequeue calls and MediaStore
            // I/O can block indefinitely on some firmware (notably MIUI) — a
            // timeout passed to dequeueOutputBuffer is not honoured once the
            // codec's internal thread dies. withTimeout() in the caller cannot
            // interrupt such blocking native calls, so it only throws at the
            // full 10-minute mark. Running the whole pipeline on a dedicated
            // thread and abandoning it after 9 minutes guarantees the export
            // returns (with diagnostics) instead of freezing the UI.
            val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
            val future = worker.submit<Unit> {
                diag("open")
                if (format == "wav") {
                    exportAsWavStreaming(context, sourceUri, preset, outputFile, cancelFlag, monotonicProgress)
                } else {
                    exportWithMediaCodecStreaming(context, sourceUri, preset, outputFile, format, bitrate, cancelFlag, monotonicProgress)
                }
                // MediaStore publish moved inside the bounded worker: insert()
                // and the copy stream can wedge on a busy MediaProvider.
                if (!cancelFlag.get() && outputFile.exists()) {
                    diag("publish")
                    publishToMediaStore(context, outputFile, format)
                }
            }
            try {
                future.get(EXPORT_HARD_LIMIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                cancelFlag.set(true)
                Log.e("ExportService", "Export hard-timeout; ${lastExportDiagnostics()}")
                worker.shutdownNow() // abandon the stuck native call (thread leaks until process death)
                if (outputFile.exists()) outputFile.delete()
                throw RuntimeException("Export stalled (${lastExportDiagnostics()})")
            } catch (e: java.util.concurrent.ExecutionException) {
                throw (e.cause ?: e)
            } finally {
                worker.shutdown()
            }

            if (cancelFlag.get()) {
                outputFile.delete()
                return@withContext null
            }

            diag("done")
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

    /** Publishes a finished export file into MediaStore (API 29+) or scans it. */
    private fun publishToMediaStore(context: Context, outputFile: File, format: String) {
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
        // Per-layer global loop position so the mix phase stays continuous
        // across chunks (resetting per chunk produced a click every ~1s).
        var atmospherePos: IntArray = IntArray(0)

        // ── Streaming WSOLA state ──────────────────────────────────────────
        // The time-stretch must be CONTINUOUS across chunks: grains are placed
        // at GLOBAL positions (g*hopIn input → g*hopOut output), and each
        // output slice is cut from that global stream. Chunks therefore
        // overlap seamlessly — previously every chunk restarted grain
        // alignment from frame 0, which produced a discontinuity (audible
        // click/dip) at every ~1s boundary.
        var wsolaCarry: ShortArray = ShortArray(0)   // unconsumed input frames
        var wsolaGlobalStart: Long = 0L              // global input frame of wsolaCarry[0]
        var wsolaSlice: Long = 0L                    // next output slice index (44100-frame slices)
        var wsolaEffTempo: Float = 1f                // tempo actually used by the stretch (tempo/pitchFactor)

        // Output-side overlap carry. Grains whose Hann window extends past the
        // end of the current output slice MUST be carried into the next slice
        // together with their window-sum, otherwise the frames near each slice
        // boundary are normalised by a PARTIAL window sum (~0.02) and get
        // amplified ~50x — that was the "blown speaker" blast, once per second
        // (outSliceFrames = CHUNK_FRAMES = 44100 = 1s).
        var wsolaOutCarry: FloatArray = FloatArray(0)   // interleaved samples
        var wsolaNormCarry: FloatArray = FloatArray(0)  // per-frame window sum
        var wsolaOutCarryStart: Long = 0L               // global output frame of carry[0]

        // First grain index NOT yet rendered. Grains that straddle a slice
        // boundary are already fully accumulated into the carry above, so the
        // next slice must NOT render them again — doing so double-counts both
        // the signal and the window sum (norm → 2.0), which halved the volume
        // and comb-filtered the first ~1800 frames of every slice.
        var wsolaNextGrain: Long = 0L

        fun loadAtmospheres(context: Context) {
            val layers = mutableListOf<Pair<ShortArray, Int>>()
            listOf("rain" to rainVolume, "vinyl" to vinylVolume, "wind" to windVolume, "tape" to tapeVolume)
                .filter { it.second > 0.01f }
                .forEach { (key, vol) ->
                    readAtmospherePcm(context, key, 44100)?.let { pcm ->
                        // 0.6 layer gain (was 0.8): with the music pre-scaled to
                        // 0.82 in mixAtmosphereChunk this keeps the ambience
                        // clearly present but well under the music, and matches
                        // the quieter live-playback balance.
                        layers.add(pcm to (vol * 0.6f * 32768f).toInt())
                    }
                }
            atmosphereLayers = layers
            atmospherePos = IntArray(layers.size)
        }
    }

    /**
     * Process a single chunk of PCM through all active effects. Returns null
     * when the streaming time-stretch is still buffering input (the caller
     * must keep feeding chunks; no output is produced yet).
     */
    private fun processChunk(
        chunk: ShortArray,
        state: StreamingEffectState,
        sampleRate: Int,
        channels: Int
    ): ShortArray? {
        if (state.tempo != 1.0f || state.pitch != 0f) {
            val stretched = applySpeedPitchChunk(chunk, state.tempo, state.pitch, channels, state)
                ?: return null
            return finishProcessChunk(stretched, state, channels)
        }
        return finishProcessChunk(chunk, state, channels)
    }

    /** Applies delay/reverb/bass/treble + atmosphere mixing to stretched PCM. */
    private fun finishProcessChunk(
        pcm: ShortArray,
        state: StreamingEffectState,
        channels: Int
    ): ShortArray {
        var result = pcm
        if (state.delayWet > 0.01f || state.reverbWet > 0.01f ||
            state.bassStrength > 0.01f || state.trebleCut > 0.01f) {
            result = applyCombinedEffectsChunk(result, state, channels)
        }
        if (state.atmosphereLayers.isNotEmpty()) {
            result = mixAtmosphereChunk(result, state.atmosphereLayers, channels, state)
        }
        return result
    }

    // Chunk-boundary crossfade REMOVED. It was a band-aid for the WSOLA slice
    // discontinuity, but it blended the PREVIOUS chunk's last 441 frames
    // (already written to the file) into the CURRENT chunk's first 441 frames —
    // two different pieces of audio — adding a 10 ms ghost/flutter every second.
    // produceWsolaSlice now carries its output overlap (samples + window sums)
    // across slices, so the stream is continuous by construction.

    private fun applySpeedPitchChunk(
        pcm: ShortArray,
        tempo: Float,
        semitones: Float,
        channels: Int,
        state: StreamingEffectState
    ): ShortArray? {
        if (tempo == 1.0f && semitones == 0f) return pcm

        val safeTempo = tempo.coerceIn(0.25f, 2.5f)
        val pitchFactor = Math.pow(2.0, (semitones / 12.0)).toFloat().coerceIn(0.25f, 4.0f)

        // Fold pitch into the stretch: stretch by tempo/pitchFactor, then
        // resample back by pitchFactor — net duration = tempo-only stretch,
        // pitch shifted. Keeps the stretch fully inside the streaming WSOLA
        // (per-chunk pitch resampling would reintroduce seams).
        val effTempo = if (pitchFactor != 1f) (safeTempo / pitchFactor).coerceIn(0.25f, 2.5f) else safeTempo
        state.wsolaEffTempo = effTempo
        var result = timeStretchStreamingFeed(pcm, effTempo, channels, state) ?: return null

        if (pitchFactor != 1f) {
            val stretchedFrames = result.size / channels
            result = resampleFramesChunk(
                result,
                (stretchedFrames / pitchFactor).toInt().coerceAtLeast(1),
                channels
            )
        }
        return result
    }

    /**
     * Feeds one raw PCM chunk into the streaming WSOLA pipeline. Returns the
     * next output slice (44100 frames) when enough input has accumulated, or
     * null when the input is still being buffered (caller must keep feeding).
     */
    private fun timeStretchStreamingFeed(
        pcm: ShortArray,
        tempo: Float,
        channels: Int,
        state: StreamingEffectState
    ): ShortArray? {
        val grain = 2048
        val hopOut = 1024
        val outSliceFrames = CHUNK_FRAMES
        val hopIn = hopOut * tempo.toDouble()

        // Append to the carry buffer (input since wsolaGlobalStart).
        val prev = state.wsolaCarry
        val combined = ShortArray(prev.size + pcm.size)
        System.arraycopy(prev, 0, combined, 0, prev.size)
        System.arraycopy(pcm, 0, combined, prev.size, pcm.size)
        state.wsolaCarry = combined

        val slice = state.wsolaSlice
        val outStart = slice * outSliceFrames
        // Grains g with q_g = g*hopOut whose window [q_g, q_g+grain) intersects
        // the output slice [outStart, outStart+outSliceFrames). Clamped so
        // slice 0 starts the stream at grain 0 (no negative grains).
        val gStart = maxOf(0L, ((outStart - grain) / hopOut) + 1)
        val gEnd = (outStart + outSliceFrames - 1) / hopOut
        val inStart = (gStart * hopIn).toLong()
        val inEnd = (gEnd * hopIn).toLong() + grain
        val inNeeded = (inEnd - inStart).toInt()

        val carryFrames = state.wsolaCarry.size / channels
        if (carryFrames < inNeeded) return null // keep buffering

        return produceWsolaSlice(state, channels, slice, gStart, gEnd,
            inStart, outStart, outSliceFrames, grain, hopOut, hopIn, carryFrames)
    }

    /**
     * Called when the input source is exhausted: emits the final partial
     * output slice from whatever input remains (the file's tail). Returns an
     * empty array when nothing is left.
     */
    private fun timeStretchStreamingFlush(
        tempo: Float,
        channels: Int,
        state: StreamingEffectState
    ): ShortArray {
        if (state.wsolaCarry.isEmpty()) {
            // Input ended exactly on a slice boundary: the pending output
            // overlap is all that is left. Emit it as a short fade-out instead
            // of truncating the last ~46 ms mid-waveform.
            return flushWsolaOutCarry(state, channels)
        }
        val grain = 2048
        val hopOut = 1024
        val outSliceFrames = CHUNK_FRAMES
        val hopIn = hopOut * tempo.toDouble()

        val slice = state.wsolaSlice
        val outStart = slice * outSliceFrames
        val gStart = maxOf(0L, ((outStart - grain) / hopOut) + 1)
        val inStart = (gStart * hopIn).toLong()
        val carryFrames = state.wsolaCarry.size / channels
        val relInStart = (inStart - state.wsolaGlobalStart).toInt()

        // Too little input left even for one grain — resample the remainder.
        if (carryFrames - relInStart < grain) {
            val availFrames = (carryFrames - relInStart).coerceAtLeast(0)
            if (availFrames <= 0) return ShortArray(0)
            val avail = ShortArray(availFrames * channels)
            System.arraycopy(state.wsolaCarry, relInStart * channels, avail, 0, avail.size)
            // CONSUME the remainder: a repeated flush must return empty so the
            // feed loop marks inputExhausted and the pipeline finalizes.
            // Previously this branch left wsolaCarry untouched (and never
            // advanced wsolaSlice), so flush returned the SAME resampled tail
            // forever — the export encoded that tail endlessly and hung at 96%
            // until MainViewModel's 10-minute withTimeout fired.
            state.wsolaCarry = ShortArray(0)
            state.wsolaGlobalStart = inStart
            state.wsolaSlice++
            val tail = resampleFramesChunk(avail, (availFrames / tempo).toInt().coerceAtLeast(1), channels)
            // Prepend the pending output overlap so the join to this resampled
            // remainder is continuous rather than a hard cut.
            val head = flushWsolaOutCarry(state, channels)
            if (head.isEmpty()) return tail
            val merged = ShortArray(head.size + tail.size)
            System.arraycopy(head, 0, merged, 0, head.size)
            System.arraycopy(tail, 0, merged, head.size, tail.size)
            return merged
        }

        // Largest grain that fits in the remaining input.
        val gEndNominal = (outStart + outSliceFrames - 1) / hopOut
        var gEnd = gStart
        while (gEnd <= gEndNominal &&
            (gEnd * hopIn).toLong() - inStart + grain <= carryFrames) {
            gEnd++
        }
        gEnd--
        if (gEnd < gStart) return ShortArray(0)

        val outLen = ((gEnd * hopOut) - outStart + grain).toInt().coerceAtLeast(1)
        val sliceLen = outLen.coerceAtMost(outSliceFrames)
        return produceWsolaSlice(state, channels, slice, gStart, gEnd,
            inStart, outStart, sliceLen, grain, hopOut, hopIn, carryFrames)
    }

    /**
     * Emits the pending output-overlap carry as final audio and clears it.
     *
     * The carry holds grain tails whose Hann window was never completed by a
     * following grain, so its window sum ramps down towards 0. Normalising by
     * that ramp would amplify the tail back to full scale (the same 50x blast
     * the carry was introduced to fix), so the ramp is deliberately LEFT IN:
     * it becomes a ~46 ms fade-out at the very end of the export, which is the
     * musically correct ending for a truncated overlap-add stream.
     */
    private fun flushWsolaOutCarry(
        state: StreamingEffectState,
        channels: Int
    ): ShortArray {
        val carryOut = state.wsolaOutCarry
        val carryNorm = state.wsolaNormCarry
        state.wsolaOutCarry = FloatArray(0)
        state.wsolaNormCarry = FloatArray(0)
        if (carryNorm.isEmpty()) return ShortArray(0)

        // Trim frames that carry no energy at all (window sum ~0).
        var last = carryNorm.size - 1
        while (last >= 0 && carryNorm[last] < 1e-4f) last--
        if (last < 0) return ShortArray(0)
        val frames = last + 1

        val result = ShortArray(frames * channels)
        for (f in 0 until frames) {
            // Divide by 1.0 (not by the ramp) → natural fade-out.
            for (c in 0 until channels) {
                val v = carryOut[f * channels + c].toInt()
                result[f * channels + c] = v.coerceIn(-32768, 32767).toShort()
            }
        }
        return result
    }

    private fun produceWsolaSlice(
        state: StreamingEffectState,
        channels: Int,
        slice: Long,
        gStart: Long,
        gEnd: Long,
        inStart: Long,
        outStart: Long,
        outLen: Int,
        grain: Int,
        hopOut: Int,
        hopIn: Double,
        carryFrames: Int
    ): ShortArray {
        val carry = state.wsolaCarry
        // Accumulate into a buffer that is `grain` frames LONGER than the slice
        // we emit. Grains whose Hann window runs past the slice end land in that
        // tail and are carried into the next slice instead of being truncated.
        //
        // This is the "blown speaker" fix: previously the window was cut at
        // outLen, so the last frames of every slice had only a partial
        // window-sum in `norm` (often ~0.02). Dividing by that tiny value
        // multiplied those samples by ~50x, producing a full-scale blast at
        // every slice boundary — i.e. once per second, since
        // outSliceFrames = CHUNK_FRAMES = 44100.
        val accLen = outLen + grain
        val out = FloatArray(accLen * channels)
        val norm = FloatArray(accLen)
        val window = FloatArray(grain) { i ->
            (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (grain - 1))).toFloat()
        }

        // Fold in the overlap left by the previous slice, aligned by its global
        // output position (the flush path can emit a short slice, so carry[0]
        // does not always land exactly on this slice's outStart).
        val prevOut = state.wsolaOutCarry
        val prevNorm = state.wsolaNormCarry
        if (prevNorm.isNotEmpty()) {
            val offset = (state.wsolaOutCarryStart - outStart).toInt()
            if (offset >= 0 && offset < accLen) {
                val copyFrames = minOf(prevNorm.size, accLen - offset)
                for (f in 0 until copyFrames) {
                    val dstF = offset + f
                    norm[dstF] += prevNorm[f]
                    val srcBase = f * channels
                    val dstBase = dstF * channels
                    for (c in 0 until channels) {
                        out[dstBase + c] += prevOut[srcBase + c]
                    }
                }
            }
            // offset out of range → the carry belongs to a discarded region;
            // dropping it is correct (and safer than misaligning it).
        }

        // Grains below wsolaNextGrain straddled the previous slice boundary and
        // are ALREADY fully accumulated in the carry folded in above. Rendering
        // them again would add their signal and window a second time (norm → 2),
        // which halves the level and comb-filters the first ~grain frames of
        // every slice. Start at the first grain that has never been rendered.
        val firstG = maxOf(gStart, state.wsolaNextGrain)
        for (g in firstG..gEnd) {
            val baseIn = ((g * hopIn).toLong() - inStart).toInt() // within carry
            if (baseIn < 0) continue                              // input already dropped
            if (baseIn + grain > carryFrames) continue // should not happen when input is sufficient
            val q = ((g * hopOut) - outStart).toInt() // within out; may be negative
            val kStart = if (q < 0) -q else 0
            val kEnd = minOf(grain, accLen - q)
            if (kEnd <= kStart) continue
            for (k in kStart until kEnd) {
                val w = window[k]
                val src = (baseIn + k) * channels
                val dst = (q + k) * channels
                for (c in 0 until channels) {
                    out[dst + c] += carry[src + c].toFloat() * w
                }
                norm[q + k] += w
            }
        }
        state.wsolaNextGrain = maxOf(state.wsolaNextGrain, gEnd + 1)

        // Emit only the frames whose overlap is complete (the first outLen).
        val result = ShortArray(outLen * channels)
        for (f in 0 until outLen) {
            // Floor the divisor: at the very start of the stream the window
            // legitimately ramps from 0, and dividing by that would explode the
            // first few frames. Flooring turns it into a short, inaudible
            // fade-in instead.
            val n = maxOf(norm[f], 0.35f)
            for (c in 0 until channels) {
                val v = (out[f * channels + c] / n).toInt()
                result[f * channels + c] = v.coerceIn(-32768, 32767).toShort()
            }
        }

        // Keep the unfinished tail (and its window sums) for the next slice.
        val tailFrames = accLen - outLen
        val tailOut = FloatArray(tailFrames * channels)
        val tailNorm = FloatArray(tailFrames)
        System.arraycopy(out, outLen * channels, tailOut, 0, tailFrames * channels)
        System.arraycopy(norm, outLen, tailNorm, 0, tailFrames)
        state.wsolaOutCarry = tailOut
        state.wsolaNormCarry = tailNorm
        state.wsolaOutCarryStart = outStart + outLen

        // Drop consumed input: everything before the next slice's inStart.
        val nextGStart = maxOf(0L, ((slice * CHUNK_FRAMES + CHUNK_FRAMES - grain) / hopOut) + 1)
        val nextInStart = (nextGStart * hopIn).toLong()
        val dropFrames = (nextInStart - state.wsolaGlobalStart).toInt().coerceIn(0, carryFrames)
        val keptShorts = carry.size - dropFrames * channels
        if (keptShorts > 0) {
            state.wsolaCarry = carry.copyOfRange(dropFrames * channels, carry.size)
        } else {
            state.wsolaCarry = ShortArray(0)
        }
        state.wsolaGlobalStart = nextInStart
        state.wsolaSlice++
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

        // Pre-attenuate the dry signal proportionally to how much bass boost is
        // applied so the boosted low end has room to grow WITHOUT slamming the
        // ceiling. The live path uses a hardware BassBoost shelf (gentle); the
        // old offline gain of 2.5x with no headroom pushed bass-heavy material
        // way past full-scale, and the hard clip at the end turned that into
        // the "bold"/glitchy distortion the user hears. 1.0 - 0.35*strength
        // leaves headroom that matches the added low-end energy.
        val dryGain = 1f - 0.35f * state.bassStrength.coerceIn(0f, 1f)

        for (i in pcm.indices) {
            var sample = (pcm[i].toFloat() / 32768f) * dryGain
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

            // Bass — gain lowered 2.5f -> 1.2f to match the gentle hardware
            // BassBoost shelf used in live preview (was far more aggressive
            // offline, the main source of the export sounding "bolder").
            if (state.bassStrength > 0.01f) {
                if (ch == 0) {
                    state.bassLpL = state.bassAlpha * state.bassLpL + (1 - state.bassAlpha) * sample
                    sample += state.bassStrength * 1.2f * state.bassLpL
                } else {
                    state.bassLpR = state.bassAlpha * state.bassLpR + (1 - state.bassAlpha) * sample
                    sample += state.bassStrength * 1.2f * state.bassLpR
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

            // Soft limiter instead of a hard clip: samples inside ~[-0.8,0.8]
            // pass through linearly; beyond that they roll off along a tanh
            // curve toward ±1.0. A hard coerceIn() flattens peaks into a
            // square edge (clicks/glitch); the soft knee keeps loud transients
            // smooth, removing the post-render distortion.
            output[i] = (softLimit(sample) * 32768f).toInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    /**
     * Soft-knee limiter. Linear within ±[THRESH]; above it, the excess is
     * compressed through tanh so the signal asymptotically approaches ±1.0
     * without the abrupt flat-top a hard clip produces.
     */
    private fun softLimit(x: Float): Float {
        val thresh = 0.8f
        val ax = kotlin.math.abs(x)
        if (ax <= thresh) return x
        val sign = if (x >= 0f) 1f else -1f
        val over = ax - thresh
        val range = 1f - thresh
        // tanh maps [0,inf) -> [0,1); scale so the knee is continuous at thresh.
        return sign * (thresh + range * kotlin.math.tanh(over / range))
    }

    private fun mixAtmosphereChunk(
        pcm: ShortArray,
        layers: List<Pair<ShortArray, Int>>,
        channels: Int,
        state: StreamingEffectState
    ): ShortArray {
        val result = ShortArray(pcm.size)
        // Give the music a little headroom before summing the ambience so the
        // combined signal doesn't slam into full-scale and hard-clip (the
        // post-render "glitch"/distortion). 0.82 leaves ~1.6 dB for layers,
        // matching the lower per-layer gains used at load time.
        for (i in result.indices) {
            var sample = (pcm[i].toInt() * 0.82f)
            for (li in layers.indices) {
                val (layerPcm, scaledVol) = layers[li]
                val loopSize = layerPcm.size
                if (loopSize > 0) {
                    val pos = state.atmospherePos[li]
                    sample += ((layerPcm[pos % loopSize].toInt() * scaledVol) shr 15).toFloat()
                    state.atmospherePos[li] = pos + 1
                }
            }
            // Same soft-knee limiter as the effects stage so any overshoot from
            // summing layers rolls off smoothly instead of clicking.
            result[i] = (softLimit(sample / 32768f) * 32768f)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STREAMING DECODE + PROCESS + ENCODE PIPELINE
    // ═══════════════════════════════════════════════════════════════════════════

    private class ChunkedPcmSource(
        private val context: Context,
        private val sourceUri: Uri,
        private val cancelFlag: AtomicBoolean,
        private val onProgress: ((Float) -> Unit)?
    ) {
        private val extractor = MediaExtractor()
        private var decoder: MediaCodec? = null
        private var inputSampleRate = 44100
        private var inputChannels = 2
        private var inputDuration = 0L
        private var audioTrackIndex = -1
        val duration: Long get() = inputDuration
        fun open(): Boolean {
            extractor.setDataSource(context, sourceUri, null)
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
            inputDuration = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                // KEY_DURATION lives in the container, not the track format, and
                // is missing for many files. Without it the decode phase reports
                // no progress and the encode phase starts at 50%.
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, sourceUri)
                        retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull()?.times(1000L) ?: 0L
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrDefault(0L)
            }

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
            return true
        }

        fun close() {
            // decoder.stop()/release() can hang on broken firmware — bound each
            // to 10s so a stalled decoder never blocks the export thread.
            runTimed(10_000, "decoder.stop+release") {
                try { decoder?.stop() } catch (_: Exception) {}
                try { decoder?.release() } catch (_: Exception) {}
            }
            runTimed(5_000, "extractor.release") {
                try { extractor.release() } catch (_: Exception) {}
            }
        }

        fun nextChunk(): ShortArray? {
            if (cancelFlag.get()) return null

            val decoder = this.decoder ?: return null
            val accumulator = ShortArray(CHUNK_SHORTS)
            var accumulated = 0
            var sawInputEOS = false
            var sawOutputEOS = false

            // Per-call deadline: on some firmware the decoder can stop yielding
            // output while never flagging EOS, spinning this loop forever (the
            // dequeue timeout parameter is ignored once the codec's thread
            // dies). Bail after 60s of no progress so the pipeline can finalize
            // with what it has instead of hanging until the caller's timeout.
            val callStart = System.currentTimeMillis()
            var lastProgressMs = callStart

            while (accumulated < CHUNK_SHORTS && !sawOutputEOS) {
                if (cancelFlag.get()) return null
                if (System.currentTimeMillis() - lastProgressMs > 60_000) {
                    Log.w("ExportService", "decoder produced no output for 60s; ending stream")
                    break
                }

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
                                // Decode owns only the first half of the bar.
                                // It finishes EARLY relative to the encode when
                                // the tempo is slowed (output is longer than the
                                // input), so letting it claim 0.90 made the bar
                                // jump to 90% and then sit there while the
                                // encode caught up. Capping it at 0.50 keeps the
                                // bar responsive early without overtaking the
                                // encode's true progress.
                                val progress = (sampleTimeUs.toFloat() / inputDuration.toFloat())
                                    .coerceIn(0f, 1f) * 0.50f
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
                            lastProgressMs = System.currentTimeMillis()
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
        val totalDurationUs = source.duration

        val effectState = StreamingEffectState(preset, 44100, 2)
        effectState.loadAtmospheres(context)

        // Same tempo-aware denominator as the WAV path (see exportAsWavStreaming).
        val tempo = effectState.tempo.coerceIn(0.25f, 4f)
        val expectedOutUs = if (tempo != 1f) (totalDurationUs / tempo).toLong() else totalDurationUs

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
        var chunksProcessed = 0
        var pendingPcm = ShortArray(0)
        var pendingOffset = 0
        // True once the source is exhausted AND the final WSOLA flush came back
        // empty: the feed has nothing left to produce.
        var inputExhausted = false
        var eosSignaled = false
        // Consecutive empty output polls after EOS was queued. Encoders on some
        // firmware (e.g. MIUI) never deliver the final EOS output frame; without
        // this bound the export spins at 96% until MainViewModel's withTimeout
        // kills it 10 minutes later. After the cap we finalize with what we have
        // (the tail is at most a few AAC frames short — inaudible).
        var eosStallTries = 0
        val MAX_EOS_STALL_TRIES = 30

        val wallClockDeadline = System.currentTimeMillis() + 8 * 60 * 1000L
        try {
            while (!sawEncoderEOS && !cancelFlag.get() &&
                   System.currentTimeMillis() < wallClockDeadline) {
                diag(if (inputExhausted) "encode-eos" else "encode")
                diagInputExhausted = inputExhausted
                diagFramesEncoded = totalFramesEncoded
                // Safety net: at the minimum tempo (0.25) the stretched output
                // is at most 4x the source duration. If we ever exceed 5x, a
                // feed bug is looping (e.g. flush returning the same tail) —
                // force end-of-input so the bounded EOS wait finalizes the
                // file instead of hanging at 96% until the withTimeout.
                if (totalDurationUs > 0 && !inputExhausted &&
                    totalFramesEncoded > (totalDurationUs * 44100L / 1000000L) * 5L) {
                    Log.w("ExportService", "Output exceeds 5x source duration; forcing end-of-input")
                    inputExhausted = true
                }
                // 1) Ensure pendingPcm holds data to encode. Feed raw chunks to
                //    the streaming pipeline until it emits an output slice (it
                //    buffers input until a full WSOLA slice can be produced) or
                //    the input is exhausted (final flush).
                if (pendingOffset >= pendingPcm.size) {
                    var produced = false
                    while (!produced) {
                        if (cancelFlag.get()) break
                        val chunk = source.nextChunk()
                        if (chunk == null || chunk.isEmpty()) {
                            // Input exhausted: flush the final partial slice.
                            val flushed = timeStretchStreamingFlush(effectState.wsolaEffTempo, 2, effectState)
                            if (flushed.isNotEmpty()) {
                                pendingPcm = finishProcessChunk(flushed, effectState, 2)
                                pendingOffset = 0
                                chunksProcessed++
                                produced = true
                            } else {
                                inputExhausted = true
                                break
                            }
                        } else {
                            val processed = processChunk(chunk, effectState, 44100, 2)
                            if (processed != null) {
                                pendingPcm = processed
                                pendingOffset = 0
                                chunksProcessed++
                                produced = true
                            }
                        }
                    }
                }

                val inputIdx = encoder.dequeueInputBuffer(10000)
                if (inputIdx >= 0) {
                    val inBuf = encoder.getInputBuffer(inputIdx)
                    if (inBuf == null) {
                        try { encoder.queueInputBuffer(inputIdx, 0, 0, 0, 0) } catch (_: Exception) {}
                        continue
                    }
                    if (pendingOffset < pendingPcm.size) {
                        inBuf.clear()
                        val maxShorts = inBuf.capacity() / 2
                        val remaining = pendingPcm.size - pendingOffset
                        val framesToWrite = minOf(remaining, maxShorts)
                        val tempBytes = ByteArray(framesToWrite * 2)
                        ByteBuffer.wrap(tempBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            .put(pendingPcm, pendingOffset, framesToWrite)
                        inBuf.put(tempBytes)
                        val pts = (totalFramesEncoded * 1000000L) / 44100
                        encoder.queueInputBuffer(inputIdx, 0, tempBytes.size, pts, 0)
                        pendingOffset += framesToWrite
                        totalFramesEncoded += framesToWrite / 2
                        if (totalDurationUs > 0) {
                            // Encode is the bottleneck: pts/expectedOutUs tracks
                            // wall-clock completion (the encode runs at roughly
                            // realtime). Map it 0..0.96 so the bar starts at 0
                            // instead of jumping to 90% on the first chunk, then
                            // let the drain + finalize pad 0.96..1.0. Decode's
                            // own 0..0.5 mapping never dominates because the
                            // encode fraction rises in lockstep and stays ahead.
                            val progress = (pts.toFloat() / expectedOutUs.toFloat())
                                .coerceIn(0f, 1f) * 0.96f
                            diagProgress = progress
                            onProgress?.invoke(progress)
                        } else {
                            // No duration metadata at all: chunk-count fallback
                            // that still starts at 0 instead of jumping to 50%.
                            onProgress?.invoke((chunksProcessed * 0.02f).coerceAtMost(0.9f))
                        }
                    } else if (!eosSignaled) {
                        // Nothing left to encode — input exhausted and flushed.
                        // Queue EOS exactly ONCE: the previous code re-queued it
                        // on every free input buffer, which can confuse device
                        // encoders into never emitting the final output frame.
                        try {
                            encoder.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSignaled = true
                            diagEosSignaled = true
                        } catch (_: Exception) {}
                    }
                } else if (inputExhausted && pendingOffset >= pendingPcm.size && !eosSignaled) {
                    // Encoder input buffers are all full but there is nothing
                    // left to feed and no EOS was queued: force the end-of-stream
                    // so the pipeline always completes instead of spinning on a
                    // full input queue (previously the export stalled forever at
                    // ~95% progress).
                    try {
                        encoder.signalEndOfInputStream()
                        eosSignaled = true
                        diagEosSignaled = true
                    } catch (_: Exception) {}
                }

                val waitingForEos = inputExhausted && pendingOffset >= pendingPcm.size && eosSignaled
                val bufInfo = MediaCodec.BufferInfo()
                var wroteAnyOutput = false
                // Once everything is queued, poll with a short timeout: if the
                // encoder never delivers its EOS frame we must notice the stall
                // quickly (2s per poll) instead of blocking 10s per iteration.
                var outIdx = encoder.dequeueOutputBuffer(bufInfo, if (waitingForEos) 2000 else 10000)
                while (outIdx >= 0) {
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufInfo.size = 0
                    }
                    if (bufInfo.size > 0) {
                        wroteAnyOutput = true
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
                        diagSawEncoderEOS = true
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    outIdx = encoder.dequeueOutputBuffer(bufInfo, 0)
                }

                // Bounded EOS wait: some device encoders swallow the EOS frame
                // entirely. Only count iterations where nothing was written —
                // a slow but productive flush must never be cut short.
                if (!sawEncoderEOS && waitingForEos) {
                    if (wroteAnyOutput) {
                        eosStallTries = 0
                    } else {
                        eosStallTries++
                        if (eosStallTries >= MAX_EOS_STALL_TRIES) {
                            Log.w("ExportService", "Encoder never emitted EOS after $eosStallTries polls; finalizing anyway")
                            break
                        }
                    }
                } else {
                    eosStallTries = 0
                }
            }

            // Drain remaining output. Bounded: a codec that never flags EOS
            // (some devices) must not hang the export forever — give it 60
            // polls of at most 1s each (~1 minute worst case) then finalize
            // the muxer with what we have. Pad the bar across the drain so it
            // visibly crawls 96% -> ~99% instead of snapping to 100%.
            var drainTries = 0
            val MAX_DRAIN_TRIES = 60
            val drainProgressBase = 0.96f
            val drainProgressSpan = 0.03f
            diag("drain")
            while (!cancelFlag.get() && drainTries < MAX_DRAIN_TRIES) {
                drainTries++
                if ((drainTries % 10) == 0) {
                    val frac = drainTries.toFloat() / MAX_DRAIN_TRIES.toFloat()
                    onProgress?.invoke(drainProgressBase + drainProgressSpan * frac)
                }
                val bufInfo = MediaCodec.BufferInfo()
                val outIdx = encoder.dequeueOutputBuffer(bufInfo, 1000)
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
            // Bridge to 99% so the user sees the bar essentially done before
            // the muxer is finalized; the final 1.0f is emitted in finally so
            // the bar always completes even if finalization throws (previously
            // the export sat frozen at 96/99% when muxer.stop/release hiccuped).
            onProgress?.invoke(0.99f)
        } finally {
            diag("finalize")
            source.close()
            // encoder.stop()/muxer.stop() are well-known to hang on Xiaomi/MIUI
            // firmware when the codec never emitted its EOS output. Bound each
            // call so the export thread recovers instead of freezing until
            // MainViewModel's 10-minute withTimeout.
            if (muxerStarted) runTimed(10_000, "muxer.stop") { muxer.stop() }
            runTimed(5_000, "muxer.release") { muxer.release() }
            runTimed(15_000, "encoder.stop") { encoder.stop() }
            runTimed(15_000, "encoder.release") { encoder.release() }
            onProgress?.invoke(1.0f)
        }
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
        val totalDurationUs = source.duration

        val effectState = StreamingEffectState(preset, 44100, 2)
        effectState.loadAtmospheres(context)

        // Output duration = input duration / tempo: slowing to 0.8 stretches
        // the output to 1.25× the input. Mapping encode progress against the
        // INPUT duration caused the bar to hit 0.97 at ~80 % of real work and
        // then stall there until the render finished — the "stuck at 97 %".
        val tempo = effectState.tempo.coerceIn(0.25f, 4f)
        val expectedOutUs = if (tempo != 1f) (totalDurationUs / tempo).toLong() else totalDurationUs

        try {
            val headerPlaceholder = ByteArray(44)
            val raf = java.io.RandomAccessFile(outputFile, "rw")
            raf.write(headerPlaceholder)

            var totalDataSize = 0L
            var totalFramesWritten = 0L
            var wavChunksProcessed = 0

            val wallClockDeadline = System.currentTimeMillis() + 8 * 60 * 1000L
            while (!cancelFlag.get() && System.currentTimeMillis() < wallClockDeadline) {
                diag("wav-encode")
                val chunk = source.nextChunk()
                var processed: ShortArray? = null
                if (chunk != null && chunk.isNotEmpty()) {
                    processed = processChunk(chunk, effectState, 44100, 2)
                }
                if (processed == null) {
                    if (chunk == null) {
                        // Input exhausted: flush the final partial slice.
                        val flushed = timeStretchStreamingFlush(effectState.wsolaEffTempo, 2, effectState)
                        if (flushed.isNotEmpty()) {
                            processed = finishProcessChunk(flushed, effectState, 2)
                        } else {
                            break
                        }
                    } else {
                        // Streaming stretch still buffering — keep feeding.
                        continue
                    }
                }
                val outChunk = processed!!

                val tempBytes = ByteArray(outChunk.size * 2)
                ByteBuffer.wrap(tempBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .put(outChunk, 0, outChunk.size)
                raf.write(tempBytes)
                totalDataSize += outChunk.size * 2
                totalFramesWritten += outChunk.size / 2
                wavChunksProcessed++

                if (totalDurationUs > 0) {
                    val pts = (totalFramesWritten * 1000000L) / 44100
                    // Map against the OUTPUT duration (input / tempo) so the bar
                    // tracks real work when tempo is slowed. 0..0.97; the final
                    // 3% is bridged to 100% after the WAV header is patched in.
                    val progress = (pts.toFloat() / expectedOutUs.toFloat())
                        .coerceIn(0f, 1f) * 0.97f
                    diagProgress = progress
                    onProgress?.invoke(progress)
                } else {
                    onProgress?.invoke((wavChunksProcessed * 0.02f).coerceAtMost(0.9f))
                }
            }

            onProgress?.invoke(0.97f)
            raf.seek(0)
            writeWavHeader(raf, 44100, 2, totalDataSize)
            raf.close()

        } finally {
            source.close()
            // Guaranteed completion marker: the write loop above already maps
            // 0..0.97, so 1.0f here is reached even if header patching throws
            // (previously the bar froze below 100% on that failure path).
            onProgress?.invoke(1.0f)
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
