package com.dhanuk.lofiga.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
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
        val files = mutableListOf<File>()
        val musDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val dirs = mutableListOf<File>()
        if (musDir != null) {
            dirs.add(File(musDir, "Lofiga"))
        }
        try {
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lofiga"))
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lofiga"))
        } catch (_: Exception) {
        }
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

    LaunchedEffect(viewModel) {
        viewModel.exportCompleted.collect {
            val files = mutableListOf<File>()
            val musDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val dirs = mutableListOf<File>()
            if (musDir != null) {
                dirs.add(File(musDir, "Lofiga"))
            }
            try {
                dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lofiga"))
                dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lofiga"))
            } catch (_: Exception) {
            }
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
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground()

        Column(modifier = Modifier.fillMaxSize()) {
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
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }

                    if (exportedFiles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                        "No generated songs yet",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Export a track to see it here",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "Exported Lofi Track",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = "Play", tint = Purple500)
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                try {
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
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Could not share file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
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
