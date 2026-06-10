package com.dhanuk.lofiga

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhanuk.lofiga.ads.AdManager
import com.dhanuk.lofiga.model.SavedConfig
import com.dhanuk.lofiga.ui.LofigaMainApp
import com.dhanuk.lofiga.ui.MainViewModel
import com.dhanuk.lofiga.ui.components.LofigaNavigationBar
import com.dhanuk.lofiga.ui.screens.*
import com.dhanuk.lofiga.ui.theme.DarkSurface
import com.dhanuk.lofiga.ui.theme.LofigaTheme
import com.dhanuk.lofiga.ui.theme.Purple500
import com.dhanuk.lofiga.ui.theme.White38
import com.dhanuk.lofiga.ui.theme.White60
import com.dhanuk.lofiga.util.SettingsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {

    private var showPermissionRationale by mutableStateOf(false)
    private var hasRequestedPermissions by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted && hasRequestedPermissions) {
            Toast.makeText(this, "Some features may not work without storage access", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestAudioPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }
        }

        if (permissions.isNotEmpty()) {
            hasRequestedPermissions = true
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions) {
            if (!hasRequestedPermissions) {
                showPermissionRationale = true
            } else {
                requestAudioPermissions()
            }
        }
    }

    fun proceedWithPermissions() {
        showPermissionRationale = false
        requestAudioPermissions()
    }

    fun skipPermissions() {
        showPermissionRationale = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdManager.requestConsent(this)
        checkAndRequestPermissions()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settingsManager.settingsFlow.collectAsState(initial = SettingsManager.AppSettings())

            LofigaTheme(darkTheme = settings.isDarkMode) {
                var onboardingComplete by remember { mutableStateOf(false) }

                LaunchedEffect(onboardingComplete) {
                    if (onboardingComplete) {
                        viewModel.settingsManager.setHasSeenOnboarding(true)
                    }
                }

                if (showPermissionRationale) {
                    PermissionRationaleDialog(
                        onAccept = { proceedWithPermissions() },
                        onSkip = { skipPermissions() }
                    )
                }

                OnboardingDialog(
                    hasSeenOnboarding = settings.hasSeenOnboarding || onboardingComplete,
                    onOnboardingComplete = {
                        onboardingComplete = true
                    },
                    onSkip = {
                        onboardingComplete = true
                    }
                ) {
                    LofigaMainApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun OnboardingDialog(
    hasSeenOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    onSkip: () -> Unit,
    content: @Composable () -> Unit
) {
    if (hasSeenOnboarding) {
        content()
    } else {
        OnboardingScreen(
            onGetStarted = onOnboardingComplete,
            onSkip = onSkip
        )
    }
}

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    var dontShowAgain by remember { mutableStateOf(false) }

    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.MusicNote,
            title = "Transform Your Music",
            description = "Convert any song into relaxing lofi beats with easy-to-use effects"
        ),
        OnboardingPage(
            icon = Icons.Default.Tune,
            title = "Customize Your Sound",
            description = "Adjust tempo, pitch, reverb, and add atmospheric layers like rain or vinyl"
        ),
        OnboardingPage(
            icon = Icons.Default.CloudDownload,
            title = "Export & Share",
            description = "Export your lofi creations in high quality and share with friends"
        )
    )

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF191022),
                            Color(0xFF2D243A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Skip button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSkip) {
                        Text(
                            "Skip",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Page content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Icon with gradient background
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF993DF5),
                                        Color(0xFF3DF5E6)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = pages[currentPage].icon,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Title
                    Text(
                        text = pages[currentPage].title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description
                    Text(
                        text = pages[currentPage].description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Page indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(pages.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (index == currentPage)
                                            Color(0xFF993DF5)
                                        else
                                            Color.White.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }

                // Bottom section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPage > 0) {
                            TextButton(onClick = { currentPage-- }) {
                                Text("Back", color = Color.White.copy(alpha = 0.7f))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                if (currentPage < pages.size - 1) {
                                    currentPage++
                                } else {
                                    onGetStarted()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF993DF5)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                if (currentPage < pages.size - 1) "Next" else "Get Started",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
fun PermissionRationaleDialog(
    onAccept: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = DarkSurface,
        title = {
            Text(
                "Music Access Needed",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Lofiga needs a few permissions to work:",
                    color = White60,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text("• Scan your songs library", color = White38)
                Text("• Load songs for editing", color = White38)
                Text("• Export your lofi mixes", color = White38)
                Text("• Send you music updates and tips", color = White38)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Your music stays on your device - we never upload or share your files.",
                    color = White38,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Purple500),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Allow Access")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Not Now", color = White38)
            }
        }
    )
}