package com.dhanuk.lofiga.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.ads.BannerAd
import com.dhanuk.lofiga.ads.DebugFlags
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.*
import com.dhanuk.lofiga.ui.theme.*
import com.dhanuk.lofiga.ui.theme.LocalAppColors
import com.dhanuk.lofiga.util.SettingsManager
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnAdInspectorClosedListener
import kotlinx.coroutines.delay
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
    var showAdDebugDialog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableStateOf(0) }

    LaunchedEffect(versionTapCount) {
        if (versionTapCount in 1..6) {
            delay(3000)
            versionTapCount = 0
        }
    }

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
                subtitle = when {
                    versionTapCount == 0 -> "2.0.0 (Native)"
                    versionTapCount < 7 -> "2.0.0 (Native) • ${7 - versionTapCount} more tap${if (7 - versionTapCount == 1) "" else "s"}"
                    else -> "2.0.0 (Native)"
                },
                icon = Icons.Outlined.Info,
                onClick = {
                    if (versionTapCount < 7) {
                        versionTapCount++
                        if (versionTapCount >= 7) {
                            versionTapCount = 0
                            showAdDebugDialog = true
                        }
                    }
                }
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

            Spacer(Modifier.height(16.dp))
            BannerAd(
                modifier = Modifier.fillMaxWidth()
            )
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

    if (showAdDebugDialog) {
        AdDiagnosticsDialog(onDismiss = { showAdDebugDialog = false })
    }
}

