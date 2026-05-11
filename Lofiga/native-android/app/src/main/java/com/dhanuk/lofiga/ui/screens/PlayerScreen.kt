package com.dhanuk.lofiga.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.dhanuk.lofiga.model.CustomPreset
import com.dhanuk.lofiga.model.LofiPreset
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentValues by viewModel.currentValues.collectAsState()
    val currentPreset by viewModel.currentPreset.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val position by viewModel.audioEngine.position.collectAsState()
    val duration by viewModel.audioEngine.duration.collectAsState()
    val isLooping by viewModel.audioEngine.isLooping.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val waveformData by viewModel.audioEngine.waveformData.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showAllPresets by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(true) }
    var showAtmosphere by remember { mutableStateOf(false) }
    var showExportInfo by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        if (currentTrack == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VinylRecord(size = 160, isPlaying = false)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Select a song to begin",
                        color = White60,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pick a song from the Songs tab",
                        color = White38,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                // Track Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Purple500.copy(alpha = 0.3f), Purple500.copy(alpha = 0.1f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = Purple500,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack!!.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isPlaying) "Now Playing" else "Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaying) Purple400 else White38
                        )
                    }
                    // First Menu: Export, Preset, Save
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "Export Menu", tint = White60)
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                            containerColor = DarkSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export", color = Color.White) },
                                onClick = {
                                    viewModel.exportTrack(viewModel.getApplication())
                                    showExportMenu = false
                                },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = White60) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Config", color = Color.White) },
                                onClick = {
                                    viewModel.saveCurrentConfig()
                                    showExportMenu = false
                                },
                                leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null, tint = White60) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save as Preset", color = Color.White) },
                                onClick = {
                                    showSavePresetDialog = true
                                    showExportMenu = false
                                },
                                leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = White60) }
                            )
                        }
                    }
                    // Second Menu: More Effects
                    var showEffectsMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showEffectsMenu = true }) {
                            Icon(Icons.Outlined.Tune, contentDescription = "More Effects", tint = White60)
                        }
                        DropdownMenu(
                            expanded = showEffectsMenu,
                            onDismissRequest = { showEffectsMenu = false },
                            containerColor = DarkSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Bass Boost", color = Color.White) },
                                onClick = { showEffects = true; showEffectsMenu = false },
                                leadingIcon = { Icon(Icons.Outlined.Equalizer, contentDescription = null, tint = White60) }
                            )
                            DropdownMenuItem(
                                text = { Text("Treble Cut", color = Color.White) },
                                onClick = { showEffects = true; showEffectsMenu = false },
                                leadingIcon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = White60) }
                            )
                            DropdownMenuItem(
                                text = { Text("Atmosphere", color = Color.White) },
                                onClick = { showAtmosphere = true; showEffectsMenu = false },
                                leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null, tint = White60) }
                            )
                        }
                    }
                }

                // Static Album Art (no waveform)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Purple500.copy(alpha = 0.4f), Purple500.copy(alpha = 0.1f), Cyan400.copy(alpha = 0.2f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Purple400,
                        modifier = Modifier.size(48.dp)
                    )
                }

var sliderPosition by remember { mutableStateOf(position.toFloat()) }
var isDragging by remember { mutableStateOf(false) }
Column(modifier = Modifier.padding(horizontal = 24.dp)) {
    Slider(
        value = sliderPosition,
        onValueChange = { 
            sliderPosition = it 
            isDragging = true
        },
        onValueChangeFinished = {
            viewModel.audioEngine.seekTo(sliderPosition.toLong())
            isDragging = false
        },
        valueRange = 0f..if (duration > 0) duration.toFloat() else 1f,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Purple500,
            inactiveTrackColor = White12
        )
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatDuration(if (isDragging) sliderPosition.toLong() else position), style = MaterialTheme.typography.bodySmall, color = White38)
        Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = White38)
    }
}

