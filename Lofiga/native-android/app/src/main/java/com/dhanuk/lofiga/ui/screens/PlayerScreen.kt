package com.dhanuk.lofiga.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import android.widget.Toast
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.model.CustomPreset
import com.dhanuk.lofiga.model.LofiPreset
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
    val isExporting by viewModel.isExporting.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val selectedCustomPresetId by viewModel.selectedCustomPresetId.collectAsState()
    val audioError by viewModel.audioEngine.error.collectAsState()
    val exportedFilePath by viewModel.exportedFilePath.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val colors = LocalAppColors.current

    var showSavePresetSheet by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(true) }
    var showAtmosphere by remember { mutableStateOf(true) }
    var showPostExportSupportPrompt by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // ── Effects used by the effect cards ─────────────────────────────────────
    // Tempo: stored 0.5..1.5 and displayed as -50%..+50%
    // Pitch: stored -5..+5 semitones and displayed as such
    val tempoPercent = (currentValues.tempo - 1f) * 100f
    val tempoForSlider = currentValues.tempo.coerceIn(0.5f, 1.5f)

    // Show audio errors
    LaunchedEffect(audioError) {
        audioError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.audioEngine.clearError()
        }
    }

    // Share exported file when export completes
    LaunchedEffect(exportedFilePath) {
        exportedFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = if (path.endsWith(".wav")) "audio/wav" else "audio/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Lofi Mix"))
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Exported to: $path")
                }
            }
            viewModel.clearExportedFilePath()
            showPostExportSupportPrompt = true
        }
    }

    // Snackbar messages from viewmodel
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        if (currentTrack == null) {
            EmptyPlayerState()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LofigaTopBar(
                    title = "Player",
                    onBack = onBack,
                    actions = {
                        var showActionsMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showActionsMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Actions", tint = colors.textPrimary)
                            }
                            DropdownMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false },
                                containerColor = colors.surface
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export") },
                                    onClick = { viewModel.exportTrack(context); showActionsMenu = false },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Config") },
                                    onClick = { viewModel.saveCurrentConfig(); showActionsMenu = false },
                                    leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save as Preset") },
                                    onClick = { showSavePresetSheet = true; showActionsMenu = false },
                                    leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                )

                // ── Scrollable content ───────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                ) {
                    TrackInfoCard(
                        title = currentTrack?.title.orEmpty(),
                        artist = currentTrack?.artist.orEmpty(),
                        onAction = { action ->
                            when (action) {
                                TrackAction.Export -> viewModel.exportTrack(context)
                                TrackAction.SaveConfig -> viewModel.saveCurrentConfig()
                                TrackAction.SavePreset -> showSavePresetSheet = true
                            }
                        },
                        albumArtUri = currentTrack?.albumArtUri
                    )

                    Spacer(Modifier.height(12.dp))

                    WaveformVisualizer(viewModel)

                    Spacer(Modifier.height(20.dp))

                    // ── Presets ──────────────────────────────────────────────
                    PresetsRow(
                        viewModel = viewModel,
                        currentPreset = currentPreset,
                        customPresets = customPresets,
                        selectedCustomPresetId = selectedCustomPresetId,
                        onSavePreset = { showSavePresetSheet = true }
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── Effects section ──────────────────────────────────────
                    ExpandableSection(
                        icon = Icons.Outlined.Tune,
                        title = "EFFECTS",
                        expanded = showEffects,
                        onToggle = { showEffects = !showEffects }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EffectCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Speed,
                                label = "Tempo",
                                value = "%+.0f%%".format(tempoPercent),
                                sliderValue = tempoForSlider,
                                onSliderChange = { viewModel.updateTempo(it) },
                                valueRange = 0.5f..1.5f
                            )
                            val pitchLabel = "%+.1f st".format(currentValues.pitch.coerceIn(-5f, 5f))
                            EffectCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Tune,
                                label = "Pitch",
                                value = pitchLabel,
                                sliderValue = currentValues.pitch.coerceIn(-5f, 5f),
                                onSliderChange = { viewModel.updatePitch(it) },
                                valueRange = -5f..5f
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EffectCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Forward30,
                                label = "Reverb",
                                value = "${(currentValues.reverb * 100).toInt()}%",
                                sliderValue = currentValues.reverb,
                                onSliderChange = { viewModel.updateReverb(it) }
                            )
                            EffectCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Timeline,
                                label = "Delay",
                                value = "${(currentValues.delay * 100).toInt()}%",
                                sliderValue = currentValues.delay,
                                onSliderChange = { viewModel.updateDelay(it) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EffectCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Equalizer,
                                label = "Bass",
                                value = "${(currentValues.bass * 100).toInt()}%",
                                sliderValue = currentValues.bass,
                                onSliderChange = { viewModel.updateBass(it) }
                            )
                            EffectCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.GraphicEq,
                                label = "Treble Cut",
                                value = "${(currentValues.trebleCut * 100).toInt()}%",
                                sliderValue = currentValues.trebleCut,
                                onSliderChange = { viewModel.updateTrebleCut(it) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Atmosphere section ───────────────────────────────────
                    ExpandableSection(
                        icon = Icons.Outlined.Cloud,
                        title = "ATMOSPHERE",
                        expanded = showAtmosphere,
                        onToggle = { showAtmosphere = !showAtmosphere }
                    ) {
                        AtmosphereSlider(volume = currentValues.rainVolume, onVolumeChange = { viewModel.updateAtmosphere("rain", it) }, label = "Rain", icon = Icons.Outlined.WaterDrop)
                        AtmosphereSlider(volume = currentValues.vinylVolume, onVolumeChange = { viewModel.updateAtmosphere("vinyl", it) }, label = "Vinyl", icon = Icons.Outlined.DiscFull)
                        AtmosphereSlider(volume = currentValues.windVolume, onVolumeChange = { viewModel.updateAtmosphere("wind", it) }, label = "Wind", icon = Icons.Outlined.Air)
                        AtmosphereSlider(volume = currentValues.tapeVolume, onVolumeChange = { viewModel.updateAtmosphere("tape", it) }, label = "Tape", icon = Icons.Outlined.FiberManualRecord)
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.exportTrack(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = RoundedCornerShape(26.dp),
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Exporting...", color = Color.White)
                        } else {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Export your mix", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // ── Fixed bottom playback controls ───────────────────────────
                PlaybackControls(viewModel)
            }

            // ── Export progress overlay ──────────────────────────────────────
            if (isExporting) {
                val exportProgress by viewModel.exportProgress.collectAsState()
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Indigo, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                            Spacer(Modifier.height(20.dp))
                            Text("Rendering Lofi Mix...", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text("${(exportProgress * 100).toInt()}% Complete", style = MaterialTheme.typography.bodyLarge, color = Indigo, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { exportProgress },
                                modifier = Modifier.fillMaxWidth(0.8f).height(6.dp),
                                color = Indigo,
                                trackColor = colors.outline
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelExport() },
                                border = BorderStroke(1.dp, colors.outline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
                            ) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        // ── Export error dialog ──────────────────────────────────────────────
        val exportError by viewModel.lastExportError.collectAsState()
        if (exportError != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearExportError() },
                containerColor = colors.surface,
                title = { Text("Export Failed", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
                text = {
                    Column {
                        Text("Please copy this error and share it with the developer:", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().background(colors.surfaceHighlight, RoundedCornerShape(8.dp)).padding(12.dp)
                        ) {
                            Text(exportError ?: "", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Lofiga export error", exportError ?: ""))
                        Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                        viewModel.clearExportError()
                    }) { Text("Copy", color = Indigo, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearExportError() }) { Text("Close", color = colors.textSecondary) }
                }
            )
        }

        // ── Post-export support prompt (rewarded ad, user-initiated) ─────────
        if (showPostExportSupportPrompt) {
            AlertDialog(
                onDismissRequest = { showPostExportSupportPrompt = false },
                containerColor = colors.surface,
                title = { Text("Mix exported!", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
                text = { Text("Enjoying Lofiga? Watch a short ad to get 15 minutes of ad-free listening.", color = colors.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        showPostExportSupportPrompt = false
                        context.findActivity()?.let { act ->
                            AdManager.showRewarded(
                                activity = act,
                                onRewarded = {
                                    AdManager.grantAdFree(15 * 60 * 1000L)
                                },
                                onDismissed = {}
                            )
                        }
                    }) { Text("Watch Ad", color = colors.accent, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showPostExportSupportPrompt = false }) { Text("No Thanks", color = colors.textSecondary) }
                }
            )
        }

        // ── Save preset bottom sheet ─────────────────────────────────────────
        if (showSavePresetSheet) {
            var presetName by remember { mutableStateOf("") }
            ModalBottomSheet(
                onDismissRequest = { showSavePresetSheet = false },
                containerColor = colors.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = Indigo, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Save Custom Preset", color = colors.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Save the current effect settings as a reusable preset", color = colors.textTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset Name") },
                        placeholder = { Text("e.g. My Chill Mix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = Indigo,
                            focusedBorderColor = Indigo,
                            unfocusedBorderColor = colors.outline,
                            focusedContainerColor = colors.surfaceHighlight,
                            unfocusedContainerColor = colors.surfaceHighlight
                        )
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
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo, disabledContainerColor = Indigo.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Preset", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Snackbar host ────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp, start = 16.dp, end = 16.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Sub-composables
// ═══════════════════════════════════════════════════════════════════════════════

private enum class TrackAction { Export, SaveConfig, SavePreset }

@Composable
private fun EmptyPlayerState() {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = colors.surfaceHighlight,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Select a song to begin", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Pick a song from the Library tab or load one from your files", color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, lineHeight = 22.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderChip(icon = Icons.Outlined.Speed, label = "Tempo")
                HeaderChip(icon = Icons.Outlined.Forward30, label = "Reverb")
                HeaderChip(icon = Icons.Outlined.Cloud, label = "Atmosphere")
            }
        }
    }
}

@Composable
private fun TrackInfoCard(
    title: String,
    artist: String,
    onAction: (TrackAction) -> Unit,
    albumArtUri: android.net.Uri? = null
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceHighlight,
            border = BorderStroke(1.dp, colors.outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (albumArtUri != null) {
                    coil3.compose.AsyncImage(
                        model = albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Icon(Icons.Outlined.Album, contentDescription = null, tint = colors.accent.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                artist.ifBlank { "Unknown Artist" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedIconButton(
                    onClick = { onAction(TrackAction.SavePreset) },
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Save preset", tint = colors.accent, modifier = Modifier.size(18.dp))
                }
                OutlinedIconButton(
                    onClick = { onAction(TrackAction.Export) },
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Export", tint = colors.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun WaveformVisualizer(viewModel: MainViewModel) {
    val colors = LocalAppColors.current
    val rawFftData by viewModel.audioEngine.fftData.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()

    // Smooth the FFT data so the bars don't jump too abruptly.
    // Persist mutable state across recompositions; read the latest raw values inside the loop
    // rather than using them as keys, otherwise the producer restarts every time fftData emits.
    val engine = viewModel.audioEngine
    val smoothed = remember { mutableStateListOf<Float>().apply { repeat(rawFftData.size.coerceAtLeast(16)) { add(0f) } } }
    LaunchedEffect(Unit) {
        while (isActive) {
            val target = engine.fftData.value
            for (i in smoothed.indices) {
                val t = target.getOrNull(i)?.coerceIn(0f, 1f) ?: 0f
                smoothed[i] = (smoothed[i] * 0.72f + t * 0.28f).coerceIn(0f, 1f)
            }
            if (!engine.isPlaying.value && smoothed.all { it < 0.02f }) {
                smoothed.replaceAll { 0f }
            }
            delay(32)
        }
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val width = size.width
        val height = size.height
        val barCount = smoothed.size.coerceAtLeast(1)
        val gapRatio = 0.35f
        val barWidth = (width / barCount) * (1f - gapRatio)
        val gap = (width / barCount) * gapRatio
        val radius = barWidth / 2f

        for (i in 0 until barCount) {
            val magnitude = smoothed.getOrNull(i) ?: 0f
            val barHeight = magnitude * height * 0.85f
            val x = i * (barWidth + gap) + gap / 2f
            val y = height - barHeight
            drawRoundRect(
                color = if (magnitude > 0.05f) colors.accent else colors.accent.copy(alpha = 0.25f),
                topLeft = Offset(x = x, y = y),
                size = Size(width = barWidth, height = barHeight.coerceAtLeast(radius * 2)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun PresetsRow(
    viewModel: MainViewModel,
    currentPreset: LofiPreset,
    customPresets: List<CustomPreset>,
    selectedCustomPresetId: Long?,
    onSavePreset: () -> Unit
) {
    val colors = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Presets", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary, fontWeight = FontWeight.SemiBold)
                if (currentPreset == LofiPreset.Custom) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = onSavePreset,
                        label = { Text("Save", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = IndigoContainer,
                            labelColor = colors.accent,
                            leadingIconContentColor = colors.accent
                        ),
                        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f))
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LofiPreset.entries.filter { it != LofiPreset.Custom }.forEach { preset ->
                FilterChip(
                    selected = currentPreset == preset,
                    onClick = { viewModel.applyPreset(preset) },
                    label = { Text(preset.displayName, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoContainer,
                        selectedLabelColor = colors.accent,
                        containerColor = colors.surface,
                        labelColor = colors.textPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.outline,
                        selectedBorderColor = colors.accent.copy(alpha = 0.3f),
                        enabled = true,
                        selected = currentPreset == preset
                    )
                )
            }
            customPresets.forEach { preset ->
                val isSelected = selectedCustomPresetId == preset.id
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.applyCustomPreset(preset) },
                    label = { Text(preset.name, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoContainer,
                        selectedLabelColor = colors.accent,
                        containerColor = colors.surface,
                        labelColor = colors.textPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.outline,
                        selectedBorderColor = colors.accent.copy(alpha = 0.3f),
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }
    }
}

@Composable
private fun AtmosphereSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colors = LocalAppColors.current
    val isActive = volume > 0.01f

    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceHighlight,
        border = BorderStroke(1.dp, if (isActive) colors.accent.copy(alpha = 0.3f) else colors.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = label, tint = if (isActive) colors.accent else colors.textTertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isActive) colors.accent else colors.textPrimary, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.weight(1f))
                Text("${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.outline)
            )
        }
    }
}

@Composable
private fun PlaybackControls(viewModel: MainViewModel) {
    val position by viewModel.audioEngine.position.collectAsState()
    val duration by viewModel.audioEngine.duration.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val isLooping by viewModel.audioEngine.isLooping.collectAsState()
    val colors = LocalAppColors.current

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }
    val sliderDisplayValue = if (isDragging) dragPosition else position.toFloat()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(formatDuration(sliderDisplayValue.toLong()), style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, modifier = Modifier.width(36.dp))
                Slider(
                    value = sliderDisplayValue,
                    onValueChange = { dragPosition = it; isDragging = true },
                    onValueChangeFinished = {
                        viewModel.audioEngine.seekTo(dragPosition.toLong())
                        isDragging = false
                    },
                    valueRange = 0f..if (duration > 0) duration.toFloat() else 1f,
                    modifier = Modifier.weight(1f).height(20.dp),
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.outline)
                )
                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, modifier = Modifier.width(36.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.audioEngine.seekTo((position - 10000).coerceAtLeast(0)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Replay10, contentDescription = "-10s", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { viewModel.previousTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = colors.textSecondary, modifier = Modifier.size(24.dp))
                }
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Indigo,
                    shadowElevation = 2.dp
                ) {
                    IconButton(onClick = { viewModel.audioEngine.togglePlayPause() }, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.nextTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = colors.textSecondary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { viewModel.audioEngine.seekTo((position + 10000).coerceAtMost(duration)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Forward10, contentDescription = "+10s", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
