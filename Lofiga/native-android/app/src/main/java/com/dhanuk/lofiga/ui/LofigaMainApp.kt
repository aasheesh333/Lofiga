package com.dhanuk.lofiga.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dhanuk.lofiga.model.SavedConfig
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
                onSongSelected = { path, name ->
                    viewModel.loadTrackFromFile(path, name)
                    selectedIndex = 1 // Switch to player after selecting song
                },
                onEditConfig = { config ->
                    viewModel.editConfig(config)
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
                    selectedIndex = 1 // Switch to player after selecting song
                }
            )
            3 -> SettingsScreen(
                viewModel = viewModel
            )
        }
    }
}
