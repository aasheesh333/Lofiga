package com.dhanuk.lofiga.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhanuk.lofiga.model.AudioTrack
import com.dhanuk.lofiga.model.MoodTag
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*

enum class SortOption(val label: String) {
    DATE_ADDED("Recent"),
    NAME("Title A-Z"),
    ARTIST("Artist A-Z")
}

enum class MoodFilterOption(val label: String, val tag: MoodTag?) {
    ALL("All", null),
    CHILL("Chill", MoodTag.CHILL),
    MID("Mid", MoodTag.MID),
    ENERGETIC("Energetic", MoodTag.ENERGETIC)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onSongSelected: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val allSongs by viewModel.allSongs.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val moodTags by viewModel.moodTags.collectAsState()

    var sortOption by remember { mutableStateOf(SortOption.DATE_ADDED) }
    var moodFilter by remember { mutableStateOf(MoodFilterOption.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadSongs()
            kotlinx.coroutines.delay(600)
            isRefreshing = false
        }
    }

    val sortedSongs = remember(filteredSongs, sortOption, moodFilter, moodTags) {
        val moodFiltered = if (moodFilter.tag == null) {
            filteredSongs
        } else {
            filteredSongs.filter { track -> moodTags[track.id] == moodFilter.tag }
        }
        when (sortOption) {
            SortOption.DATE_ADDED -> moodFiltered.sortedByDescending { it.dateAdded }
            SortOption.NAME -> moodFiltered.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> moodFiltered.sortedBy { it.artist.lowercase() }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LofigaTopBar(title = "Library")

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search songs or artists", color = colors.textTertiary) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = colors.textTertiary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = colors.textTertiary)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = colors.surfaceHighlight,
                            unfocusedContainerColor = colors.surfaceHighlight,
                            focusedLeadingIconColor = colors.textTertiary,
                            unfocusedLeadingIconColor = colors.textTertiary
                        )
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${sortedSongs.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            FilterChip(
                                selected = moodFilter != MoodFilterOption.ALL,
                                onClick = { showFilterMenu = true },
                                label = { Text("Filter: ${moodFilter.label}", style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoContainer,
                                    selectedLabelColor = colors.accent,
                                    containerColor = colors.surface,
                                    labelColor = colors.textPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (moodFilter != MoodFilterOption.ALL) colors.accent.copy(alpha = 0.3f) else colors.outline
                                )
                            )
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                                containerColor = colors.surface
                            ) {
                                MoodFilterOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label, color = if (moodFilter == option) colors.accent else colors.textPrimary) },
                                        onClick = { moodFilter = option; showFilterMenu = false },
                                        leadingIcon = if (moodFilter == option) {
                                            { Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                        Box {
                            FilterChip(
                                selected = sortOption != SortOption.DATE_ADDED,
                                onClick = { showSortMenu = true },
                                label = { Text("Sort: ${sortOption.label}", style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoContainer,
                                    selectedLabelColor = colors.accent,
                                    containerColor = colors.surface,
                                    labelColor = colors.textPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (sortOption != SortOption.DATE_ADDED) colors.accent.copy(alpha = 0.3f) else colors.outline
                                )
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                containerColor = colors.surface
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label, color = if (sortOption == option) colors.accent else colors.textPrimary) },
                                        onClick = { sortOption = option; showSortMenu = false },
                                        leadingIcon = if (sortOption == option) {
                                            { Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
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
                                Surface(
                                    shape = CircleShape,
                                    color = IndigoContainer,
                                    modifier = Modifier.size(88.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (searchQuery.isNotEmpty()) Icons.Outlined.SearchOff else Icons.Outlined.LibraryMusic,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    if (searchQuery.isNotEmpty()) "No songs match \"$searchQuery\"" else "No songs yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (searchQuery.isNotEmpty()) "Try a different search" else "Add music files to your device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isEmpty()) {
                                    Spacer(Modifier.height(20.dp))
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
                } else {
                    items(sortedSongs, key = {
                        when {
                            it.dataPath.isNotEmpty() -> it.dataPath
                            it.uri != null -> it.uri.toString()
                            else -> "song_${it.id}_${it.title}"
                        }
                    }) { song ->
                        SongItem(
                            title = song.title,
                            artist = song.artist,
                            duration = song.formattedDuration,
                            isCurrentlyPlaying = currentTrack?.dataPath == song.dataPath ||
                                (currentTrack?.uri != null && currentTrack?.uri == song.uri),
                            onClick = { onSongSelected(song) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                            albumArtUri = song.albumArtUri
                        )
                    }
                }
            }
        }
    }
}
