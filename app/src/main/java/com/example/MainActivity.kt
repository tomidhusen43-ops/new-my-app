package com.example

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.engine.CardProcessingEngine
import com.example.model.CardAnalysis
import com.example.model.CardCorners
import com.example.security.SecurityManager
import com.example.ui.screens.BatchScreen
import com.example.ui.screens.CornerEditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.PinMode
import com.example.ui.screens.ResultScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

sealed interface AppDestination {
    data object Home : AppDestination
    data class CornerEditor(val bitmap: Bitmap, val corners: CardCorners) : AppDestination
    data class Result(val original: Bitmap, val reconstructed: Bitmap, val analysis: CardAnalysis) : AppDestination
    data object Batch : AppDestination
    data object PinSetup : AppDestination
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SecurityManager.applyScreenSecurity(this)
        SecurityManager.checkAndLock(this)
        setContent {
            MyApplicationTheme {
                CardCloneApp(
                    onExitApp = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SecurityManager.applyScreenSecurity(this)
    }
}

@Composable
fun CardCloneApp(
    onExitApp: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<AppDestination>(AppDestination.Home) }
    var activeManualCorners by remember { mutableStateOf<CardCorners?>(null) }

    // If app is locked with PIN, present PIN unlock screen first
    if (SecurityManager.isAppLocked.value) {
        PinLockScreen(
            mode = PinMode.UNLOCK,
            onSuccess = {
                SecurityManager.unlock()
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (val screen = currentScreen) {
            is AppDestination.Home -> {
                HomeScreen(
                    onNavigateToResult = { orig, recon, analysis ->
                        currentScreen = AppDestination.Result(orig, recon, analysis)
                    },
                    onNavigateToCornerEditor = { bmp, corners ->
                        currentScreen = AppDestination.CornerEditor(bmp, activeManualCorners ?: corners)
                    },
                    onNavigateToBatch = {
                        currentScreen = AppDestination.Batch
                    },
                    onOpenPinSetup = {
                        currentScreen = AppDestination.PinSetup
                    },
                    manualCorners = activeManualCorners
                )
            }

            is AppDestination.PinSetup -> {
                BackHandler {
                    currentScreen = AppDestination.Home
                }
                PinLockScreen(
                    mode = PinMode.SETUP,
                    onSuccess = {
                        currentScreen = AppDestination.Home
                    },
                    onCancel = {
                        currentScreen = AppDestination.Home
                    }
                )
            }

            is AppDestination.CornerEditor -> {
                BackHandler {
                    currentScreen = AppDestination.Home
                }
                CornerEditorScreen(
                    bitmap = screen.bitmap,
                    initialCorners = screen.corners,
                    onApplyCorners = { updatedCorners ->
                        activeManualCorners = updatedCorners
                        currentScreen = AppDestination.Home
                    },
                    onCancel = {
                        currentScreen = AppDestination.Home
                    }
                )
            }

            is AppDestination.Result -> {
                BackHandler {
                    currentScreen = AppDestination.Home
                }
                ResultScreen(
                    originalBitmap = screen.original,
                    reconstructedBitmap = screen.reconstructed,
                    analysis = screen.analysis,
                    onBack = {
                        currentScreen = AppDestination.Home
                    },
                    onRebuild = {
                        coroutineScope.launch {
                            val quality = CardProcessingEngine.calculateImageQuality(screen.original)
                            val prep = CardProcessingEngine.PreparedImage(
                                bitmap = screen.original,
                                originalWidth = screen.analysis.originalWidth,
                                originalHeight = screen.analysis.originalHeight,
                                processingWidth = screen.analysis.processingWidth,
                                processingHeight = screen.analysis.processingHeight,
                                wasResized = screen.analysis.wasResized,
                                quality = quality
                            )
                            val (newAnalysis, newRecon) = CardProcessingEngine.processCardPipeline(
                                prepared = prep,
                                manualCorners = activeManualCorners
                            )
                            currentScreen = AppDestination.Result(screen.original, newRecon, newAnalysis)
                        }
                    }
                )
            }

            is AppDestination.Batch -> {
                BackHandler {
                    currentScreen = AppDestination.Home
                }
                BatchScreen(
                    onBack = {
                        currentScreen = AppDestination.Home
                    },
                    onInspectCard = { orig, recon, analysis ->
                        currentScreen = AppDestination.Result(orig, recon, analysis)
                    }
                )
            }
        }
    }
}
