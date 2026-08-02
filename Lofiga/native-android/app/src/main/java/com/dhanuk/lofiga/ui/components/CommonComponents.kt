package com.dhanuk.lofiga.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dhanuk.lofiga.R
import com.dhanuk.lofiga.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
// Lofiga v2 — Shared UI Components
// Pure white surfaces, 1px #EEEEEE outlines, deep indigo #1F3A8A accent.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Ambient page background. The v2.1 design keeps the page a very light
 * off-white and lets cards/pop surfaces sit cleanly on top.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(modifier = modifier.background(colors.pageBg))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LofigaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalAppColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.bg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onSearch != null) {
                IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            actions()
        }
    }
}

/**
 * 4-tab bottom NavigationBar: Home, Library, Player, Settings.
 * White surface, indigo pill indicator on active tab, gray inactive icons.
 */
@Composable
fun LofigaNavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showNowPlayingBadge: Boolean = false
) {
    val colors = LocalAppColors.current
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    ) {
        listOf("Home", "Library", "Player", "Settings").forEachIndexed { index, label ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (index) {
                                0 -> Icons.Outlined.Home
                                1 -> Icons.Outlined.LibraryMusic
                                2 -> Icons.Outlined.Equalizer
                                3 -> Icons.Outlined.Settings
                                else -> Icons.Outlined.MusicNote
                            },
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                        if (showNowPlayingBadge && index == 2 && selectedIndex != 2) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-4).dp)
                                    .size(6.dp)
                                    .background(Indigo, CircleShape)
                            )
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Indigo,
                    unselectedIconColor = colors.textSecondary,
                    selectedTextColor = Indigo,
                    unselectedTextColor = colors.textSecondary,
                    indicatorColor = IndigoContainer
                ),
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

/**
 * Small pill chip for headers — subtle surface with outline.
 */
@Composable
fun HeaderChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceHighlight,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Collapsible section card — white surface, 1px outline, 12dp radius.
 * Used for EFFECTS and ATMOSPHERE blocks on the Player screen.
 */
@Composable
fun ExpandableSection(
    icon: ImageVector,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = IndigoContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (expanded) {
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                Column(
                    modifier = Modifier.padding(16.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Effect slider row — icon + label + indigo value on top, slider below.
 */
@Composable
fun EffectSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    displayValue: String? = null,
    icon: @Composable (() -> Unit)? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                }
            }
            Text(
                displayValue ?: "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Indigo,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Indigo,
                activeTrackColor = Indigo,
                inactiveTrackColor = colors.outline,
                activeTickColor = Indigo,
                inactiveTickColor = colors.outline
            )
        )
    }
}

/**
 * Song list row — flat with bottom divider, 48dp thumbnail, title/artist/duration.
 * Tapping loads the track into the Player.
 */
@Composable
fun SongItem(
    title: String,
    artist: String,
    duration: String,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    gradientThumb: Boolean = false,
    thumbTitle: String = title,
    albumArtUri: android.net.Uri? = null
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCurrentlyPlaying) IndigoContainer else colors.surfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentlyPlaying) {
                    Icon(
                        imageVector = Icons.Outlined.Equalizer,
                        contentDescription = null,
                        tint = Indigo,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(albumArtUri)
                            .fallback(R.drawable.ic_default_album_art)
                            .error(R.drawable.ic_default_album_art)
                            .placeholder(R.drawable.ic_default_album_art)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentlyPlaying) Indigo else colors.textPrimary,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                    artist,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
            }
            Text(
                duration,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary
            )
        }
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
    }
}

/**
 * First letter(s) of the most significant words from a title, for a text-based placeholder.
 */
fun titleWordMonogram(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> (words.first().take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * Section label with action affordance.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        action?.invoke()
    }
}

/**
 * Thumbnail with gradient — kept for backward compat but simplified.
 */
@Composable
fun GradientThumbnail(
    size: Int = 48,
    title: String,
    isActive: Boolean = false
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) IndigoContainer else colors.surfaceHighlight),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isActive) Icons.Outlined.Equalizer else Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = if (isActive) Indigo else colors.textTertiary,
            modifier = Modifier.size((size * 0.4f).dp)
        )
    }
}

/**
 * Gradient colour pair for a title hash — simplified to indigo shades.
 */
fun gradientForTitle(title: String): List<Color> {
    return listOf(Indigo, IndigoLight)
}

/**
 * Effect card — compact slider card for the player.
 */
@Composable
fun EffectCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    val colors = LocalAppColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceHighlight,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(value, style = MaterialTheme.typography.labelMedium, color = Indigo, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderValue.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onSliderChange,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Indigo,
                    activeTrackColor = Indigo,
                    inactiveTrackColor = colors.outline
                )
            )
        }
    }
}

/**
 * Delete confirmation dialog.
 */
@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFBA1A1A), modifier = Modifier.size(32.dp)) },
        title = { Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textTertiary)
            }
        }
    )
}

/**
 * Atmosphere chip — compact slider for rain/vinyl/wind/tape.
 */
@Composable
fun AtmosphereChip(
    label: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val isActive = volume > 0.01f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (isActive) Indigo.copy(alpha = 0.3f) else colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (isActive) Indigo else colors.textSecondary
            )
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.width(60.dp).height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Indigo,
                    activeTrackColor = Indigo,
                    inactiveTrackColor = colors.outline
                )
            )
        }
    }
}
