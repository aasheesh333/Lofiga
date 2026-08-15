package com.dhanuk.lofiga.model

/**
 * Defines the built-in lofi presets available in the app.
 */
enum class LofiPreset(val displayName: String, val values: PresetValues) {
    Normal(
        "Normal",
        PresetValues(tempo = 1.0f, pitch = 0f, reverb = 0f, delay = 0f, bass = 0f, trebleCut = 0f)
    ),
    LofiSlow(
        "Slow Reverb",
        PresetValues(tempo = 0.88f, pitch = -1.5f, reverb = 0.45f, delay = 0.15f, bass = 0.25f, trebleCut = 0.45f)
    ),
    RainyNight(
        "Rainy Night",
        PresetValues(tempo = 0.90f, pitch = -1.0f, reverb = 0.50f, delay = 0.20f, bass = 0.20f, trebleCut = 0.50f,
            rainVolume = 0.28f, windVolume = 0.12f)
    ),
    Vintage(
        "Vintage",
        PresetValues(tempo = 0.95f, pitch = -0.5f, reverb = 0.30f, delay = 0f, bass = 0f, trebleCut = 0.60f,
            vinylVolume = 0.24f, tapeVolume = 0.15f)
    ),
    Dreamy(
        "Dreamy",
        PresetValues(tempo = 0.90f, pitch = -1.0f, reverb = 0.70f, delay = 0.40f, bass = 0f, trebleCut = 0.10f,
            windVolume = 0.16f)
    ),
    Sad(
        "Melancholy",
        PresetValues(tempo = 0.80f, pitch = -2.5f, reverb = 0.60f, delay = 0.20f, bass = 0.30f, trebleCut = 0.40f,
            rainVolume = 0.18f, vinylVolume = 0.12f)
    ),
    JazzCafe(
        "Jazz Cafe",
        PresetValues(tempo = 0.92f, pitch = -0.5f, reverb = 0.35f, delay = 0.10f, bass = 0.15f, trebleCut = 0.30f,
            vinylVolume = 0.26f, tapeVolume = 0.15f)
    ),
    MidnightDrive(
        "Midnight Drive",
        PresetValues(tempo = 0.90f, pitch = -1.5f, reverb = 0.55f, delay = 0.30f, bass = 0.35f, trebleCut = 0.35f,
            rainVolume = 0.15f, windVolume = 0.12f)
    ),
    SummerRain(
        "Summer Rain",
        PresetValues(tempo = 0.95f, pitch = -0.5f, reverb = 0.40f, delay = 0.15f, bass = 0.10f, trebleCut = 0.20f,
            rainVolume = 0.30f, windVolume = 0.12f)
    ),
    VinylWarmth(
        "Vinyl Warmth",
        PresetValues(tempo = 0.93f, pitch = -0.5f, reverb = 0.25f, delay = 0f, bass = 0.20f, trebleCut = 0.55f,
            vinylVolume = 0.28f, tapeVolume = 0.15f)
    ),
    DeepFocus(
        "Deep Focus",
        PresetValues(tempo = 0.85f, pitch = -2.0f, reverb = 0.50f, delay = 0.10f, bass = 0.10f, trebleCut = 0.30f,
            windVolume = 0.16f, rainVolume = 0.14f)
    ),
    Moonlit(
        "Moonlit",
        PresetValues(tempo = 0.90f, pitch = -1.5f, reverb = 0.65f, delay = 0.35f, bass = 0.25f, trebleCut = 0.25f,
            windVolume = 0.18f, tapeVolume = 0.13f)
    ),
    Custom(
        "Custom",
        PresetValues()
    );

    companion object {
        fun fromDisplayName(name: String): LofiPreset {
            return entries.find { it.displayName == name } ?: Custom
        }
    }
}

data class PresetValues(
    val tempo: Float = 1.0f,
    val pitch: Float = 0f,
    val reverb: Float = 0f,
    val delay: Float = 0f,
    val bass: Float = 0f,
    val trebleCut: Float = 0f,
    val rainVolume: Float = 0f,
    val vinylVolume: Float = 0f,
    val windVolume: Float = 0f,
    val tapeVolume: Float = 0f
)

/**
 * A user-created custom preset saved to the database.
 */
data class CustomPreset(
    val id: Long = 0,
    val name: String,
    val values: PresetValues,
    val createdAt: Long = System.currentTimeMillis()
)