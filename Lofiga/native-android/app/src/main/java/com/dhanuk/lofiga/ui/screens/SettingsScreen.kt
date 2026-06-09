package com.dhanuk.lofiga.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import com.dhanuk.lofiga.ui.theme.LocalAppColors
import com.dhanuk.lofiga.util.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsManager.settingsFlow.collectAsState(initial = SettingsManager.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = LocalAppColors.current

    var showFormatDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 80.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            SectionHeader("GENERAL")

            SettingItem(
                title = "Audio Format",
                subtitle = settings.audioFormat.uppercase(),
                icon = Icons.Outlined.AudioFile,
                onClick = { showFormatDialog = true }
            )

            if (settings.audioFormat != "wav") {
                SettingItem(
                    title = "Audio Bitrate",
                    subtitle = settings.audioBitrate,
                    icon = Icons.Outlined.Speed,
                    onClick = { showBitrateDialog = true }
                )
            }

            SettingItem(
                title = "Theme",
                subtitle = if (settings.isDarkMode) "Dark Mode" else "Light Mode",
                icon = Icons.Outlined.DarkMode,
                onClick = {},
                isSwitch = true,
                switchValue = settings.isDarkMode,
                onSwitchChange = { dark ->
                    scope.launch { viewModel.settingsManager.updateDarkMode(dark) }
                }
            )

            Spacer(Modifier.height(32.dp))

            SectionHeader("ABOUT")

            SettingItem(
                title = "Version",
                subtitle = "2.0.0 (Native)",
                icon = Icons.Outlined.Info,
                onClick = {}
            )

            SettingItem(
                title = "Made with \u2764 for Lofi Lovers",
                subtitle = "",
                icon = Icons.Outlined.FavoriteBorder,
                onClick = {}
            )

            SettingItem(
                title = "Privacy Policy",
                subtitle = "Learn how we handle your data",
                icon = Icons.Outlined.PrivacyTip,
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dhanuk.page.gd/Lofiga/Privacy-Policy.html?i=2"))
                    context.startActivity(browserIntent)
                }
            )

            SettingItem(
                title = "Terms of Use",
                subtitle = "Read our terms and conditions",
                icon = Icons.Outlined.Description,
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dhanuk.page.gd/Lofiga/Terms-Of-Use.html"))
                    context.startActivity(browserIntent)
                }
            )

            SettingItem(
                title = "Contact Us",
                subtitle = "Get in touch with us",
                icon = Icons.Outlined.MailOutline,
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dhanuk.page.gd/Lofiga/Contact-Us.html"))
                    context.startActivity(browserIntent)
                }
            )

            Spacer(Modifier.height(24.dp))

            SectionHeader("CUSTOM PRESETS")
            val customPresets by viewModel.customPresets.collectAsState()
            if (customPresets.isEmpty()) {
                Text(
                    "No custom presets saved yet",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                customPresets.forEach { preset ->
                    key(preset.id) {
                        var showPresetDeleteConfirm by remember { mutableStateOf(false) }
                        if (showPresetDeleteConfirm) {
                            DeleteConfirmDialog(
                                title = "Delete Preset",
                                message = "Are you sure you want to delete \"${preset.name}\"? This cannot be undone.",
                                onConfirm = {
                                    viewModel.deleteCustomPreset(preset.id)
                                    showPresetDeleteConfirm = false
                                },
                                onDismiss = { showPresetDeleteConfirm = false }
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(colors.outline, colors.outline)
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Bookmark,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        color = colors.textPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Custom preset",
                                        color = colors.textTertiary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { showPresetDeleteConfirm = true }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5252).copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    if (showFormatDialog) {
        FormatSelectionDialog(
            currentFormat = settings.audioFormat,
            onSelect = { format ->
                scope.launch { viewModel.settingsManager.updateFormat(format) }
                showFormatDialog = false
            },
            onDismiss = { showFormatDialog = false }
        )
    }

    if (showBitrateDialog) {
        BitrateSelectionDialog(
            currentBitrate = settings.audioBitrate,
            onSelect = { bitrate ->
                scope.launch { viewModel.settingsManager.updateBitrate(bitrate) }
                showBitrateDialog = false
            },
            onDismiss = { showBitrateDialog = false }
        )
    }
}

@Composable
private fun FormatSelectionDialog(
    currentFormat: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val formats = listOf("m4a" to "M4A (AAC) - Best quality/size", "wav" to "WAV - Uncompressed")

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Export Format", color = colors.textPrimary) },
        text = {
            Column {
                formats.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onSelect(value) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFormat == value,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Purple500,
                                unselectedColor = colors.textTertiary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(value.uppercase(), color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text(label, color = colors.textTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Purple500)
            }
        }
    )
}

@Composable
private fun BitrateSelectionDialog(
    currentBitrate: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val bitrates = listOf("128k" to "128 Kbps - Small file", "192k" to "192 Kbps - Balanced", "256k" to "256 Kbps - High quality", "320k" to "320 Kbps - Best quality")

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Audio Bitrate", color = colors.textPrimary) },
        text = {
            Column {
                bitrates.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onSelect(value) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentBitrate == value,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Purple500,
                                unselectedColor = colors.textTertiary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(value, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text(label, color = colors.textTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Purple500)
            }
        }
    )
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isSwitch: Boolean = false,
    switchValue: Boolean = false,
    onSwitchChange: (Boolean) -> Unit = {}
) {
    val colors = LocalAppColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(colors.outline, colors.outline)
            )
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W600,
                    color = colors.textPrimary
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary
                    )
                }
            }
            if (isSwitch) {
                Switch(
                    checked = switchValue,
                    onCheckedChange = onSwitchChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Purple500,
                        checkedTrackColor = Purple500.copy(alpha = 0.3f),
                        uncheckedThumbColor = colors.textTertiary,
                        uncheckedTrackColor = colors.outline
                    )
                )
            } else {
                Icon(
                    Icons.Outlined.NavigateNext,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