LaunchedEffect(position, isDragging) {
    if (!isDragging) {
        sliderPosition = position.toFloat()
    }
}

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.audioEngine.seekTo((position - 10000).coerceAtLeast(0)) }) {
                        Icon(Icons.Filled.Replay10, contentDescription = "-10s", tint = White60, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.previousTrack() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = White60, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = Purple500
                    ) {
                        IconButton(
                            onClick = { viewModel.audioEngine.togglePlayPause() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { viewModel.nextTrack() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = White60, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.audioEngine.seekTo((position + 10000).coerceAtMost(duration)) }) {
                        Icon(Icons.Filled.Forward10, contentDescription = "+10s", tint = White60, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.audioEngine.toggleLoop() }) {
                        Icon(
                            if (isLooping) Icons.Filled.RepeatOne else Icons.Outlined.Repeat,
                            contentDescription = "Loop",
                            tint = if (isLooping) Purple500 else White38,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Presets
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Purple400, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            SectionHeader("PRESETS")
                        }
                        TextButton(onClick = { showAllPresets = !showAllPresets }) {
                            Text(
                                if (showAllPresets) "Less" else "All",
                                color = Purple400,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presetsToShow = if (showAllPresets) LofiPreset.entries.toList()
                        else LofiPreset.entries.take(4)

                        items(presetsToShow) { preset ->
                            if (preset != LofiPreset.Custom) {
                                FilterChip(
                                    selected = currentPreset == preset,
                                    onClick = { viewModel.applyPreset(preset) },
                                    label = { Text(preset.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Purple500.copy(alpha = 0.2f),
                                        containerColor = DarkSurfaceHighlight,
                                        selectedLabelColor = Purple400,
                                        labelColor = White60
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = White12,
                                        selectedBorderColor = Purple500.copy(alpha = 0.5f),
                                        enabled = true,
                                        selected = currentPreset == preset
                                    )
                                )
                            }
                        }
                        items(customPresets) { preset ->
                            FilterChip(
                                selected = currentPreset == LofiPreset.Custom,
                                onClick = { viewModel.applyCustomPreset(preset) },
                                label = { Text(preset.name, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Cyan400.copy(alpha = 0.15f),
                                    containerColor = DarkSurfaceHighlight,
                                    selectedLabelColor = Cyan400,
                                    labelColor = White60
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = White12,
                                    selectedBorderColor = Cyan400.copy(alpha = 0.5f),
                                    enabled = true,
                                    selected = currentPreset == LofiPreset.Custom
                                )
                            )
                        }
                    }
                }

                // Effects Section (collapsible)
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showEffects = !showEffects },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = Purple400, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        SectionHeader("EFFECTS", modifier = Modifier.weight(1f))
                        Icon(
                            if (showEffects) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = White38,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (showEffects) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        EffectSlider(
                            value = currentValues.tempo,
                            onValueChange = { viewModel.updateTempo(it) },
                            label = "Tempo",
                            displayValue = "${(currentValues.tempo * 100).toInt()}%",
                            description = "Playback speed",
                            icon = { Icon(Icons.Outlined.Speed, contentDescription = null, tint = Purple400, modifier = Modifier.size(20.dp)) }
                        )
                        val semitones = currentValues.pitch
                        val pitchLabel = if (semitones >= 0) "+${"%.1f".format(semitones)} st" else "${"%.1f".format(semitones)} st"
                        EffectSlider(
                            value = (currentValues.pitch + 5f) / 10f,
                            onValueChange = { viewModel.updatePitch(it * 10f - 5f) },
                            label = "Pitch",
                            displayValue = pitchLabel,
                            description = "Shift pitch (semitones)",
                            icon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = Purple400, modifier = Modifier.size(20.dp)) }
                        )
                        EffectSlider(
                            value = currentValues.reverb,
                            onValueChange = { viewModel.updateReverb(it) },
                            label = "Reverb",
                            displayValue = "${(currentValues.reverb * 100).toInt()}%",
                            description = "Room echo effect",
                            icon = { Icon(Icons.Outlined.Forward30, contentDescription = null, tint = Purple400, modifier = Modifier.size(20.dp)) }
                        )
                        EffectSlider(
                            value = currentValues.delay,
                            onValueChange = { viewModel.updateDelay(it) },
                            label = "Delay",
                            displayValue = "${(currentValues.delay * 100).toInt()}%",
                            description = "Echo repetition",
                            icon = { Icon(Icons.Outlined.Timeline, contentDescription = null, tint = Purple400, modifier = Modifier.size(20.dp)) }
                        )
                        EffectSlider(
                            value = currentValues.bass,
                            onValueChange = { viewModel.updateBass(it) },
                            label = "Bass Boost",
                            displayValue = "${(currentValues.bass * 100).toInt()}%",
                            description = "Low frequency boost",
                            icon = { Icon(Icons.Outlined.Equalizer, contentDescription = null, tint = Purple400, modifier = Modifier.size(20.dp)) }
                        )
                        EffectSlider(
                            value = currentValues.trebleCut,
                            onValueChange = { viewModel.updateTrebleCut(it) },
                            label = "Treble Cut",
                            displayValue = "${(currentValues.trebleCut * 100).toInt()}%",
                            description = "High frequency filter",
                            icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Purple400, modifier = Modifier.size(20.dp)) }
                        )
                    }
                }

                // Atmosphere Section (collapsible, hidden by default)
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showAtmosphere = !showAtmosphere },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Purple400, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        SectionHeader("ATMOSPHERE", modifier = Modifier.weight(1f))
                        Icon(
                            if (showAtmosphere) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = White38,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (showAtmosphere) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Add background ambiance to your mix",
                            color = White38,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val atmosphereItems = listOf(
                                "rain" to Icons.Outlined.WaterDrop,
                                "vinyl" to Icons.Outlined.DiscFull,
                                "wind" to Icons.Outlined.Air,
                                "tape" to Icons.Outlined.FiberManualRecord
                            )
                            atmosphereItems.forEach { (key, icon) ->
                                val volume = when (key) {
                                    "rain" -> currentValues.rainVolume
                                    "vinyl" -> currentValues.vinylVolume
                                    "wind" -> currentValues.windVolume
                                    "tape" -> currentValues.tapeVolume
                                    else -> 0f
                                }
                                AtmosphereControl(
                                    label = key.replaceFirstChar { it.uppercase() },
                                    icon = icon,
                                    volume = volume,
                                    onVolumeChange = { viewModel.updateAtmosphere(key, it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Export Button
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.exportTrack(viewModel.getApplication()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Purple500,
                        disabledContainerColor = Purple500.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Exporting...", color = Color.White)
                    } else {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Lofi Mix", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = { showExportInfo = !showExportInfo }) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = White38, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("How export works", color = White38, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (showExportInfo) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceHighlight
                    ) {
                        Text(
                            "Export saves your current mix with all effects applied. " +
                            "The processed file is saved to Music/Lofiga folder. " +
                            "M4A offers the best balance of quality and file size.",
                            color = White60,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // Export overlay
            if (isExporting) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Purple500)
                        Spacer(Modifier.height(16.dp))
                        Text("Rendering Lofi Mix...", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("This happens offline on your device.", color = White38, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = { viewModel.cancelExport() }) {
                            Text("Cancel", color = White60)
                        }
                    }
                }
            }
        }

        if (showSavePresetDialog) {
            var presetName by remember { mutableStateOf("") }
            AlertDialog(
                containerColor = DarkSurface,
                onDismissRequest = { showSavePresetDialog = false },
                title = { Text("Save Custom Preset", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        placeholder = { Text("My Preset", color = White38) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Purple500,
                            focusedBorderColor = Purple500,
                            unfocusedBorderColor = White12,
                            focusedContainerColor = DarkSurfaceHighlight,
                            unfocusedContainerColor = DarkSurfaceHighlight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (presetName.isNotBlank()) {
                                viewModel.saveCustomPreset(presetName)
                                showSavePresetDialog = false
                            }
                        },
                        enabled = presetName.isNotBlank()
                    ) {
                        Text("Save", color = Purple500)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSavePresetDialog = false }) {
                        Text("Cancel", color = White38)
                    }
                }
            )
        }
    }
}

@Composable
private fun AtmosphereControl(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = volume > 0.01f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Purple500.copy(alpha = 0.15f) else DarkSurfaceHighlight,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isActive) Purple500.copy(alpha = 0.3f) else White12,
                    if (isActive) Purple500.copy(alpha = 0.1f) else White12
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) Purple400 else White38,
                modifier = Modifier.size(20.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) Purple400 else White38
            )
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.width(50.dp).height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Purple500,
                    activeTrackColor = Purple500,
                    inactiveTrackColor = White12
                )
            )
        }
    }

    SnackbarHost(hostState = snackbarHostState)

private fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}