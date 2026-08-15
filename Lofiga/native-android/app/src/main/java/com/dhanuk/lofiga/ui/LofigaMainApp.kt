package com.dhanuk.lofiga.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
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
private const val TAB_SWITCH_AD_COOLDOWN_MS = AdManager.MIN_INTERSTITIAL_INTERVAL

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

    // Navigation history stack — back press pops the last screen.
    // Home (0) is always the bottom of the stack.
    val backStack = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    val activity = context.findActivity()

    fun navigateTo(index: Int) {
        if (index == selectedIndex) return
        if (backStack.lastOrNull() != selectedIndex) {
            backStack.add(selectedIndex)
        }
        if (backStack.size > 4) backStack.removeAt(0)
        selectedIndex = index
    }

    fun handleBackPress() {
        if (backStack.isNotEmpty()) {
            val prev = backStack.removeAt(backStack.lastIndex)
            selectedIndex = prev
        } else {
            activity?.finish()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        handleBackPress()
    }

    // Show the now-playing dot when a track is loaded and the user isn't on Player.
    val currentTrack by viewModel.currentTrack.collectAsState()
    val showNowPlayingBadge = currentTrack != null && selectedIndex != 2

    val isAdFree by AdManager.isAdFree.collectAsState()

    LaunchedEffect(selectedIndex) {
        if (previousIndex != selectedIndex) {
            // Never interrupt the Player tab — the user just picked a track and
            // an interstitial before seeing it is a bad experience.
            if (selectedIndex != 2) {
                val now = System.currentTimeMillis()
                if (now - lastInterstitialTime >= TAB_SWITCH_AD_COOLDOWN_MS) {
                    context.findActivity()?.let { act ->
                        AdManager.showInterstitial(act)
                    }
                    lastInterstitialTime = now
                }
            }
            previousIndex = selectedIndex
        }
    }

    Scaffold(
        bottomBar = {
            LofigaNavigationBar(
                selectedIndex = selectedIndex,
                onItemSelected = { index -> navigateTo(index) },
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
                        navigateTo(2)
                    },
                    onEditConfig = { config ->
                        val success = viewModel.editConfig(config)
                        if (success) navigateTo(2)
                    },
                    onBrowseAll = { navigateTo(1) },
                    onMixSelected = { path, name ->
                        viewModel.loadTrackFromFile(path, name)
                        navigateTo(2)
                    },
                    onRequestPermission = {
                        (context.findActivity() as? com.dhanuk.lofiga.MainActivity)?.requestPermissionsFromUi()
                    }
                )
                1 -> LibraryScreen(
                    viewModel = viewModel,
                    onSongSelected = { track ->
                        viewModel.loadTrack(track)
                        navigateTo(2)
                    },
                    onRequestPermission = {
                        (context.findActivity() as? com.dhanuk.lofiga.MainActivity)?.requestPermissionsFromUi()
                    }
                )
                2 -> PlayerScreen(
                    viewModel = viewModel,
                    onBack = { handleBackPress() }
                )
                3 -> SettingsScreen(
                    viewModel = viewModel
                )
            }

            // Recreate the banner when the ad-free state flips: AdManager destroys
            // the AdView on grant, so it must be rebuilt on expiry.
            key(isAdFree) {
                BannerAd(
                    visible = selectedIndex != 2,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                )
            }
        }
    }
}
