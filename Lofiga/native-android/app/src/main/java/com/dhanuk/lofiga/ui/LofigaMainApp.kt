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

private const val TAB_SWITCH_AD_COOLDOWN_MS = 60_000L

@Composable
fun LofigaMainApp(
    viewModel: MainViewModel
) {
    var selectedIndex by remember { mutableStateOf(0) }
    var previousIndex by remember { mutableStateOf(0) }
    var lastInterstitialTime by remember { mutableStateOf(0L) }
    val context = LocalContext.current

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
                }
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
                        selectedIndex = 1
                    },
                    onEditConfig = { config ->
                        val success = viewModel.editConfig(config)
                        if (success) {
                            selectedIndex = 1
                        }
                    }
                )
                1 -> PlayerScreen(
                    viewModel = viewModel,
                    onBack = { selectedIndex = 0 }
                )
                2 -> LibraryScreen(
                    viewModel = viewModel,
                    onFileSelected = { path, name ->
                        viewModel.loadTrackFromFile(path, name)
                        selectedIndex = 1
                    }
                )
                3 -> SettingsScreen(
                    viewModel = viewModel
                )
            }

            BannerAd(
                visible = selectedIndex != 1,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            )
        }
    }
}
