package com.dhanuk.lofiga.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.ads.BannerAd
import com.dhanuk.lofiga.ui.components.LofigaNavigationBar
import com.dhanuk.lofiga.ui.screens.*

import android.content.Context
import android.content.ContextWrapper

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Interstitials on tab switch are policy-safe only with a long cooldown.
// Keep this in sync with AdManager.MIN_INTERSTITIAL_INTERVAL (2 minutes).
private const val TAB_SWITCH_AD_COOLDOWN_MS = 120_000L

@Composable
fun LofigaMainApp(
    viewModel: MainViewModel
) {
    // ═════════════════════════════════════════════════════════════════════════════
    // Lofiga v2 tab order: Home (0), Library (1), Player (2), Settings (3)
    // ═════════════════════════════════════════════════════════════════════════════
    var selectedIndex by remember { mutableStateOf(0) }
    var previousIndex by remember { mutableStateOf(0) }
    var lastInterstitialTime by remember { mutableStateOf(0L) }
    val context = LocalContext.current

    // Show the now-playing dot when a track is loaded and the user isn't on Player.
    val currentTrack by viewModel.currentTrack.collectAsState()
    val showNowPlayingBadge = currentTrack != null && selectedIndex != 2

    LaunchedEffect(selectedIndex) {
        if (previousIndex != selectedIndex) {
            val now = System.currentTimeMillis()
            if (now - lastInterstitialTime >= TAB_SWITCH_AD_COOLDOWN_MS) {
                context.findActivity()?.let { act ->
                    AdManager.showInterstitial(act)
                }
                lastInterstitialTime = now
            }
            previousIndex = selectedIndex
        }
    }

    Scaffold(
        bottomBar = {
            LofigaNavigationBar(
                selectedIndex = selectedIndex,
                onItemSelected = { index ->
                    selectedIndex = index
                },
                showNowPlayingBadge = showNowPlayingBadge
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedIndex) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onSongSelected = { track ->
                        viewModel.loadTrack(track)
                        selectedIndex = 2
                    },
                    onEditConfig = { config ->
                        val success = viewModel.editConfig(config)
                        if (success) selectedIndex = 2
                    },
                    onBrowseAll = { selectedIndex = 1 },
                    onMixSelected = { path, name ->
                        viewModel.loadTrackFromFile(path, name)
                        selectedIndex = 2
                    }
                )
                1 -> LibraryScreen(
                    viewModel = viewModel,
                    onSongSelected = { track ->
                        viewModel.loadTrack(track)
                        selectedIndex = 2
                    }
                )
                2 -> PlayerScreen(
                    viewModel = viewModel,
                    onBack = { selectedIndex = 0 }
                )
                3 -> SettingsScreen(
                    viewModel = viewModel
                )
            }

            BannerAd(
                visible = selectedIndex != 2,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            )
        }
    }
}
