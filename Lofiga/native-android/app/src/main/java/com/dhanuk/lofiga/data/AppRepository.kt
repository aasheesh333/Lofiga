package com.dhanuk.lofiga.data

import android.content.Context
import com.dhanuk.lofiga.model.CustomPreset
import com.dhanuk.lofiga.model.PresetValues
import com.dhanuk.lofiga.model.SavedConfig

/**
 * Repository that wraps Room database operations.
 */
class AppRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val configDao = db.savedConfigDao()
    private val presetDao = db.customPresetDao()

    // --- Saved Configs (Recent Edits) ---

    suspend fun getAllConfigs(): List<SavedConfig> {
        return configDao.getAll().map { entity ->
            SavedConfig(
                id = entity.id,
                fileName = entity.fileName,
                filePath = entity.filePath,
                savedAt = entity.savedAt,
                values = PresetValues(
                    tempo = entity.tempo,
                    pitch = entity.pitch,
                    reverb = entity.reverb,
                    delay = entity.delay,
                    bass = entity.bass,
                    trebleCut = entity.trebleCut,
                    rainVolume = entity.rainVolume,
                    vinylVolume = entity.vinylVolume,
                    windVolume = entity.windVolume,
                    tapeVolume = entity.tapeVolume
                )
            )
        }
    }

    suspend fun saveConfig(config: SavedConfig) {
        configDao.insert(
            SavedConfigEntity(
                id = config.id.ifEmpty { System.currentTimeMillis().toString() },
                fileName = config.fileName,
                filePath = config.filePath,
                savedAt = config.savedAt,
                tempo = config.values.tempo,
                pitch = config.values.pitch,
                reverb = config.values.reverb,
                delay = config.values.delay,
                bass = config.values.bass,
                trebleCut = config.values.trebleCut,
                rainVolume = config.values.rainVolume,
                vinylVolume = config.values.vinylVolume,
                windVolume = config.values.windVolume,
                tapeVolume = config.values.tapeVolume
            )
        )
    }

    suspend fun deleteConfig(id: String) {
        configDao.delete(id)
    }

    // --- Custom Presets ---

    suspend fun getAllCustomPresets(): List<CustomPreset> {
        return presetDao.getAll().map { entity ->
            CustomPreset(
                id = entity.id,
                name = entity.name,
                values = PresetValues(
                    tempo = entity.tempo,
                    pitch = entity.pitch,
                    reverb = entity.reverb,
                    delay = entity.delay,
                    bass = entity.bass,
                    trebleCut = entity.trebleCut,
                    rainVolume = entity.rainVolume,
                    vinylVolume = entity.vinylVolume,
                    windVolume = entity.windVolume,
                    tapeVolume = entity.tapeVolume
                ),
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun saveCustomPreset(preset: CustomPreset): Long {
        return presetDao.insert(
            CustomPresetEntity(
                name = preset.name,
                tempo = preset.values.tempo,
                pitch = preset.values.pitch,
                reverb = preset.values.reverb,
                delay = preset.values.delay,
                bass = preset.values.bass,
                trebleCut = preset.values.trebleCut,
                rainVolume = preset.values.rainVolume,
                vinylVolume = preset.values.vinylVolume,
                windVolume = preset.values.windVolume,
                tapeVolume = preset.values.tapeVolume,
                createdAt = preset.createdAt
            )
        )
    }

    suspend fun deleteCustomPreset(id: Long) {
        presetDao.delete(id)
    }
}