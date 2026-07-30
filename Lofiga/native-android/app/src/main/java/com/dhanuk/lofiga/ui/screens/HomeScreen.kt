package com.dhanuk.lofiga.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhanuk.lofiga.model.AudioTrack
import com.dhanuk.lofiga.model.SavedConfig
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import com.dhanuk.lofiga.ui.theme.LocalAppColors

enum class SortOption(val label: String) {
    DATE_ADDED("Date Added"),
    NAME("Name"),
    DURATION("Duration"),
    SIZE("Size"),
    ARTIST("Artist")
}

/**
 * C.2 — library filter by auto-detected track mood. ALL shows every track
 * (including ones whose mood hasn't been computed yet); the others narrow
 * the list to tracks whose [com.dhanuk.lofiga.model.MoodTag] matches.
 */
enum class MoodFilterOption(val label: String, val tag: com.dhanuk.lofiga.model.MoodTag?) {
    ALL("All", null),
    CHILL("Chill", com.dhanuk.lofiga.model.MoodTag.CHILL),
    MID("Mid", com.dhanuk.lofiga.model.MoodTag.MID),
    ENERGETIC("Energetic", com.dhanuk.lofiga.model.MoodTag.ENERGETIC)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onSongSelected: (com.dhanuk.lofiga.model.AudioTrack) -> Unit,
    onEditConfig: (SavedConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val recentEdits by viewModel.recentEdits.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val moodTags by viewModel.moodTags.collectAsState()   // C.2: id -> MoodTag

    var sortOption by remember { mutableStateOf(SortOption.DATE_ADDED) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var moodFilter by remember { mutableStateOf(MoodFilterOption.ALL) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadSongs()
            kotlinx.coroutines.delay(600)
            isRefreshing = false
        }
    }

    val sortedSongs = remember(filteredSongs, sortOption, moodFilter, moodTags) {
        // C.2: apply mood filter BEFORE sort so the chip count reflects only
        // tagged-matching tracks. ALL keeps untagged tracks visible too.
        val moodFiltered = if (moodFilter.tag == null) {
            filteredSongs
        } else {
            filteredSongs.filter { track -> moodTags[track.id] == moodFilter.tag }
        }
        when (sortOption) {
            SortOption.DATE_ADDED -> moodFiltered.sortedByDescending { it.dateAdded }
            SortOption.NAME -> moodFiltered.sortedBy { it.title.lowercase() }
            SortOption.DURATION -> moodFiltered.sortedBy { it.durationMs }
            SortOption.SIZE -> moodFiltered.sortedByDescending { it.fileSize }
            SortOption.ARTIST -> moodFiltered.sortedBy { it.artist.lowercase() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier.weight(1f)
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
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Turn Any Song Into Lofi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(bottom = 20.dp)
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search songs...", color = colors.textTertiary) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = colors.textTertiary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = colors.textTertiary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.textPrimary,
                                focusedBorderColor = colors.textPrimary,
                                unfocusedBorderColor = colors.outline,
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface
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
                                color = colors.textTertiary
                            )
                            Box {
                                TextButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Outlined.Sort, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(sortOption.label, color = colors.textPrimary, style = MaterialTheme.typography.bodySmall)
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    containerColor = colors.surface
                                ) {
                                    SortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (sortOption == option) {
                                                        Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    Text(option.label, color = colors.textPrimary)
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

                    // C.2 — Mood filter chip row. Visible only when there's at
                    // least one tagged track so first-time users with an untagged
                    // library don't see a useless empty row. Updates as moodTags
                    // fills in via background precompute.
                    if (moodTags.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MoodFilterOption.entries.forEach { option ->
                                    FilterChip(
                                        selected = moodFilter == option,
                                        onClick = { moodFilter = option },
                                        label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                                        leadingIcon = if (moodFilter == option) {
                                            {
                                                Icon(
                                                    Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    if (recentEdits.isNotEmpty()) {
                        item {
                            SectionHeader("RECENT EDITS")
                        }
                        items(recentEdits.take(3), key = { it.id }) { edit ->
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
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        colors.textPrimary.copy(alpha = 0.3f),
                                                        colors.textPrimary.copy(alpha = 0.2f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (searchQuery.isNotEmpty()) Icons.Outlined.SearchOff else Icons.Outlined.LibraryMusic,
                                            contentDescription = null,
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    Text(
                                        if (searchQuery.isNotEmpty())
                                            "No songs match \"$searchQuery\""
                                        else
                                            "No songs yet!",
                                        color = colors.textPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        if (searchQuery.isNotEmpty())
                                            "Try a different search term"
                                        else
                                            "Add music files to your device to get started",
                                        color = colors.textTertiary,
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
                                                    containerColor = colors.textPrimary
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
                                            color = colors.textTertiary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(sortedSongs, key = {
                            // dataPath is non-nullable (default ""); build a stable,
                            // collision-free key with sensible fallbacks.
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
                                gradientThumb = true,
                                thumbTitle = song.title,
                                isCurrentlyPlaying = currentTrack?.dataPath == song.dataPath || (currentTrack?.uri != null && currentTrack?.uri == song.uri),
                                onClick = { onSongSelected(song) }
                            )
                        }
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
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
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
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = edit.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W600,
                    color = colors.textPrimary
                )
                Text(
                    text = "Saved recently",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = colors.textTertiary
                )
            }
        }
    }
}
