/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mohammedanaspatel.whatisthat.data.FocusTarget
import com.mohammedanaspatel.whatisthat.data.ScanResult
import com.mohammedanaspatel.whatisthat.data.ScanHistoryItem
import com.mohammedanaspatel.whatisthat.data.ScanHistoryStore
import com.mohammedanaspatel.whatisthat.data.HistoryLayout
import com.mohammedanaspatel.whatisthat.ml.Classifier
import com.mohammedanaspatel.whatisthat.ui.screens.CameraScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ErrorScreen
import com.mohammedanaspatel.whatisthat.ui.screens.HistoryScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ResultScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ScanningScreen
import com.mohammedanaspatel.whatisthat.ui.screens.StumpedScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ThemePickerSheet
import com.mohammedanaspatel.whatisthat.ui.theme.PRESETS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APP_TAG = "WhatIsThat"

sealed class Screen {
    data object Camera : Screen()
    data class Scanning(val bitmap: Bitmap, val focusTarget: FocusTarget?) : Screen()
    data class Result(val result: ScanResult, val bitmap: Bitmap) : Screen()
    data class Unknown(val bitmap: Bitmap, val confidencePercent: Int?) : Screen()
    data class Error(val message: String) : Screen()
    data object History : Screen()
}

private sealed class ClassifierState {
    data object Loading : ClassifierState()
    data class Ready(val classifier: Classifier) : ClassifierState()
    data class Error(val message: String) : ClassifierState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatIsThatApp()
        }
    }
}

@Composable
fun WhatIsThatApp() {
    var currentTheme by remember { mutableStateOf(PRESETS.first { it.id == "obsidian" }) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera) }
    var showThemePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val historyStore = remember { ScanHistoryStore(context.applicationContext) }
    var scanHistory by remember { mutableStateOf<List<ScanHistoryItem>>(historyStore.load()) }
    var historyLayout by remember { mutableStateOf(historyStore.loadLayout()) }
    var classifierState by remember { mutableStateOf<ClassifierState>(ClassifierState.Loading) }
    var classifierLoadAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(classifierLoadAttempt) {
        classifierState = ClassifierState.Loading
        classifierState = try {
            val loadedClassifier = withContext(Dispatchers.IO) {
                Classifier(context.applicationContext)
            }
            ClassifierState.Ready(loadedClassifier)
        } catch (exception: Exception) {
            Log.e(APP_TAG, "Failed to load local classifier", exception)
            ClassifierState.Error(
                "The local AI model could not be loaded. Please try again."
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = currentTheme.bg) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                is Screen.Camera -> CameraScreen(
                    theme = currentTheme,
                    onCaptured = { bitmap, focusTarget ->
                        currentScreen = Screen.Scanning(bitmap, focusTarget)
                    },
                    onCameraError = { message ->
                        currentScreen = Screen.Error(message)
                    },
                    onOpenHistory = { currentScreen = Screen.History },
                    onOpenThemes = { showThemePicker = true }
                )

                is Screen.Scanning -> {
                    when (val state = classifierState) {
                        is ClassifierState.Loading -> ScanningScreen(
                            theme = currentTheme,
                            bitmap = screen.bitmap,
                            focusTarget = screen.focusTarget,
                            classifier = null,
                            onResult = { result ->
                                scanHistory = historyStore.add(result, screen.bitmap)
                                currentScreen = Screen.Result(result, screen.bitmap)
                            },
                            onUnknown = { confidence ->
                                currentScreen = Screen.Unknown(screen.bitmap, confidence)
                            },
                            onError = { message ->
                                currentScreen = Screen.Error(message)
                            },
                            onOpenThemes = { showThemePicker = true }
                        )

                        is ClassifierState.Ready -> ScanningScreen(
                            theme = currentTheme,
                            bitmap = screen.bitmap,
                            focusTarget = screen.focusTarget,
                            classifier = state.classifier,
                            onResult = { result ->
                                scanHistory = historyStore.add(result, screen.bitmap)
                                currentScreen = Screen.Result(result, screen.bitmap)
                            },
                            onUnknown = { confidence ->
                                currentScreen = Screen.Unknown(screen.bitmap, confidence)
                            },
                            onError = { message ->
                                currentScreen = Screen.Error(message)
                            },
                            onOpenThemes = { showThemePicker = true }
                        )

                        is ClassifierState.Error -> ErrorScreen(
                            theme = currentTheme,
                            message = state.message,
                            onRetry = {
                                classifierLoadAttempt += 1
                            },
                            onBackToCamera = { currentScreen = Screen.Camera },
                            onOpenThemes = { showThemePicker = true }
                        )
                    }
                }

                is Screen.Result -> ResultScreen(
                    theme = currentTheme,
                    result = screen.result,
                    capturedBitmap = screen.bitmap,
                    onScanAgain = { currentScreen = Screen.Camera },
                    onOpenThemes = { showThemePicker = true }
                )

                is Screen.Unknown -> StumpedScreen(
                    theme = currentTheme,
                    capturedBitmap = screen.bitmap,
                    confidencePercent = screen.confidencePercent,
                    onTryAgain = { currentScreen = Screen.Camera },
                    onOpenThemes = { showThemePicker = true }
                )

                is Screen.Error -> ErrorScreen(
                    theme = currentTheme,
                    message = screen.message,
                    onRetry = {
                        classifierLoadAttempt += 1
                        currentScreen = Screen.Camera
                    },
                    onBackToCamera = { currentScreen = Screen.Camera },
                    onOpenThemes = { showThemePicker = true }
                )

                is Screen.History -> HistoryScreen(
                    theme = currentTheme,
                    items = scanHistory,
                    layout = historyLayout,
                    onBack = { currentScreen = Screen.Camera },
                    onLayoutChange = { selectedLayout ->
                        historyLayout = selectedLayout
                        historyStore.saveLayout(selectedLayout)
                    },
                    onClear = {
                        historyStore.clear()
                        scanHistory = emptyList()
                    },
                    onOpenThemes = { showThemePicker = true }
                )
            }
        }

        if (showThemePicker) {
            ThemePickerSheet(
                currentTheme = currentTheme,
                onThemeSelected = { currentTheme = it },
                onDismiss = { showThemePicker = false }
            )
        }
    }
}
