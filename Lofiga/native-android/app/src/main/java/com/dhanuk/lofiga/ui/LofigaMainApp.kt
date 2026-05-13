package com.dhanuk.lofiga.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dhanuk.lofiga.ui.components.LofigaNavigationBar
import com.dhanuk.lofiga.ui.screens.*

@Composable
fun LofigaMainApp(
    viewModel: MainViewModel
) {
    var selectedIndex by remember { mutableStateOf(0) }

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
    }
}
