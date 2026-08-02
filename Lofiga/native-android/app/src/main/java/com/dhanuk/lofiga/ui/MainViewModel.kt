package com.dhanuk.lofiga.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.lofiga.audio.AudioEngine
import com.dhanuk.lofiga.data.AppRepository
import com.dhanuk.lofiga.LofigaApplication
import com.dhanuk.lofiga.media.MediaPlaybackService
import com.dhanuk.lofiga.media.MediaSessionManagerHolder
import com.dhanuk.lofiga.model.*
import com.dhanuk.lofiga.util.AudioQueryHelper
import com.dhanuk.lofiga.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) { // Updated for playback and UI fixes

    val audioEngine = AudioEngine(application)
    val repository = AppRepository(application)
    val settingsManager = SettingsManager(application)

    // --- Song Library ---
    private val _allSongs = MutableStateFlow<List<AudioTrack>>(emptyList())
    val allSongs: StateFlow<List<AudioTrack>> = _allSongs.asStateFlow()

    /** C.2: per-track mood classifications (keyed by MediaStore AudioTrack.id),
     *  populated by AudioEngine as each track's FFT precompute completes. */
    val moodTags: StateFlow<Map<Long, MoodTag>> = audioEngine.moodTags

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

    private val _selectedCustomPresetId = MutableStateFlow<Long?>(null)
    val selectedCustomPresetId: StateFlow<Long?> = _selectedCustomPresetId.asStateFlow()

    // --- Recent Edits ---
    private val _recentEdits = MutableStateFlow<List<SavedConfig>>(emptyList())
    val recentEdits: StateFlow<List<SavedConfig>> = _recentEdits.asStateFlow()

    // --- Custom Presets ---
    private val _customPresets = MutableStateFlow<List<CustomPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomPreset>> = _customPresets.asStateFlow()

    private var remixAutoSaveJob: kotlinx.coroutines.Job? = null

    // --- UI State ---
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _lastExportError = MutableStateFlow<String?>(null)
    val lastExportError: StateFlow<String?> = _lastExportError.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            audioEngine.init()
        }

        val app = getApplication<LofigaApplication>()
        val sessionManager = app.mediaSessionManager

        audioEngine.onPlaybackStateChanged = { isPlaying ->
            // Connect (or re-connect) the Media3 session to the current ExoPlayer
            // the first time playback begins, and surface the session to the
            // MediaSessionService via the process holder.
            val player = audioEngine.playerForSession
            if (player != null) {
                sessionManager.connect(player)
                MediaSessionManagerHolder.mediaSession = sessionManager.session
            }

            if (isPlaying) {
                val intent = Intent(app, MediaPlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // The media notification can't be shown if POST_NOTIFICATIONS
                    // was denied, and starting a foreground service from the
                    // background can throw — neither should crash the app.
                    try {
                        app.startForegroundService(intent)
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "startForegroundService failed: ${e.message}")
                    }
                } else {
                    app.startService(intent)
                }
            } else if (audioEngine.currentTrackTitle.isEmpty()) {
                // Nothing loaded — stop the foreground service entirely.
                val intent = Intent(app, MediaPlaybackService::class.java)
                app.stopService(intent)
                sessionManager.release()
                MediaSessionManagerHolder.mediaSession = null
            }
            // When paused with a track still loaded, leave the service running so
            // the persistent media notification stays usable.
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
            val songs = withContext(Dispatchers.IO) {
                AudioQueryHelper.queryAllSongs(getApplication())
            }
            _allSongs.value = songs
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Track Loading ---

    fun loadTrack(track: AudioTrack) {
        audioEngine.currentTrackTitle = track.title
        audioEngine.currentTrackArtist = track.artist
        audioEngine.albumArtUri = track.albumArtUri
        audioEngine.currentTrackId = track.id  // C.2: used to key the mood tag
        val success = track.uri?.let { audioEngine.loadTrack(it, autoPlay = true) } ?: false
        if (success) {
            _currentTrack.value = track
            _currentTrackIndex.value = filteredSongs.value.indexOf(track)
            applyPresetOnLoad()
        } else {
            _snackbarMessage.tryEmit(audioEngine.error.value ?: "Failed to load track")
        }
    }

    fun loadTrackFromFile(filePath: String, fileName: String): Boolean {
        audioEngine.currentTrackTitle = fileName
        audioEngine.currentTrackArtist = ""
        audioEngine.albumArtUri = null
        audioEngine.currentTrackId = 0  // file-picked tracks have no MediaStore id
        val success = audioEngine.loadTrackFromFile(filePath, autoPlay = true)
        if (success) {
            _currentTrack.value = AudioTrack(
                title = fileName,
                dataPath = filePath
            )
            // Not part of the MediaStore library list, so reset the index so
            // next/previous don't jump to an unrelated library position.
            _currentTrackIndex.value = -1
            applyPresetOnLoad()
            return true
        } else {
            _snackbarMessage.tryEmit(audioEngine.error.value ?: "Failed to load track")
            return false
        }
    }

    fun nextTrack() {
        val songs = filteredSongs.value
        if (songs.isEmpty()) return
        val currentIdx = _currentTrackIndex.value
        if (currentIdx < 0 || currentIdx >= songs.size) {
            // Current track not in filtered songs (e.g., loaded from file picker)
            loadTrack(songs[0])
        } else {
            val nextIndex = (currentIdx + 1) % songs.size
            loadTrack(songs[nextIndex])
        }
    }

    fun previousTrack() {
        val songs = filteredSongs.value
        if (songs.isEmpty()) return
        val currentIdx = _currentTrackIndex.value
        if (currentIdx < 0 || currentIdx >= songs.size) {
            // Current track not in filtered songs (e.g., loaded from file picker)
            loadTrack(songs[0])
        } else {
            val prevIndex = if (currentIdx <= 0) songs.size - 1 else currentIdx - 1
            loadTrack(songs[prevIndex])
        }
    }

    // --- Presets ---

    /**
     * Tracks whether the user has loaded at least one track this session.
     * The first load defaults to the [LofiPreset.LofiSlow] baseline so users
     * opening the app for the first time still get the signature slowed+reverb
     * vibe; subsequent loads in the same session keep whatever the user is
     * working with (C.1: preset carryover across tracks).
     */
    private var firstLoadOfSession = true

    /**
     * Pushes the live [_currentValues] into the [AudioEngine] without touching
     * the [_currentPreset] / [_selectedCustomPresetId] slots the UI shows.
     * Used by [loadTrack] / [loadTrackFromFile] to re-apply the user's tweaks
     * to a freshly-created ExoPlayer instance (loadTrack tears the player down
     * then reconstructs it, dropping PlaybackParameters + Equalizer/Reverb state).
     */
    private fun applyCurrentValuesToEngine() {
        val v = _currentValues.value
        audioEngine.setSpeedAndPitch(v.tempo, v.pitch)
        audioEngine.setReverbAndDelay(v.reverb, v.delay)
        audioEngine.setBassBoost(v.bass)
        audioEngine.setTrebleCut(v.trebleCut)
        audioEngine.setAllAtmosphereVolumes(v)
    }

    private fun applyPresetOnLoad() {
        if (firstLoadOfSession) {
            // First track of the session: classic Lofiga default, so new users
            // opening the app hear the signature slowed+reverb sound, not clean audio.
            applyPreset(LofiPreset.LofiSlow)
            firstLoadOfSession = false
        } else {
            // Subsequent tracks in the same session: keep whatever the user is
            // tweaking — no silent LofiSlow reset midsession. C.1.
            applyCurrentValuesToEngine()
        }
    }

    fun applyPreset(preset: LofiPreset) {
        _currentPreset.value = preset
        _selectedCustomPresetId.value = null
        _currentValues.value = preset.values

        audioEngine.setSpeedAndPitch(preset.values.tempo, preset.values.pitch)
        audioEngine.setReverbAndDelay(preset.values.reverb, preset.values.delay)
        audioEngine.setBassBoost(preset.values.bass)
        audioEngine.setTrebleCut(preset.values.trebleCut)
        audioEngine.setAllAtmosphereVolumes(preset.values)
    }

    fun updateTempo(value: Float) {
        val newValues = _currentValues.value.copy(tempo = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = null
        audioEngine.setSpeedAndPitch(value, newValues.pitch)
        scheduleRemixAutoSave()
    }

    fun updatePitch(value: Float) {
        val newValues = _currentValues.value.copy(pitch = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = null
        audioEngine.setSpeedAndPitch(newValues.tempo, value)
        scheduleRemixAutoSave()
    }

    fun updateReverb(value: Float) {
        val newValues = _currentValues.value.copy(reverb = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = null
        audioEngine.setReverbAndDelay(value, newValues.delay)
        scheduleRemixAutoSave()
    }

    fun updateDelay(value: Float) {
        val newValues = _currentValues.value.copy(delay = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = null
        audioEngine.setReverbAndDelay(newValues.reverb, value)
        scheduleRemixAutoSave()
    }

    fun updateBass(value: Float) {
        val newValues = _currentValues.value.copy(bass = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = null
        audioEngine.setBassBoost(value)
        scheduleRemixAutoSave()
    }

    fun updateTrebleCut(value: Float) {
        val newValues = _currentValues.value.copy(trebleCut = value)
        _currentValues.value = newValues
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = null
        audioEngine.setTrebleCut(value)
        scheduleRemixAutoSave()
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
        _selectedCustomPresetId.value = null
        audioEngine.setAtmosphereVolume(key, volume)
        scheduleRemixAutoSave()
    }

    // --- Recent Edits ---

    fun loadRecentEdits() {
        viewModelScope.launch {
            _recentEdits.value = withContext(Dispatchers.IO) {
                repository.getAllConfigs()
            }
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

    private fun scheduleRemixAutoSave() {
        val track = _currentTrack.value ?: return
        val values = _currentValues.value
        if (values == PresetValues()) return
        remixAutoSaveJob?.cancel()
        remixAutoSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            val existing = _recentEdits.value.find { it.filePath == track.dataPath }
            val configId = existing?.id ?: UUID.randomUUID().toString()
            repository.saveConfig(
                SavedConfig(
                    id = configId,
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

    fun editConfig(config: SavedConfig): Boolean {
        // First load the track audio
        if (config.filePath.isBlank()) {
            _snackbarMessage.tryEmit("Config has no file path")
            return false
        }
        val fileExists = java.io.File(config.filePath).exists()
        if (!fileExists) {
            _snackbarMessage.tryEmit("File not found: ${config.fileName}")
            return false
        }

        _currentTrack.value = AudioTrack(
            title = config.fileName,
            dataPath = config.filePath
        )
        // Loaded from a saved config (arbitrary file), not the library list —
        // reset the index so next/previous start from a sensible position.
        _currentTrackIndex.value = -1

        val success = audioEngine.loadTrackFromFile(config.filePath, autoPlay = true)
        if (!success) {
            _snackbarMessage.tryEmit(audioEngine.error.value ?: "Failed to load file")
            return false
        }

        // Now apply the saved values
        _currentValues.value = config.values
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = null
        
        audioEngine.setSpeedAndPitch(config.values.tempo, config.values.pitch)
        audioEngine.setReverbAndDelay(config.values.reverb, config.values.delay)
        audioEngine.setBassBoost(config.values.bass)
        audioEngine.setTrebleCut(config.values.trebleCut)
        audioEngine.setAllAtmosphereVolumes(config.values)
        return true
    }

    // --- Custom Presets ---

    fun loadCustomPresets() {
        viewModelScope.launch {
            _customPresets.value = withContext(Dispatchers.IO) {
                repository.getAllCustomPresets()
            }
        }
    }

    fun saveCustomPreset(name: String) {
        viewModelScope.launch {
            val newId = repository.saveCustomPreset(
                CustomPreset(
                    name = name,
                    values = _currentValues.value
                )
            )
            loadCustomPresets()
            _selectedCustomPresetId.value = newId
            _snackbarMessage.tryEmit("Preset '$name' saved!")
        }
    }

    fun deleteCustomPreset(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomPreset(id)
            loadCustomPresets()
            if (_selectedCustomPresetId.value == id) {
                _selectedCustomPresetId.value = null
            }
        }
    }

    fun applyCustomPreset(preset: CustomPreset) {
        _currentValues.value = preset.values
        _currentPreset.value = LofiPreset.Custom
        _selectedCustomPresetId.value = preset.id

        audioEngine.setSpeedAndPitch(preset.values.tempo, preset.values.pitch)
        audioEngine.setReverbAndDelay(preset.values.reverb, preset.values.delay)
        audioEngine.setBassBoost(preset.values.bass)
        audioEngine.setTrebleCut(preset.values.trebleCut)
        audioEngine.setAllAtmosphereVolumes(preset.values)
    }

    // --- Export ---

    private val _exportedFilePath = MutableStateFlow<String?>(null)
    // Flow to notify when an export completes (used to refresh export list)
    private val _exportCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exportCompleted = _exportCompleted.asSharedFlow()
    val exportedFilePath: StateFlow<String?> = _exportedFilePath.asStateFlow()

    fun exportTrack(context: android.content.Context) {
        val track = _currentTrack.value ?: run {
            _snackbarMessage.tryEmit("No track selected - select a song first")
            return
        }

        val hasValidSource = track.uri != null || (track.dataPath.isNotBlank())
        if (!hasValidSource) {
            _snackbarMessage.tryEmit("Export failed: Track source not found - reload the song")
            return
        }

        if (track.dataPath.isNotBlank() && !java.io.File(track.dataPath).exists()) {
            _snackbarMessage.tryEmit("Export failed: Audio file not found at path")
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0f
            _exportedFilePath.value = null
            _lastExportError.value = null
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
                    _exportedFilePath.value = result
                    _exportCompleted.tryEmit(Unit)
                } else {
                    _snackbarMessage.tryEmit("Export cancelled")
                }
            } catch (e: Exception) {
                val msg = "${e.javaClass.simpleName}: ${e.message ?: "(no message)"}"
                val stack = e.stackTraceToString().lineSequence()
                    .firstOrNull { it.contains("com.dhanuk.lofiga") }
                    ?: e.stackTrace.firstOrNull()?.toString()
                    ?: ""
                _lastExportError.value = "$msg\nat $stack"
                _snackbarMessage.tryEmit("Export failed: $msg")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearExportError() {
        _lastExportError.value = null
    }

    fun clearExportedFilePath() {
        _exportedFilePath.value = null
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