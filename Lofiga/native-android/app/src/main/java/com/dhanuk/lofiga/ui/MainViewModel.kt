package com.dhanuk.lofiga.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.lofiga.audio.AudioEngine
import com.dhanuk.lofiga.data.AppRepository
import com.dhanuk.lofiga.model.*
import com.dhanuk.lofiga.util.AudioQueryHelper
import com.dhanuk.lofiga.util.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val audioEngine = AudioEngine(application)
    val repository = AppRepository(application)
    val settingsManager = SettingsManager(application)

    // --- Song Library ---
    private val _allSongs = MutableStateFlow<List<AudioTrack>>(emptyList())
    val allSongs: StateFlow<List<AudioTrack>> = _allSongs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredSongs: StateFlow<List<AudioTrack>> = combine(_allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Current Session ---
    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _currentValues = MutableStateFlow(PresetValues())
    val currentValues: StateFlow<PresetValues> = _currentValues.asStateFlow()

    private val _currentPreset = MutableStateFlow(LofiPreset.LofiSlow)
    val currentPreset: StateFlow<LofiPreset> = _currentPreset.asStateFlow()

    // --- Recent Edits ---
    private val _recentEdits = MutableStateFlow<List<SavedConfig>>(emptyList())
    val recentEdits: StateFlow<List<SavedConfig>> = _recentEdits.asStateFlow()

    // --- Custom Presets ---
    private val _customPresets = MutableStateFlow<List<CustomPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomPreset>> = _customPresets.asStateFlow()

    // --- UI State ---
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            audioEngine.init()
        }
        loadSongs()
        loadRecentEdits()
        loadCustomPresets()
    }

    // --- Song Loading ---

    private fun hasAudioPermission(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun loadSongs() {
        if (!hasAudioPermission()) {
            _allSongs.value = emptyList()
            return
        }
        viewModelScope.launch {
            val songs = AudioQueryHelper.queryAllSongs(getApplication())
            _allSongs.value = songs
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Track Loading ---

    fun loadTrack(track: AudioTrack) {
        _currentTrack.value = track
        _currentTrackIndex.value = filteredSongs.value.indexOf(track)
        track.uri?.let { audioEngine.loadTrack(it) }
        applyPreset(LofiPreset.LofiSlow) // Default preset
        audioEngine.play()
    }

    fun loadTrackFromFile(filePath: String, fileName: String) {
        _currentTrack.value = AudioTrack(
            title = fileName,
            dataPath = filePath
        )
        audioEngine.loadTrackFromFile(filePath)
        applyPreset(LofiPreset.LofiSlow)

        // Try to play, show error if fails
        try {
            audioEngine.play()
        } catch (e: Exception) {
            _snackbarMessage.tryEmit("Playback error: ${e.message}")
        }
    }

    fun nextTrack() {
        val songs = filteredSongs.value
        if (songs.isEmpty()) return
        val nextIndex = (_currentTrackIndex.value + 1) % songs.size
        loadTrack(songs[nextIndex])
    }

    fun previousTrack() {
        val songs = filteredSongs.value
        if (songs.isEmpty()) return
        val prevIndex = if (_currentTrackIndex.value <= 0) songs.size - 1 else _currentTrackIndex.value - 1
        loadTrack(songs[prevIndex])
    }

    // --- Presets ---

    fun applyPreset(preset: LofiPreset) {
        _currentPreset.value = preset
        _currentValues.value = preset.values

        audioEngine.setSpeedAndPitch(preset.values.tempo, preset.values.pitch)
        audioEngine.setReverb(preset.values.reverb)
        audioEngine.setBassBoost(preset.values.bass)
        audioEngine.setTrebleCut(preset.values.trebleCut)
        audioEngine.setDelay(preset.values.delay)
        audioEngine.setAllAtmosphereVolumes(preset.values)
    }

    fun updateTempo(value: Float) {
        val newValues = _currentValues.value.copy(tempo = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        audioEngine.setSpeedAndPitch(value, newValues.pitch)
    }

    fun updatePitch(value: Float) {
        val newValues = _currentValues.value.copy(pitch = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        audioEngine.setSpeedAndPitch(newValues.tempo, value)
    }

    fun updateReverb(value: Float) {
        val newValues = _currentValues.value.copy(reverb = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        audioEngine.setReverb(value)
    }

    fun updateDelay(value: Float) {
        val newValues = _currentValues.value.copy(delay = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        audioEngine.setDelay(value)
    }

    fun updateBass(value: Float) {
        val newValues = _currentValues.value.copy(bass = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        audioEngine.setBassBoost(value)
    }

    fun updateTrebleCut(value: Float) {
        val newValues = _currentValues.value.copy(trebleCut = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        audioEngine.setTrebleCut(value)
    }

    fun updateAtmosphere(key: String, volume: Float) {
        val current = _currentValues.value
        val newValues = when (key) {
            "rain" -> current.copy(rainVolume = volume)
            "vinyl" -> current.copy(vinylVolume = volume)
            "wind" -> current.copy(windVolume = volume)
            "tape" -> current.copy(tapeVolume = volume)
            else -> current
        }
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        audioEngine.setAtmosphereVolume(key, volume)
    }

    // --- Recent Edits ---

    fun loadRecentEdits() {
        viewModelScope.launch {
            _recentEdits.value = repository.getAllConfigs()
        }
    }

    fun saveCurrentConfig() {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            repository.saveConfig(
                SavedConfig(
                    id = UUID.randomUUID().toString(),
                    fileName = track.title,
                    filePath = track.dataPath,
                    savedAt = System.currentTimeMillis(),
                    values = _currentValues.value
                )
            )
            loadRecentEdits()
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            repository.deleteConfig(id)
            loadRecentEdits()
        }
    }

    fun editConfig(config: SavedConfig) {
        _currentValues.value = config.values
        _currentPreset.value = LofiPreset.Custom
        _currentTrack.value = AudioTrack(
            title = config.fileName,
            dataPath = config.filePath
        )

        audioEngine.setSpeedAndPitch(config.values.tempo, config.values.pitch)
        audioEngine.setReverb(config.values.reverb)
        audioEngine.setBassBoost(config.values.bass)
        audioEngine.setTrebleCut(config.values.trebleCut)
        audioEngine.setDelay(config.values.delay)
        audioEngine.setAllAtmosphereVolumes(config.values)
    }

    // --- Custom Presets ---

    fun loadCustomPresets() {
        viewModelScope.launch {
            _customPresets.value = repository.getAllCustomPresets()
        }
    }

    fun saveCustomPreset(name: String) {
        viewModelScope.launch {
            repository.saveCustomPreset(
                CustomPreset(
                    name = name,
                    values = _currentValues.value
                )
            )
            loadCustomPresets()
            _snackbarMessage.tryEmit("Preset '$name' saved!")
        }
    }

    fun deleteCustomPreset(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomPreset(id)
            loadCustomPresets()
        }
    }

    fun applyCustomPreset(preset: CustomPreset) {
        _currentValues.value = preset.values
        _currentPreset.value = LofiPreset.Custom

        audioEngine.setSpeedAndPitch(preset.values.tempo, preset.values.pitch)
        audioEngine.setReverb(preset.values.reverb)
        audioEngine.setBassBoost(preset.values.bass)
        audioEngine.setTrebleCut(preset.values.trebleCut)
        audioEngine.setDelay(preset.values.delay)
        audioEngine.setAllAtmosphereVolumes(preset.values)
    }

    // --- Export ---

    fun exportTrack(context: android.content.Context) {
        val track = _currentTrack.value ?: run {
            _snackbarMessage.tryEmit("No track selected")
            return
        }

        // Check if we have either URI or file path
        val hasValidSource = track.uri != null || (track.dataPath.isNotBlank())
        if (!hasValidSource) {
            _snackbarMessage.tryEmit("Export failed: Track source not found")
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0f
            try {
                val settings = settingsManager.settingsFlow.first()
                val inputPath = if (track.dataPath.isNotBlank()) track.dataPath else null
                val result = com.dhanuk.lofiga.export.ExportService.exportTrack(
                    context = context,
                    inputUri = track.uri ?: Uri.EMPTY,
                    inputPath = inputPath,
                    fileName = track.title,
                    preset = _currentValues.value,
                    format = settings.audioFormat,
                    bitrate = settings.audioBitrate,
                    onProgress = { _exportProgress.value = it }
                )
                if (result != null) {
                    _snackbarMessage.tryEmit("Exported to: $result")
                } else {
                    _snackbarMessage.tryEmit("Export completed, but no file path returned")
                }
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Export failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun cancelExport() {
        com.dhanuk.lofiga.export.ExportService.cancelExport()
        _isExporting.value = false
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}