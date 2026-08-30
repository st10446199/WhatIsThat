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
 * Commit 2: static navigation shell - plain "when" switch between screens,
 * no crossfade (Commit 3 adds AnimatedContent) and no theme picker sheet
 * wiring yet (also Commit 3). Theme is fixed to Obsidian for now.
 */
@Composable
fun WhatIsThatApp() {
    var currentTheme by remember { mutableStateOf(PRESETS.first { it.id == "obsidian" }) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera) }

    Surface(modifier = Modifier.fillMaxSize(), color = currentTheme.bg) {
        when (val screen = currentScreen) {
            is Screen.Camera -> CameraScreen(
                theme = currentTheme,
                onCapture = { currentScreen = Screen.Scanning }
            )

            is Screen.Scanning -> ScanningScreen(
                theme = currentTheme,
                onScanComplete = {
                    currentScreen = Screen.Result(MOCK_RESULT)
                    // To preview the Stumped screen instead, swap the line above for:
                    // currentScreen = Screen.Stumped
                }
            )

            is Screen.Result -> ResultScreen(
                theme = currentTheme,
                result = screen.result,
                onScanAgain = { currentScreen = Screen.Camera }
            )

            is Screen.Stumped -> StumpedScreen(
                theme = currentTheme,
                onTryAgain = { currentScreen = Screen.Camera }
            )
        }
    }
}
