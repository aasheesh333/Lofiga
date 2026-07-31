package com.dhanuk.lofiga.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhanuk.lofiga.BuildConfig
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.DeleteConfirmDialog
import com.dhanuk.lofiga.ui.theme.*
import com.dhanuk.lofiga.util.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val settings by viewModel.settingsManager.settingsFlow.collectAsState(initial = SettingsManager.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val customPresets by viewModel.customPresets.collectAsState()

    var showBitrateMenu by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp)
        ) {
            // ── Top app bar ────────────────────────────────────────────────────
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )

            // ════════════════════════════════════════════════════════════════════
            // Section 1 — APPEARANCE
            // ════════════════════════════════════════════════════════════════════
            SettingsSection("APPEARANCE") {
                SwitchRow(
                    title = "Dark mode",
                    subtitle = "Use dark surfaces instead of light",
                    checked = settings.isDarkMode,
                    onCheckedChange = { dark ->
                        scope.launch { viewModel.settingsManager.updateDarkMode(dark) }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ════════════════════════════════════════════════════════════════════
            // Section 2 — EXPORT
            // ════════════════════════════════════════════════════════════════════
            SettingsSection("EXPORT") {
                // Audio quality dropdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBitrateMenu = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio quality", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                        Text("Bitrate for exported files", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                    }
                    Box {
                        Text(
                            settings.audioBitrate,
                            style = MaterialTheme.typography.labelLarge,
                            color = Indigo,
                            fontWeight = FontWeight.SemiBold
                        )
                        DropdownMenu(
                            expanded = showBitrateMenu,
                            onDismissRequest = { showBitrateMenu = false },
                            containerColor = colors.surface
                        ) {
                            listOf("128k" to "128 kbps", "192k" to "192 kbps", "256k" to "256 kbps", "320k" to "320 kbps").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(label, color = if (settings.audioBitrate == value) Indigo else colors.textPrimary)
                                            if (settings.audioBitrate == value) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Outlined.Check, contentDescription = null, tint = Indigo, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        scope.launch { viewModel.settingsManager.updateBitrate(value) }
                                        showBitrateMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = colors.textTertiary)
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                // Format segmented buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Format", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = settings.audioFormat == "m4a",
                            onClick = { scope.launch { viewModel.settingsManager.updateFormat("m4a") } },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("M4A", style = MaterialTheme.typography.labelLarge) }
                        SegmentedButton(
                            selected = settings.audioFormat == "wav",
                            onClick = { scope.launch { viewModel.settingsManager.updateFormat("wav") } },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("WAV", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ════════════════════════════════════════════════════════════════════
            // Section 3 — ADS & PREMIUM
            // ════════════════════════════════════════════════════════════════════
            SettingsSection("ADS & PREMIUM") {
                ActionRow(
                    title = "Remove ads",
                    subtitle = "Upgrade to premium for an ad-free experience"
                ) {
                    OutlinedButton(
                        onClick = { snackbarMessage = "Premium coming soon" },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Indigo),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Indigo),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("Upgrade", fontWeight = FontWeight.SemiBold) }
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                ActionRow(
                    title = "Watch ad for 1 hour ad-free",
                    subtitle = "Watch a short ad to remove ads temporarily"
                ) {
                    TextButton(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                AdManager.showRewarded(
                                    activity = activity,
                                    onRewarded = { snackbarMessage = "Enjoy 1 hour of ad-free listening!" },
                                    onDismissed = { snackbarMessage = "Ad not available right now" }
                                )
                            } else {
                                snackbarMessage = "Unable to show ad"
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Indigo),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Watch ad", fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                ActionRow(
                    title = "Reset ad counters",
                    subtitle = "Clear ad failure counters and retry"
                ) {
                    TextButton(
                        onClick = {
                            AdManager.resetFailureCounters(context)
                            snackbarMessage = "Ad counters reset"
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.textTertiary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("Reset") }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ════════════════════════════════════════════════════════════════════
            // Section 4 — CUSTOM PRESETS
            // ════════════════════════════════════════════════════════════════════
            if (customPresets.isNotEmpty()) {
                SettingsSection("CUSTOM PRESETS") {
                    customPresets.forEach { preset ->
                        key(preset.id) {
                            var showDelete by remember { mutableStateOf(false) }
                            if (showDelete) {
                                DeleteConfirmDialog(
                                    title = "Delete Preset",
                                    message = "Delete \"${preset.name}\"? This cannot be undone.",
                                    onConfirm = { viewModel.deleteCustomPreset(preset.id); showDelete = false },
                                    onDismiss = { showDelete = false }
                                )
                            }
                            ActionRow(
                                title = preset.name,
                                subtitle = "Custom preset"
                            ) {
                                IconButton(onClick = { showDelete = true }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFBA1A1A).copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                                }
                            }
                            HorizontalDivider(color = colors.outline, thickness = 1.dp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ════════════════════════════════════════════════════════════════════
            // Section 5 — ABOUT
            // ════════════════════════════════════════════════════════════════════
            SettingsSection("ABOUT") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lofiga", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary
                    )
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                LinkRow("Rate on Play Store") {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
                    } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                    }
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                LinkRow("Send feedback") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dhanuk.page.gd/Lofiga/Contact-Us.html")))
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                LinkRow("Privacy policy") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dhanuk.page.gd/Lofiga/Privacy-Policy.html?i=2")))
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                LinkRow("Terms of use") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dhanuk.page.gd/Lofiga/Terms-Of-Use.html")))
                }
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                LinkRow("Open source licenses") {
                    snackbarMessage = "Licenses available at github.com/dhanuk/lofiga"
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }
}

// ── Reusable section card ──────────────────────────────────────────────────────
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            title.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outline),
            shadowElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

// ── Switch row ─────────────────────────────────────────────────────────────────
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Indigo,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.outline
            )
        )
    }
}

// ── Action row (title + subtitle + trailing composable) ────────────────────────
@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        trailing()
    }
}

// ── Link row (indigo text, clickable) ──────────────────────────────────────────
@Composable
private fun LinkRow(
    title: String,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Indigo, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
    }
}

// ── Activity finder (needed for rewarded ads) ──────────────────────────────────
private fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
