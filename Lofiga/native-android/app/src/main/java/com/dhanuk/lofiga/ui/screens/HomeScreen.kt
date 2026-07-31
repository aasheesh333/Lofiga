package com.dhanuk.lofiga.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dhanuk.lofiga.model.AudioTrack
import com.dhanuk.lofiga.model.SavedConfig
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onSongSelected: (AudioTrack) -> Unit,
    onEditConfig: (SavedConfig) -> Unit,
    onBrowseAll: () -> Unit,
    onMixSelected: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val allSongs by viewModel.allSongs.collectAsState()
    val recentEdits by viewModel.recentEdits.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    // ── Exported mixes (moved from old LibraryScreen) ─────────────────────────
    var exportedFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        exportedFiles = loadExportedFiles(context)
    }
    LaunchedEffect(viewModel) {
        viewModel.exportCompleted.collect {
            exportedFiles = loadExportedFiles(context)
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadSongs()
            exportedFiles = loadExportedFiles(context)
            kotlinx.coroutines.delay(600)
            isRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── Top bar ────────────────────────────────────────────────────────
            item {
                Text(
                    "Lofiga",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            // ── Hero CTA card ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            "Transform your sound",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Slow down, add reverb, and create your perfect lofi mix.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onBrowseAll,
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Mix", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Recent Tracks ──────────────────────────────────────────────────
            if (allSongs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader("Recent Tracks")
                        TextButton(onClick = onBrowseAll) {
                            Text("See all", color = Indigo, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allSongs.take(8), key = { it.id }) { song ->
                            CompactTrackCard(
                                title = song.title,
                                artist = song.artist,
                                isPlaying = currentTrack?.dataPath == song.dataPath,
                                onClick = { onSongSelected(song) }
                            )
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.LibraryMusic,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No music found", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("Add music files to your device", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadSongs() },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Scan Music")
                            }
                        }
                    }
                }
            }

            // ── Recent Mixes ───────────────────────────────────────────────────
            if (exportedFiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Recent Mixes",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(exportedFiles.take(3), key = { it.absolutePath }) { file ->
                    MixItem(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        onPlay = { onMixSelected(file.absolutePath, file.name) },
                        context = context
                    )
                }
            }

            // ── Recent Edits ───────────────────────────────────────────────────
            if (recentEdits.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Recent Edits",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(recentEdits.take(3), key = { it.id }) { edit ->
                    RecentEditItem(
                        edit = edit,
                        onClick = { onEditConfig(edit) },
                        onDelete = { viewModel.deleteConfig(edit.id) }
                    )
                }
            }
        }
    }
}

// ── Helper: load exported mix files ────────────────────────────────────────────
private suspend fun loadExportedFiles(context: android.content.Context): List<File> {
    return withContext(Dispatchers.IO) {
        val fileList = mutableListOf<File>()
        val musDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val dirs = mutableListOf<File>()
        if (musDir != null) dirs.add(File(musDir, "Lofiga"))
        try {
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lofiga"))
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lofiga"))
        } catch (_: Exception) {}
        dirs.forEach { dir ->
            if (dir.exists()) {
                fileList.addAll(
                    dir.listFiles { f -> f.extension in listOf("wav", "m4a", "aac") }
                        ?.sortedByDescending { it.lastModified() }
                        ?: emptyList()
                )
            }
        }
        fileList.distinctBy { it.absolutePath }
    }
}

// ── Compact track card for horizontal scroll ───────────────────────────────────
@Composable
private fun CompactTrackCard(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPlaying) IndigoContainer else colors.surfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Outlined.Equalizer else Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = if (isPlaying) Indigo else colors.textTertiary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPlaying) Indigo else colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
        Text(
            artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary
        )
    }
}

// ── Mix item row ───────────────────────────────────────────────────────────────
@Composable
private fun MixItem(
    fileName: String,
    filePath: String,
    onPlay: () -> Unit,
    context: android.content.Context
) {
    val colors = LocalAppColors.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = "Delete Mix",
            message = "Delete \"$fileName\"? This cannot be undone.",
            onConfirm = {
                File(filePath).delete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IndigoContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Indigo, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text("Exported mix", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = colors.textTertiary)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = colors.surface
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            try {
                                val file = File(filePath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share track"))
                                }
                            } catch (_: Exception) {}
                        },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFBA1A1A)) },
                        onClick = {
                            showDeleteConfirm = true
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFBA1A1A), modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
    }
}

// ── Recent edit row ────────────────────────────────────────────────────────────
@Composable
private fun RecentEditItem(
    edit: SavedConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    edit.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text("Saved recently", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
    }
}
