package com.dhanuk.lofiga.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import java.io.File

/**
 * Library screen showing exported lofi mixes on the device.
 */
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onFileSelected: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current
    var exportedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Look for exported files in Music/Lofiga or Downloads/Lofiga
        val files = mutableListOf<File>()
        val dirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lofiga"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lofiga"),
            appContext.cacheDir
        )
        dirs.forEach { dir ->
            if (dir.exists()) {
                files.addAll(
                    dir.listFiles { f -> f.extension in listOf("wav", "m4a", "aac") }
                        ?.sortedByDescending { it.lastModified() }
                        ?: emptyList()
                )
            }
        }
        exportedFiles = files.distinctBy { it.absolutePath }
        isLoading = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground()

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple500)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = "Your Mixes",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                }

                if (exportedFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.Album,
                                    contentDescription = null,
                                    tint = White38,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No generated songs yet",
                                    color = White38,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Export a track to see it here",
                                    color = White38.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    items(exportedFiles) { file ->
                        LibraryItem(
                            fileName = file.name,
                            filePath = file.absolutePath,
                            onPlay = { onFileSelected(file.absolutePath, file.name) },
                            onDelete = {
                                file.delete()
                                exportedFiles = exportedFiles - file
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(
    fileName: String,
    filePath: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = "Delete Mix",
            message = "Are you sure you want to delete \"$fileName\"? This action cannot be undone.",
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceHighlight
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = "Exported Lofi Track",
                    style = MaterialTheme.typography.bodySmall,
                    color = White38
                )
            }
            Row {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = "Play", tint = Purple500)
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = White38)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = DarkSurface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share", color = Color.White) },
                            onClick = {
                                showMenu = false
                                val shareFile = File(filePath)
                                if (shareFile.exists()) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share track"))
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, tint = White60) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFFF5252)) },
                            onClick = {
                                showDeleteConfirm = true
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF5252)) }
                        )
                    }
                }
            }
        }
    }
}