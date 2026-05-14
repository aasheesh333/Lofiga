package com.dhanuk.lofiga.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import com.dhanuk.lofiga.model.CustomPreset
import com.dhanuk.lofiga.model.LofiPreset
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import java.io.File

private fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
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
    val selectedCustomPresetId by viewModel.selectedCustomPresetId.collectAsState()
    val waveformData by viewModel.audioEngine.waveformData.collectAsState()
    val fftData by viewModel.audioEngine.fftData.collectAsState()
    val audioError by viewModel.audioEngine.error.collectAsState()
    val exportedFilePath by viewModel.exportedFilePath.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showSavePresetSheet by remember { mutableStateOf(false) }
    var showAllPresets by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(true) }
    var showAtmosphere by remember { mutableStateOf(false) }
    var showExportInfo by remember { mutableStateOf(false) }

    // Scroll states - properly remembered to prevent recreation
    val scrollState = rememberScrollState()
    val presetScrollState = rememberScrollState()

    // Selected effect type for menu: "all", "bass", "treble"
    var selectedEffectType by remember { mutableStateOf("all") }

    // Slider drag state - local to prevent fighting with position updates
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }
    // Derive slider value: use drag position when dragging, otherwise use engine position
    val sliderDisplayValue = if (isDragging) dragPosition else position.toFloat()

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    // Show audio errors in snackbar
    LaunchedEffect(audioError) {
        audioError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.audioEngine.clearError()
        }
    }

    // Show share dialog when export completes
    LaunchedEffect(exportedFilePath) {
        exportedFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = if (path.endsWith(".wav")) "audio/wav" else "audio/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Lofi Mix"))
                } catch (e: Exception) {
                    // FileProvider might fail, fallback to snackbar
                    snackbarHostState.showSnackbar("Exported to: $path")
                }
            }
            viewModel.clearExportedFilePath()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground(modifier = Modifier.fillMaxSize())

        if (currentTrack == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        color = Purple500.copy(alpha = 0.1f),
                        border = BorderStroke(2.dp, Purple500.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.LibraryMusic,
                                contentDescription = null,
                                tint = Purple500.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Select a song to begin",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pick a song from the Browse tab or\nload one from your files",
                        color = White38,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DarkSurfaceHighlight,
                            border = BorderStroke(1.dp, White12)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Speed, contentDescription = null, tint = Purple400, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tempo", color = White60, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DarkSurfaceHighlight,
                            border = BorderStroke(1.dp, White12)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Forward30, contentDescription = null, tint = Purple400, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reverb", color = White60, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DarkSurfaceHighlight,
                            border = BorderStroke(1.dp, White12)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Purple400, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Atmosphere", color = White60, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {

                // ========================
                // SCROLLABLE CONTENT (top, takes all space minus bottom controls)
                // ========================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    // --- Track Info Header ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album art placeholder
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
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack!!.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack!!.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = White38,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Action buttons
                        var showActionsMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showActionsMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Actions", tint = White60)
                            }
                            DropdownMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false },
                                containerColor = DarkSurface
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export", color = Color.White) },
                                    onClick = {
                                        viewModel.exportTrack(viewModel.getApplication())
                                        showActionsMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = White60) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Config", color = Color.White) },
                                    onClick = {
                                        viewModel.saveCurrentConfig()
                                        showActionsMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null, tint = White60) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save as Preset", color = Color.White) },
                                    onClick = {
                                        showSavePresetSheet = true
                                        showActionsMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = White60) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // --- Waveform Visualizer ---
                    WaveformVisualizer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        barCount = 32,
                        color = Purple500,
                        isPlaying = isPlaying,
                        waveformData = waveformData,
                        fftData = fftData
                    )

                    Spacer(Modifier.height(16.dp))

                    // --- Presets Section ---
                    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Purple400, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                SectionHeader("PRESETS")
                                // Quick save chip when custom preset is active
                                if (currentPreset == LofiPreset.Custom) {
                                    Spacer(Modifier.width(8.dp))
                                    AssistChip(
                                        onClick = { showSavePresetSheet = true },
                                        label = { Text("Save", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Cyan400.copy(alpha = 0.15f),
                                            labelColor = Cyan400,
                                            leadingIconContentColor = Cyan400
                                        ),
                                        border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.3f))
                                    )
                                }
                            }
                            TextButton(onClick = { showAllPresets = !showAllPresets }) {
                                Text(
                                    if (showAllPresets) "Less" else "All",
                                    color = Purple400,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Preset chips - horizontally scrollable row
                        Row(
                            modifier = Modifier
                                .horizontalScroll(presetScrollState)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presetsToShow = if (showAllPresets) LofiPreset.entries.filter { it != LofiPreset.Custom }
                            else LofiPreset.entries.filter { it != LofiPreset.Custom }.take(4)

                            presetsToShow.forEach { preset ->
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
                            // Custom presets - each with individual selection state
                            customPresets.forEach { preset ->
                                val isSelected = selectedCustomPresetId == preset.id
                                FilterChip(
                                    selected = isSelected,
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
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // --- Effects Section (Card-based layout) ---
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurface.copy(alpha = 0.5f)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showEffects = !showEffects }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, tint = Purple400, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "EFFECTS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (showEffects) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = White38,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (showEffects) {
                                HorizontalDivider(color = White12, thickness = 0.5.dp)
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Card-based effect grid (2 columns)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Tempo Card
                                        EffectCard(
                                            modifier = Modifier.weight(1f),
                                            icon = { Icon(Icons.Outlined.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                            label = "Tempo",
                                            value = "${(currentValues.tempo * 100).toInt()}%",
                                            sliderValue = currentValues.tempo,
                                            onSliderChange = { viewModel.updateTempo(it) }
                                        )
                                        // Pitch Card
                                        val semitones = currentValues.pitch
                                        val pitchLabel = if (semitones >= 0) "+" + "%.1f".format(semitones) + " st" else "%.1f".format(semitones) + " st"
                                        EffectCard(
                                            modifier = Modifier.weight(1f),
                                            icon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                            label = "Pitch",
                                            value = pitchLabel,
                                            sliderValue = (currentValues.pitch + 5f) / 10f,
                                            onSliderChange = { viewModel.updatePitch(it * 10f - 5f) }
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Reverb Card
                                        EffectCard(
                                            modifier = Modifier.weight(1f),
                                            icon = { Icon(Icons.Outlined.Forward30, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                            label = "Reverb",
                                            value = "${(currentValues.reverb * 100).toInt()}%",
                                            sliderValue = currentValues.reverb,
                                            onSliderChange = { viewModel.updateReverb(it) }
                                        )
                                        // Delay Card
                                        EffectCard(
                                            modifier = Modifier.weight(1f),
                                            icon = { Icon(Icons.Outlined.Timeline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                            label = "Delay",
                                            value = "${(currentValues.delay * 100).toInt()}%",
                                            sliderValue = currentValues.delay,
                                            onSliderChange = { viewModel.updateDelay(it) }
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Bass Card
                                        EffectCard(
                                            modifier = Modifier.weight(1f),
                                            icon = { Icon(Icons.Outlined.Equalizer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                            label = "Bass",
                                            value = "${(currentValues.bass * 100).toInt()}%",
                                            sliderValue = currentValues.bass,
                                            onSliderChange = { viewModel.updateBass(it) }
                                        )
                                        // Treble Card
                                        EffectCard(
                                            modifier = Modifier.weight(1f),
                                            icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                            label = "Treble Cut",
                                            value = "${(currentValues.trebleCut * 100).toInt()}%",
                                            sliderValue = currentValues.trebleCut,
                                            onSliderChange = { viewModel.updateTrebleCut(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // --- Atmosphere Section ---
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurface.copy(alpha = 0.5f)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAtmosphere = !showAtmosphere }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Purple400, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "ATMOSPHERE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (showAtmosphere) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = White38,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (showAtmosphere) {
                                HorizontalDivider(color = White12, thickness = 0.5.dp)
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Add background ambiance to your mix",
                                        color = White38,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    // Atmosphere sliders - each with proper label & icon
                                    listOf(
                                        Triple("rain", "Rain", Icons.Outlined.WaterDrop),
                                        Triple("vinyl", "Vinyl", Icons.Outlined.DiscFull),
                                        Triple("wind", "Wind", Icons.Outlined.Air),
                                        Triple("tape", "Tape", Icons.Outlined.FiberManualRecord)
                                    ).forEach { (key, label, icon) ->
                                        val volume = when (key) {
                                            "rain" -> currentValues.rainVolume
                                            "vinyl" -> currentValues.vinylVolume
                                            "wind" -> currentValues.windVolume
                                            "tape" -> currentValues.tapeVolume
                                            else -> 0f
                                        }
                                        val isActive = volume > 0.01f
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isActive) Purple500.copy(alpha = 0.1f) else DarkSurfaceHighlight,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isActive) Purple500.copy(alpha = 0.3f) else White12
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        icon,
                                                        contentDescription = label,
                                                        tint = if (isActive) Purple400 else White38,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isActive) Color.White else White60,
                                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                    Spacer(Modifier.weight(1f))
                                                    Text(
                                                        text = "${(volume * 100).toInt()}%",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isActive) Purple400 else White38
                                                    )
                                                }
                                                Slider(
                                                    value = volume,
                                                    onValueChange = { viewModel.updateAtmosphere(key, it) },
                                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Purple500,
                                                        activeTrackColor = Purple500,
                                                        inactiveTrackColor = White12
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // --- Export Button ---
                    Button(
                        onClick = { viewModel.exportTrack(viewModel.getApplication()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceHighlight
                        ) {
                            Text(
                                "Export saves your current mix with all effects applied. " +
                                "The processed file is saved to Music/Lofiga folder. " +
                                "M4A offers the best balance of quality and file size. " +
                                "After export, you can share directly.",
                                color = White60,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Bottom spacer to ensure content isn't hidden behind fixed controls
                    Spacer(Modifier.height(120.dp))
                }

                // ========================
                // FIXED BOTTOM CONTROLS (never scrolls)
                // ========================
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkSurface.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {

                        // --- Seek Bar ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatDuration(sliderDisplayValue.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = White38,
                                modifier = Modifier.width(40.dp)
                            )
                            Slider(
                                value = sliderDisplayValue,
                                onValueChange = { newValue ->
                                    dragPosition = newValue
                                    isDragging = true
                                },
                                onValueChangeFinished = {
                                    viewModel.audioEngine.seekTo(dragPosition.toLong())
                                    isDragging = false
                                },
                                valueRange = 0f..if (duration > 0) duration.toFloat() else 1f,
                                modifier = Modifier.weight(1f).height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Purple500,
                                    activeTrackColor = Purple500,
                                    inactiveTrackColor = White12
                                )
                            )
                            Text(
                                formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = White38,
                                modifier = Modifier.width(40.dp)
                            )
                        }

                        // --- Playback Controls ---
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = { viewModel.audioEngine.seekTo((position - 10000).coerceAtLeast(0)) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.Replay10, contentDescription = "-10s", tint = White60, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            // Previous
                            IconButton(
                                onClick = { viewModel.previousTrack() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = White60, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            // Play/Pause - Big button
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
                            Spacer(Modifier.width(12.dp))
                            // Next
                            IconButton(
                                onClick = { viewModel.nextTrack() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = White60, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            // Forward 10s
                            IconButton(
                                onClick = { viewModel.audioEngine.seekTo((position + 10000).coerceAtMost(duration)) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.Forward10, contentDescription = "+10s", tint = White60, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            // Loop
                            IconButton(
                                onClick = { viewModel.audioEngine.toggleLoop() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    if (isLooping) Icons.Filled.RepeatOne else Icons.Outlined.Repeat,
                                    contentDescription = "Loop",
                                    tint = if (isLooping) Purple500 else White38,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ========================
            // EXPORT PROGRESS OVERLAY
            // ========================
            if (isExporting) {
                val exportProgress by viewModel.exportProgress.collectAsState()
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Purple500,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                "Rendering Lofi Mix...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "${(exportProgress * 100).toInt()}% Complete",
                                color = Purple400,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { exportProgress },
                                modifier = Modifier.fillMaxWidth(0.8f).height(6.dp),
                                color = Purple500,
                                trackColor = White12,
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelExport() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = White60)
                            ) {
                                Text("Cancel", color = White60)
                            }
                        }
                    }
                }
            }
        }

        // ========================
        // SAVE PRESET BOTTOM SHEET
        // ========================
        if (showSavePresetSheet) {
            var presetName by remember { mutableStateOf("") }
            ModalBottomSheet(
                onDismissRequest = { showSavePresetSheet = false },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sheet handle via bottom sheet itself
                    Icon(
                        Icons.Outlined.BookmarkAdd,
                        contentDescription = null,
                        tint = Purple400,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Save Custom Preset",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Save the current effect settings as a reusable preset",
                        color = White38,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset Name", color = White38) },
                        placeholder = { Text("e.g. My Chill Mix", color = White38.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Purple500,
                            focusedBorderColor = Purple500,
                            unfocusedBorderColor = White12,
                            focusedContainerColor = DarkSurfaceHighlight,
                            unfocusedContainerColor = DarkSurfaceHighlight,
                            focusedLabelColor = Purple400,
                            unfocusedLabelColor = White38
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (presetName.isNotBlank()) {
                                viewModel.saveCustomPreset(presetName)
                                showSavePresetSheet = false
                            }
                        },
                        enabled = presetName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Purple500,
                            disabledContainerColor = Purple500.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Preset", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ========================
        // SNACKBAR HOST
        // ========================
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp, start = 16.dp, end = 16.dp)
        )
    }
}
