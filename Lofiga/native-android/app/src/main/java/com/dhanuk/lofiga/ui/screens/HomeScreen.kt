package com.dhanuk.lofiga.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhanuk.lofiga.model.AudioTrack
import com.dhanuk.lofiga.model.SavedConfig
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*

enum class SortOption(val label: String) {
    DATE_ADDED("Date Added"),
    NAME("Name"),
    DURATION("Duration"),
    SIZE("Size"),
    ARTIST("Artist")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onSongSelected: (String, String) -> Unit,
    onEditConfig: (SavedConfig) -> Unit
) {
    val recentEdits by viewModel.recentEdits.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    var sortOption by remember { mutableStateOf(SortOption.DATE_ADDED) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadSongs()
            // Small delay to show the refresh indicator briefly
            kotlinx.coroutines.delay(600)
            isRefreshing = false
        }
    }

    val sortedSongs = remember(filteredSongs, sortOption) {
        when (sortOption) {
            SortOption.DATE_ADDED -> filteredSongs.sortedByDescending { it.dateAdded }
            SortOption.NAME -> filteredSongs.sortedBy { it.title.lowercase() }
            SortOption.DURATION -> filteredSongs.sortedBy { it.durationMs }
            SortOption.SIZE -> filteredSongs.sortedByDescending { it.fileSize }
            SortOption.ARTIST -> filteredSongs.sortedBy { it.artist.lowercase() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 80.dp)
            ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Lofiga",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                        )
                        Text(
                            text = "Turn Any Song Into Lofi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = White38,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search songs...", color = White38) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = White38)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = White38)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Purple500,
                        focusedBorderColor = Purple500,
                        unfocusedBorderColor = White12,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${allSongs.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = White38
                    )
                    Box {
                        TextButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = null, tint = Purple400, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(sortOption.label, color = Purple400, style = MaterialTheme.typography.bodySmall)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor = DarkSurface
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (sortOption == option) {
                                                Icon(Icons.Outlined.Check, contentDescription = null, tint = Purple500, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text(option.label, color = Color.White)
                                        }
                                    },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (recentEdits.isNotEmpty()) {
                item {
                    SectionHeader("RECENT EDITS")
                }
                items(recentEdits.take(3)) { edit ->
                    RecentEditItem(
                        edit = edit,
                        onClick = { onEditConfig(edit) },
                        onDelete = { viewModel.deleteConfig(edit.id) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("YOUR SONGS")
                }
            }

            if (sortedSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            // Animated Music Icon
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Purple500.copy(alpha = 0.3f),
                                                Cyan400.copy(alpha = 0.2f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (searchQuery.isNotEmpty()) Icons.Outlined.SearchOff else Icons.Outlined.LibraryMusic,
                                    contentDescription = null,
                                    tint = if (searchQuery.isNotEmpty()) Purple400 else Cyan400,
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            Spacer(Modifier.height(24.dp))

                            Text(
                                if (searchQuery.isNotEmpty())
                                    "No songs match \"$searchQuery\""
                                else
                                    "No songs yet!",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                if (searchQuery.isNotEmpty())
                                    "Try a different search term"
                                else
                                    "Add music files to your device to get started",
                                color = White38,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = TextAlign.Center
                            )

                            if (searchQuery.isEmpty()) {
                                Spacer(Modifier.height(24.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.loadSongs() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Purple500
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Scan Music")
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "Make sure music files are stored on your device",
                                    color = White38,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                items(sortedSongs) { song ->
                    SongItem(
                        title = song.title,
                        artist = song.artist,
                        duration = song.formattedDuration,
                        isCurrentlyPlaying = currentTrack?.dataPath == song.dataPath,
                        onClick = { onSongSelected(song.dataPath, song.title) }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RecentEditItem(
    edit: SavedConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = Purple500,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = edit.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W600,
                    color = Color.White
                )
                Text(
                    text = "Saved recently",
                    style = MaterialTheme.typography.bodySmall,
                    color = White38
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = White38
                )
            }
        }
    }
}