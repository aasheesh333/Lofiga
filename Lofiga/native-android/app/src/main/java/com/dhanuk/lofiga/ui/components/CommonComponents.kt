package com.dhanuk.lofiga.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhanuk.lofiga.ui.theme.LocalAppColors
import com.dhanuk.lofiga.ui.theme.*

@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawCircle(
            color = Purple500.copy(alpha = 0.15f),
            radius = w * 0.6f,
            center = Offset(w * 0.2f, h * 0.1f)
        )
        drawCircle(
            color = Cyan400.copy(alpha = 0.08f),
            radius = w * 0.5f,
            center = Offset(w * 0.8f, h * 0.9f)
        )
        drawCircle(
            color = Purple500.copy(alpha = 0.08f),
            radius = w * 0.4f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}


@Composable
fun LofigaNavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp
    ) {
        listOf("Browse", "Player", "Exports", "Settings").forEachIndexed { index, label ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = when (index) {
                            0 -> Icons.Outlined.LibraryMusic
                            1 -> Icons.Outlined.Equalizer
                            2 -> Icons.Outlined.Folder
                            3 -> Icons.Outlined.Settings
                            else -> Icons.Outlined.MusicNote
                        },
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Purple500,
                    selectedTextColor = Purple500,
                    unselectedIconColor = colors.textTertiary,
                    unselectedTextColor = colors.textTertiary,
                    indicatorColor = Purple500.copy(alpha = 0.15f)
                )
            )
        }
    }
}

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
    Column(modifier = modifier.padding(vertical = 4.dp)) {
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
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary
                    )
                }
            }
            Text(
                text = displayValue ?: "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Purple500,
                activeTrackColor = Purple500,
                inactiveTrackColor = colors.outline,
                activeTickColor = Purple500,
                inactiveTickColor = colors.outline
            )
        )
    }
}

@Composable
fun SongItem(
    title: String,
    artist: String,
    duration: String,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    gradientThumb: Boolean = false,
    thumbTitle: String = title
) {
    val colors = LocalAppColors.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCurrentlyPlaying) colors.surfaceHighlight else colors.surface),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isCurrentlyPlaying) Purple500.copy(alpha = 0.3f) else colors.outline,
                    if (isCurrentlyPlaying) Purple500.copy(alpha = 0.1f) else colors.outline
                )
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (gradientThumb) {
                GradientThumbnail(size = 50, title = thumbTitle, isActive = isCurrentlyPlaying)
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrentlyPlaying) Purple500.copy(alpha = 0.15f) else colors.surfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentlyPlaying) Icons.Outlined.Equalizer else Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrentlyPlaying) Purple500 else colors.textTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = artist,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentlyPlaying) Purple400 else colors.textTertiary
                )
            }
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
            Icon(
                imageVector = if (isCurrentlyPlaying) Icons.Outlined.PlayArrow else Icons.Outlined.NavigateNext,
                contentDescription = null,
                tint = if (isCurrentlyPlaying) Purple500 else colors.textTertiary
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Text(
        text = title,
        modifier = modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textTertiary,
        letterSpacing = 2.sp
    )
}

/**
 * Creates a gradient thumbnail based on a string hash for visual variety.
 */
fun gradientForTitle(title: String): List<Color> {
    val pairs = listOf(
        Purple500 to Cyan400,
        Purple700 to Purple400,
        Color(0xFFE0E0E0) to Color(0xFFBDBDBD),
        Color(0xFFD0D0D0) to Color(0xFF9E9E9E),
        Color(0xFFCCCCCC) to Color(0xFF888888),
        Color(0xFFAAAAAA) to Color(0xFF666666),
        Color(0xFFBBBBBB) to Color(0xFF777777),
        Color(0xFF999999) to Color(0xFF555555)
    )
    val hash = title.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return listOf(pairs[hash % pairs.size].first, pairs[hash % pairs.size].second)
}

@Composable
fun GradientThumbnail(
    size: Int = 50,
    title: String,
    isActive: Boolean = false
) {
    val appColors = LocalAppColors.current
    val gradientColors = remember(title) { gradientForTitle(title) }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        gradientColors[0].copy(alpha = if (isActive) 0.8f else 0.6f),
                        gradientColors[1].copy(alpha = if (isActive) 0.6f else 0.4f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = appColors.textPrimary.copy(alpha = if (isActive) 0.9f else 0.7f),
            modifier = Modifier.size((size * 0.45f).dp)
        )
    }
}

@Composable
fun EffectCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit
) {
    val colors = LocalAppColors.current
    val isActive = sliderValue > 0.01f || label == "Tempo"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) Purple500.copy(alpha = 0.1f) else colors.surfaceHighlight,
        border = BorderStroke(
            1.dp,
            if (isActive) Purple500.copy(alpha = 0.3f) else colors.outline
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Purple500.copy(alpha = if (isActive) 0.8f else 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) colors.textPrimary else colors.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan400,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Purple500,
                    activeTrackColor = Purple500,
                    inactiveTrackColor = colors.outline
                )
            )
        }
    }
}

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
        icon = {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                tint = Color(0xFF757575),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = message, color = colors.textSecondary)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color(0xFF757575), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textTertiary)
            }
        }
    )
}

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
        color = if (isActive) Purple500.copy(alpha = 0.2f) else colors.surfaceHighlight,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isActive) Purple500.copy(alpha = 0.5f) else colors.outline,
                    if (isActive) Purple500.copy(alpha = 0.3f) else colors.outline
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) Purple400 else colors.textSecondary
            )
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.width(60.dp).height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Purple500,
                    activeTrackColor = Purple500,
                    inactiveTrackColor = colors.outline
                )
            )
        }
    }
}