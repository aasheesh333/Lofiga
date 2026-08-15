package com.dhanuk.lofiga.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.dhanuk.lofiga.R
import com.dhanuk.lofiga.coil.AlbumArtKey
import com.dhanuk.lofiga.model.AudioTrack
import com.dhanuk.lofiga.model.LibraryState
import com.dhanuk.lofiga.model.SavedConfig
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val allSongs by viewModel.allSongs.collectAsState()
    val recentEdits by viewModel.recentEdits.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
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
        Column(modifier = Modifier.fillMaxSize()) {
            LofigaTopBar(title = "Lofiga")

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {

            // ── Hero CTA card ──────────────────────────────────────────────────
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = IndigoContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Indigo, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Create your lofi mix",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Slow + reverb for any song",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onBrowseAll,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Mix", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // ── Recent Tracks ──────────────────────────────────────────────────
            if (allSongs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recent Tracks",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        action = {
                            TextButton(onClick = onBrowseAll, contentPadding = PaddingValues(0.dp)) {
                                Text("See all", color = Indigo, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
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
                                onClick = { onSongSelected(song) },
                                albumArtUri = song.albumArtUri,
                                dataPath = song.dataPath,
                                audioUri = song.uri
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
                        when (libraryState) {
                            LibraryState.LOADING -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Indigo)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Scanning your music…", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                                }
                            }
                            LibraryState.PERMISSION_DENIED -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = colors.textTertiary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text("Music access needed", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Grant access to your audio files to find your music", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = onRequestPermission,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Indigo)
                                    ) {
                                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Grant Access")
                                    }
                                }
                            }
                            else -> {
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
                        onDeleted = { exportedFiles = exportedFiles.filterNot { it.absolutePath == file.absolutePath } },
                        context = context
                    )
                }
            }

            // ── Recent Edits ───────────────────────────────────────────────────
            if (recentEdits.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Your Remixes",
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
    onClick: () -> Unit,
    albumArtUri: android.net.Uri? = null,
    dataPath: String? = null,
    audioUri: android.net.Uri? = null
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPlaying) IndigoContainer else colors.surfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                Icon(
                    Icons.Outlined.Equalizer,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(context)
                        .data(AlbumArtKey(dataPath = dataPath, audioUri = audioUri ?: albumArtUri))
                        .build(),
                    placeholder = painterResource(R.drawable.ic_default_album_art),
                    error = painterResource(R.drawable.ic_default_album_art),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPlaying) Indigo else colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
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
    onDeleted: () -> Unit,
    context: android.content.Context
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = "Delete Mix",
            message = "Delete \"$fileName\"? This cannot be undone.",
            onConfirm = {
                showDeleteConfirm = false
                // File I/O off the main thread; drop from the list on success.
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        try { File(filePath).delete() } catch (_: Exception) { false }
                    }
                    if (deleted) onDeleted()
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    try {
                                        val file = File(filePath)
                                        if (file.exists())
                                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        else null
                                    } catch (_: Exception) { null }
                                }
                                if (uri != null) {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share track"))
                                    } catch (_: Exception) {}
                                }
                            }
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
        }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
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
    }
}