@Composable
private fun AdDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val diag by AdManager.diagnostics.collectAsState()
    val colors = LocalAppColors.current
    var adTestMode by remember { mutableStateOf(DebugFlags.isAdTestModeEnabled(context)) }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("AdMob diagnostics", text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun forceReload() {
        AdManager.resetFailureCounters(context)
        Toast.makeText(context, "Counters reset, reloading ads…", Toast.LENGTH_SHORT).show()
    }

    fun toggleTestMode(enabled: Boolean) {
        adTestMode = enabled
        DebugFlags.setAdTestModeEnabled(context, enabled)
        if (enabled) {
            val ids = AdManager.applyTestMode(context)
            AdManager.resetFailureCounters(context)
            Toast.makeText(context, "Test mode ON. Device added: ${ids.firstOrNull() ?: "none"}", Toast.LENGTH_LONG).show()
        } else {
            AdManager.clearTestMode()
            Toast.makeText(context, "Test mode OFF. Restart app to clear fully.", Toast.LENGTH_LONG).show()
        }
    }

    val report = buildString {
        appendLine("AdMob Diagnostics")
        appendLine("---- IDs (verify these match your AdMob console) ----")
        appendLine("App:          ${diag.appId}")
        appendLine("Banner:       ${diag.bannerAdUnitId}")
        appendLine("Interstitial: ${diag.interstitialAdUnitId}")
        appendLine("Rewarded:     ${diag.rewardedAdUnitId}")
        appendLine("---- State ----")
        appendLine("SDK initialized: ${diag.isMobileAdsInitialized}")
        appendLine("Consent obtained: ${diag.isConsentObtained}")
        appendLine("Ad-free:          ${diag.isAdFree}")
        appendLine("Interstitial ready: ${diag.isInterstitialReady}")
        appendLine("Rewarded ready:     ${diag.isRewardedReady}")
        appendLine("Ad test mode:       ${diag.isAdTestMode}")
        appendLine("---- Failures (auto-reset on app foreground) ----")
        appendLine("Consecutive interstitial failures: ${diag.consecutiveInterstitialFailures} / ${diag.maxFailedLoads}")
        appendLine("Consecutive rewarded failures:     ${diag.consecutiveRewardedFailures} / ${diag.maxFailedLoads}")
        appendLine("Min interstitial interval:         ${diag.minInterstitialIntervalMs} ms")
        appendLine("Last interstitial error: ${diag.lastInterstitialError ?: "none"}")
        if (diag.lastInterstitialErrorCode != null) {
            appendLine("Interstitial error decoded: ${diag.lastInterstitialErrorCode} = ${diag.lastInterstitialErrorName}")
        }
        appendLine("Last rewarded error:     ${diag.lastRewardedError ?: "none"}")
        if (diag.lastRewardedErrorCode != null) {
            appendLine("Rewarded error decoded: ${diag.lastRewardedErrorCode} = ${diag.lastRewardedErrorName}")
        }
    }

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = {
            Text("AdMob Diagnostics", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Open the Ad Inspector for official Google fill rate, request and impression data. Or copy this local snapshot to share.",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Text("Ad Unit IDs", color = Purple500, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                DiagLine("App", diag.appId)
                DiagLine("Banner", diag.bannerAdUnitId)
                DiagLine("Interstitial", diag.interstitialAdUnitId)
                DiagLine("Rewarded", diag.rewardedAdUnitId)

                Spacer(Modifier.height(12.dp))
                Text("SDK State", color = Purple500, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                DiagLine("MobileAds initialized", diag.isMobileAdsInitialized.toString())
                DiagLine("Consent obtained", diag.isConsentObtained.toString())
                DiagLine("Ad-free mode", diag.isAdFree.toString())
                DiagLine("Interstitial ready", diag.isInterstitialReady.toString())
                DiagLine("Rewarded ready", diag.isRewardedReady.toString())
                DiagLine("Ad test mode", diag.isAdTestMode.toString())

                Spacer(Modifier.height(12.dp))
                Text("Failures & Errors", color = Purple500, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                DiagLine("Interstitial fail streak", "${diag.consecutiveInterstitialFailures} / ${diag.maxFailedLoads}")
                DiagLine("Rewarded fail streak", "${diag.consecutiveRewardedFailures} / ${diag.maxFailedLoads}")
                DiagLine("Min interstitial interval", "${diag.minInterstitialIntervalMs} ms")
                DiagLine("Last interstitial error", diag.lastInterstitialError ?: "none")
                if (diag.lastInterstitialErrorCode != null) {
                    DiagLine("Interstitial error decoded", "${diag.lastInterstitialErrorCode} = ${diag.lastInterstitialErrorName}")
                }
                DiagLine("Last rewarded error", diag.lastRewardedError ?: "none")
                if (diag.lastRewardedErrorCode != null) {
                    DiagLine("Rewarded error decoded", "${diag.lastRewardedErrorCode} = ${diag.lastRewardedErrorName}")
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "code=3 (NO_FILL) means AdMob received the request but has no ad to serve. Usually: new ad units, low impression history, or no demand for this device/region. Wait 24h, or check AdMob console ad-unit status.",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ad test mode (this device)", color = colors.textPrimary, fontWeight = FontWeight.W600, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Registers this phone as a test device. Required to open the Ad Inspector and shows test ads that always fill.",
                                color = colors.textTertiary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = adTestMode,
                            onCheckedChange = { toggleTestMode(it) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = {
                        Toast.makeText(context, "Opening Ad Inspector…", Toast.LENGTH_SHORT).show()
                        try {
                            MobileAds.openAdInspector(context, OnAdInspectorClosedListener { error ->
                                if (error != null) {
                                    android.util.Log.e("AdManager", "Ad Inspector closed with error: code=${error.code} ${error.message}")
                                    Toast.makeText(context, "Inspector error code=${error.code}: ${error.message}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Inspector closed", Toast.LENGTH_SHORT).show()
                                }
                            })
                        } catch (t: Throwable) {
                            android.util.Log.e("AdManager", "Ad Inspector open threw ${t.javaClass.simpleName}: ${t.message}", t)
                            Toast.makeText(context, "Inspector unavailable (${t.javaClass.simpleName}): ${t.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = adTestMode
                ) {
                    Text(
                        "Open Ad Inspector",
                        color = if (adTestMode) Purple500 else colors.textTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = { forceReload() }) {
                    Text("Reset & Reload", color = Purple500)
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { copyToClipboard(report) }) {
                    Text("Copy", color = colors.textSecondary)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = colors.textSecondary)
                }
            }
        }
    )
}

@Composable
private fun DiagLine(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            "$label:",
            color = colors.textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(140.dp)
        )
        Text(
            value,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
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
