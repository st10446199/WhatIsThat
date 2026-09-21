/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mohammedanaspatel.whatisthat.data.ScanResult
import com.mohammedanaspatel.whatisthat.ml.Classifier
import com.mohammedanaspatel.whatisthat.ui.screens.CameraScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ResultScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ScanningScreen
import com.mohammedanaspatel.whatisthat.ui.screens.StumpedScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ThemePickerSheet
import com.mohammedanaspatel.whatisthat.ui.theme.PRESETS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The 4 screens in the app. Scanning and Result now carry real data along
 * with them (the captured photo, and the photo + real prediction) instead of
 * just being empty markers - this is what changed for Commit 5.
 */
sealed class Screen {
    data object Camera : Screen()
    data class Scanning(val bitmap: Bitmap) : Screen()
    data class Result(val result: ScanResult, val bitmap: Bitmap?) : Screen()
    data object Stumped : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatIsThatApp()
        }
    }
}

/**
 * Commit 5: real capture -> classify -> route flow.
 *
 * The Classifier is loaded once here (not per-screen) since constructing it
 * reads the model file from disk and builds a TFLite Interpreter - fairly
 * cheap for a small model, but still wasteful to repeat every time the
 * Camera screen re-appears. It's loaded on a background thread so a slow
 * model file doesn't freeze the very first frame the person sees.
 *
 * If the model/labels files are missing (e.g. not yet added to assets/,
 * see the project README), loading fails gracefully - the person can still
 * use the app, they'll just always land on the Stumped screen instead of
 * the app crashing outright.
 */
@Composable
fun WhatIsThatApp() {
    var currentTheme by remember { mutableStateOf(PRESETS.first { it.id == "obsidian" }) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera) }
    var showThemePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var classifier by remember { mutableStateOf<Classifier?>(null) }

    LaunchedEffect(Unit) {
        classifier = try {
            withContext(Dispatchers.IO) { Classifier(context) }
        } catch (e: Exception) {
            // Model/labels missing or failed to load - null classifier means
            // every capture falls through to the Stumped screen below,
            // rather than crashing the whole app.
            null
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
                    onCaptured = { bitmap ->
                        currentScreen = Screen.Scanning(bitmap)
                    },
                    onOpenThemes = { showThemePicker = true }
                )

                is Screen.Scanning -> {
                    val loadedClassifier = classifier
                    if (loadedClassifier != null) {
                        ScanningScreen(
                            theme = currentTheme,
                            bitmap = screen.bitmap,
                            classifier = loadedClassifier,
                            onResult = { result ->
                                currentScreen = if (result != null) {
                                    Screen.Result(result, screen.bitmap)
                                } else {
                                    Screen.Stumped
                                }
                            },
                            onOpenThemes = { showThemePicker = true }
                        )
                    } else {
                        // Classifier still loading (or failed) - fall back to
                        // Stumped rather than showing a broken Scanning screen
                        // that can never finish.
                        StumpedScreen(
                            theme = currentTheme,
                            onTryAgain = { currentScreen = Screen.Camera },
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

                is Screen.Stumped -> StumpedScreen(
                    theme = currentTheme,
                    onTryAgain = { currentScreen = Screen.Camera },
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
