package com.dhanuk.lofiga.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.ui.components.LofigaNavigationBar
import com.dhanuk.lofiga.ui.screens.*

@Composable
fun LofigaMainApp(
    viewModel: MainViewModel
) {
    var selectedIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (viewModel.audioEngine.isPlaying.value && selectedIndex != 1) {
                    selectedIndex = 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        AdManager.loadInterstitial(context)
        AdManager.loadRewarded(context)
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
                },
                modifier = Modifier.padding(paddingValues)
            )
            1 -> PlayerScreen(
                viewModel = viewModel,
                onBack = { selectedIndex = 0 },
                modifier = Modifier.padding(paddingValues)
            )
            2 -> LibraryScreen(
                viewModel = viewModel,
                onFileSelected = { path, name ->
                    viewModel.loadTrackFromFile(path, name)
                    selectedIndex = 1
                },
                modifier = Modifier.padding(paddingValues)
            )
            3 -> SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
