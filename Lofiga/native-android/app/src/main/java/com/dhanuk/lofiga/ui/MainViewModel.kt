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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) { // Updated for playback and UI fixes

    val audioEngine = AudioEngine(application)
    val repository = AppRepository(application)
    val settingsManager = SettingsManager(application)
    private val sessionManager = getApplication<LofigaApplication>().mediaSessionManager

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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        sessionManager.onNextTrack = { nextTrack() }
        sessionManager.onPreviousTrack = { previousTrack() }

        // Queue-mode navigation (notification/SystemUI next/prev, auto-advance):
        // keep the ViewModel's current-track state in sync with the player.
        audioEngine.queueFftPathResolver = { idx -> filteredSongs.value.getOrNull(idx)?.dataPath }
        audioEngine.onQueueMediaItemChanged = { idx ->
            val songs = filteredSongs.value
            if (idx in songs.indices) {
                val song = songs[idx]
                // Keep AudioEngine's track metadata in sync so the mood tag
                // (keyed on currentTrackId) lands on the right song and the
                // media notification shows the current item.
                audioEngine.currentTrackTitle = song.title
                audioEngine.currentTrackArtist = song.artist
                audioEngine.albumArtUri = song.albumArtUri
                audioEngine.currentTrackId = song.id
                _currentTrack.value = song
                pushSessionArtwork(song)
                _currentTrackIndex.value = idx
                applyCurrentValuesToEngine()
            }
        }

        audioEngine.onPlaybackStateChanged = { isPlaying ->
            // Bind the Media3 session to the (single, reused) ExoPlayer exactly
            // once — rebuilding the session on every state change releases the
            // controller the MediaSessionService's notification manager holds,
            // which makes the media notification's controls vanish.
            val player = audioEngine.playerForSession
            if (player != null && (sessionManager.session == null || sessionManager.session?.player !== player)) {
                sessionManager.connect(player)
                MediaSessionManagerHolder.mediaSession = sessionManager.session
            }

            if (isPlaying) {
                if (!MediaPlaybackService.isRunning) {
                    // Start the foreground service only once per service
                    // lifetime. Re-issuing it on every resume re-runs
                    // MediaPlaybackService.onStartCommand, whose placeholder
                    // (notification id 1001) clobbers the rich controls-bearing
                    // notification the Media3 notification manager posts on the
                    // same id — the controls disappear after the first
                    // play/pause cycle.
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
                }
            } else if (audioEngine.currentTrackTitle.isEmpty()) {
                // Nothing loaded — stop the foreground service entirely. The
                // session itself stays alive for the app's lifetime so the
                // notification re-binds cleanly on the next startService.
                if (MediaPlaybackService.isRunning) {
                    val intent = Intent(app, MediaPlaybackService::class.java)
                    app.stopService(intent)
                }
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

        // When the track belongs to the library, load the whole filtered list
        // as the player's queue. With real previous/next items the player
        // exposes COMMAND_SEEK_TO_NEXT/PREVIOUS, so the media notification
        // (and Android 13+ SystemUI) next/back controls are enabled and work
        // directly on the player.
        val songs = filteredSongs.value
        val success = if (songs.isNotEmpty() && track.uri != null) {
            val items = songs.mapNotNull { song ->
                val uri = song.uri ?: return@mapNotNull null
                androidx.media3.common.MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(song.albumArtUri)
                            .build()
                    )
                    .build()
            }
            if (items.isEmpty()) {
                track.uri?.let { audioEngine.loadTrack(it, autoPlay = true) } ?: false
            } else {
                val idx = songs.indexOfFirst { it.id == track.id && track.id != 0L }
                audioEngine.loadQueue(items, if (idx >= 0) idx else 0, autoPlay = true)
            }
        } else {
            track.uri?.let { audioEngine.loadTrack(it, autoPlay = true) } ?: false
        }
        if (success) {
            _currentTrack.value = track
            pushSessionArtwork(track)
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
            val track = AudioTrack(
                title = fileName,
                dataPath = filePath
            )
            _currentTrack.value = track
            pushSessionArtwork(track)
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

    // Push the current track's embedded album art to the Media3 session so
    // the media notification / Android 13+ SystemUI cover updates with the
    // song. The library scan never populates albumArtUri (AudioQueryHelper),
    // so MediaItems carry no artwork for the notification; the in-app UI
    // shows covers via Coil's AlbumArtFetcher, but the notification only
    // reads the session's player metadata. MediaMetadataRetriever here is the
    // same source the Coil fetcher uses. media3 1.5.1 has no
    // session.setMediaMetadata, so we rewrite the CURRENT MediaItem's
    // metadata in the player queue via setMediaItems — ExoPlayer keeps
    // playing seamlessly when the current item is unchanged (same mediaId).
    // Generation-guarded: a slow extraction from an earlier track must not
    // overwrite the current one's art.
    private var sessionArtworkGeneration = 0L

    private fun pushSessionArtwork(track: AudioTrack) {
        val myGen = ++sessionArtworkGeneration
        val path = track.dataPath
        if (path.isBlank()) return
        viewModelScope.launch {
            val artwork = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        retriever.embeddedPicture
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                if (myGen != sessionArtworkGeneration) return@withContext  // superseded by a newer track
                val player = audioEngine.playerForSession ?: return@withContext
                val idx = player.currentMediaItemIndex
                val current = player.currentMediaItem ?: return@withContext
                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .apply {
                        if (artwork != null) {
                            setArtworkData(
                                artwork,
                                androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER
                            )
                        }
                    }
                    .build()
                runCatching {
                    // replaceMediaItems swaps only this entry; the copy keeps the
                    // same mediaId/uri, so ExoPlayer continues playback seamlessly.
                    val updated = current.buildUpon().setMediaMetadata(metadata).build()
                    player.replaceMediaItems(idx, idx + 1, listOf(updated))
                }
            }
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

        val track = AudioTrack(
            title = config.fileName,
            dataPath = config.filePath
        )
        _currentTrack.value = track
        pushSessionArtwork(track)
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

    // Hard ceiling for a single export. Streaming export is memory-flat and
    // normally finishes in seconds-to-a-couple-minutes; anything beyond this
    // means the encoder/muxer pipeline stalled, so the overlay is released
    // instead of hanging at ~95% forever.
    private val EXPORT_TIMEOUT_MS = 10 * 60 * 1000L

    private val _exportedFilePath = MutableStateFlow<String?>(null)
    // Flow to notify when an export completes (used to refresh export list)
    private val _exportCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exportCompleted = _exportCompleted.asSharedFlow()
    val exportedFilePath: StateFlow<String?> = _exportedFilePath.asStateFlow()

    fun exportTrack(context: android.content.Context) {
        // Re-entry guard: a second export (PlayerScreen has three export
        // entry points) would race the first one writing the SAME output file,
        // corrupting the audio and flickering the progress bar between the two
        // jobs' values.
        if (_isExporting.value) {
            _snackbarMessage.tryEmit("Export already in progress")
            return
        }

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
                // Hard ceiling so the export overlay can never hang forever: a
                // stalled encoder/muxer previously left the progress dialog
                // stuck at ~95% "Rendering Lofi Mix..." indefinitely.
                val result = withTimeout(EXPORT_TIMEOUT_MS) {
                    com.dhanuk.lofiga.export.ExportService.exportTrack(
                        context = context,
                        inputUri = track.uri ?: Uri.EMPTY,
                        inputPath = inputPath,
                        fileName = track.title,
                        preset = _currentValues.value,
                        format = settings.audioFormat,
                        bitrate = settings.audioBitrate,
                        onProgress = { _exportProgress.value = it }
                    )
                }
                if (result != null) {
                    _exportedFilePath.value = result
                    _exportCompleted.tryEmit(Unit)
                } else {
                    _snackbarMessage.tryEmit("Export cancelled")
                }
            } catch (e: TimeoutCancellationException) {
                // Cancel the still-running pipeline so it deletes the partial
                // file instead of leaving a corrupt export behind.
                com.dhanuk.lofiga.export.ExportService.cancelExport()
                _lastExportError.value = "Export timed out after ${EXPORT_TIMEOUT_MS / 60000} minutes\nat withTimeout (MainViewModel.exportTrack)"
                _snackbarMessage.tryEmit("Export timed out - the file may be incomplete")
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
                // Reset so a failed/stalled export can't leave a stale
                // percentage behind for the next attempt.
                _exportProgress.value = 0f
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
        sessionManager.release()
        MediaSessionManagerHolder.mediaSession = null
        audioEngine.release()
    }
}