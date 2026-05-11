package com.dhanuk.lofiga.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun WaveformVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    color: Color = Purple500,
    isPlaying: Boolean = false,
    waveformData: FloatArray? = null
) {
    val hasRealData = waveformData != null && waveformData.isNotEmpty() && waveformData.any { kotlin.math.abs(it) > 0.01f }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        if (!isPlaying) {
            val y = size.height / 2
            drawLine(
                color = color.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f
            )
            return@Canvas
        }

        val barWidth = size.width / barCount
        val maxHeight = size.height * 0.8f
        val centerY = size.height / 2

        for (i in 0 until barCount) {
            val x = i * barWidth + barWidth * 0.15f
            val rectWidth = barWidth * 0.7f

            val value: Float = if (hasRealData && i < waveformData!!.size) {
                waveformData[i].coerceIn(-1f, 1f)
            } else {
                val t = i.toFloat() / barCount * 2f * kotlin.math.PI.toFloat()
                val sin1 = kotlin.math.sin((2f * t + phase).toDouble()).toFloat()
                val sin2 = kotlin.math.sin((3f * t + phase * 1.5f).toDouble()).toFloat()
                val sin3 = kotlin.math.sin((5f * t + phase * 2.2f).toDouble()).toFloat()
                val sin4 = kotlin.math.sin((i * 7 + phase * 3).toDouble()).toFloat()
                0.3f * sin1 + 0.2f * sin2 + 0.1f * sin3 + 0.1f * sin4
            }

            val barHeight = ((value + 1f) / 2f).coerceIn(0.05f, 1f) * maxHeight

            drawRoundRect(
                color = color.copy(
                    alpha = 0.4f + 0.6f * (barHeight / maxHeight)
                ),
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = androidx.compose.ui.geometry.Size(rectWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
            )
        }
    }
}

@Composable
fun VinylRecord(
    modifier: Modifier = Modifier,
    size: Int = 200,
    isPlaying: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = if (isPlaying)
                tween(4000, easing = LinearEasing)
            else
                tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val radius = size.toFloat() / 2
        val center = Offset(radius, radius)

        drawCircle(
            color = Color(0xFF121212),
            radius = radius,
            center = center
        )

        for (i in 1..4) {
            drawCircle(
                color = Color(0xFF1A1A1A),
                radius = radius - (i * radius * 0.1f),
                center = center,
                style = Stroke(width = 1f)
            )
        }

        drawCircle(
            color = Purple500,
            radius = radius * 0.35f,
            center = center
        )
        drawCircle(
            color = Purple700,
            radius = radius * 0.33f,
            center = center
        )

        drawCircle(
            color = Color(0xFF121212),
            radius = radius * 0.05f,
            center = center
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.03f),
            radius = radius * 0.9f,
            center = center.copy(
                x = center.x + radius * 0.3f,
                y = center.y - radius * 0.3f
            )
        )
    }
}

@Composable
fun LofigaNavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = DarkSurface,
        contentColor = Color.White,
        tonalElevation = 0.dp
    ) {
        listOf("Songs", "Player", "Mixes", "Settings").forEachIndexed { index, label ->
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
                    unselectedIconColor = White38,
                    unselectedTextColor = White38,
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
                    color = Color.White
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = White38
                    )
                }
            }
            Text(
                text = displayValue ?: "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = White60
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
                inactiveTrackColor = White12,
                activeTickColor = Purple500,
                inactiveTickColor = White12
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCurrentlyPlaying) DarkSurfaceHighlight else DarkSurface),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isCurrentlyPlaying) Purple500.copy(alpha = 0.3f) else White12,
                    if (isCurrentlyPlaying) Purple500.copy(alpha = 0.1f) else White12
                )
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrentlyPlaying) Purple500.copy(alpha = 0.15f) else DarkSurfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Outlined.Equalizer else Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrentlyPlaying) Purple500 else White38,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = artist,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentlyPlaying) Purple400 else White38
                )
            }
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = White38
            )
            Icon(
                imageVector = if (isCurrentlyPlaying) Icons.Outlined.PlayArrow else Icons.Outlined.NavigateNext,
                contentDescription = null,
                tint = if (isCurrentlyPlaying) Purple500 else White38
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = White38,
        letterSpacing = 2.sp
    )
}

@Composable
fun AtmosphereChip(
    label: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = volume > 0.01f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Purple500.copy(alpha = 0.2f) else DarkSurfaceHighlight,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isActive) Purple500.copy(alpha = 0.5f) else White12,
                    if (isActive) Purple500.copy(alpha = 0.3f) else White12
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
                color = if (isActive) Purple400 else White60
            )
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.width(60.dp).height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Purple500,
                    activeTrackColor = Purple500,
                    inactiveTrackColor = White12
                )
            )
        }
    }
}