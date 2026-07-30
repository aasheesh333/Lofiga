package com.dhanuk.lofiga.model

/**
 * Per-track energy / mood classification derived from the FFT precompute.
 *
 * Uses the mean spectral centroid across all precomputed frames as a perceptual
 * proxy for brightness/energy, then bucketed into three bands so the library
 * can be filtered for "chill" listening sessions vs energetic picks.
 *
 * The boundaries are calibrated empirically against the 16 log-spaced FFT bands
 * the precompute emits (bands roughly span ~20Hz..~20kHz on a 1024-sample window
 * at the track's native sample rate). Centroid is the band index 0..15 where
 * half the magnitude mass sits below, half above — i.e. the "balance point".
 *
 *   Chill ...... centroid < 5.5  (energy concentrated in the lowest 5 bands)
 *   Mid ........ 5.5 .. 8.5    (mid-spectrum balance, e.g. typical lofi/vocals)
 *   Energetic .. centroid >= 8.5  (lots of high-frequency content)
 */
enum class MoodTag(val displayName: String) {
    CHILL("Chill"),
    MID("Mid"),
    ENERGETIC("Energetic");

    companion object {
        /**
         * Map a centroid value (band index, 0..15) to a [MoodTag].
         * Centroid is a Float — values outside [0, 15] are clamped.
         */
        fun fromCentroid(centroid: Float): MoodTag {
            val c = centroid.coerceIn(0f, 15f)
            return when {
                c < 5.5f -> CHILL
                c < 8.5f -> MID
                else -> ENERGETIC
            }
        }
    }
}
