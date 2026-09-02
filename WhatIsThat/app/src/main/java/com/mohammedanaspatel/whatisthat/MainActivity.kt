/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat

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
import com.mohammedanaspatel.whatisthat.data.MOCK_RESULT
import com.mohammedanaspatel.whatisthat.data.ScanResult
import com.mohammedanaspatel.whatisthat.ui.screens.CameraScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ResultScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ScanningScreen
import com.mohammedanaspatel.whatisthat.ui.screens.StumpedScreen
import com.mohammedanaspatel.whatisthat.ui.screens.ThemePickerSheet
import com.mohammedanaspatel.whatisthat.ui.theme.PRESETS
import com.mohammedanaspatel.whatisthat.ui.theme.Theme

/** The 4 screens in the app - mirrors the screen states in App.tsx. */
sealed class Screen {
    data object Camera : Screen()
    data object Scanning : Screen()
    data class Result(val result: ScanResult) : Screen()
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
 * Commit 3: crossfade transitions between screens (AnimatedContent) and the
 * theme picker bottom sheet wired into app state - tapping the 🎨 button on
 * any screen opens it, selecting a theme applies instantly everywhere.
 */
@Composable
fun WhatIsThatApp() {
    var currentTheme by remember { mutableStateOf(PRESETS.first { it.id == "obsidian" }) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera) }
    var showThemePicker by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = currentTheme.bg) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                is Screen.Camera -> CameraScreen(
                    theme = currentTheme,
                    onCapture = { currentScreen = Screen.Scanning },
                    onOpenThemes = { showThemePicker = true }
                )

                is Screen.Scanning -> ScanningScreen(
                    theme = currentTheme,
                    onScanComplete = {
                        currentScreen = Screen.Result(MOCK_RESULT)
                        // To preview the Stumped screen instead, swap the line above for:
                        // currentScreen = Screen.Stumped
                    },
                    onOpenThemes = { showThemePicker = true }
                )

                is Screen.Result -> ResultScreen(
                    theme = currentTheme,
                    result = screen.result,
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
