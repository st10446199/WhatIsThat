/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedanaspatel.whatisthat.ui.theme.Theme
import com.mohammedanaspatel.whatisthat.ui.theme.accentTokens

/**
 * Camera screen - static version (Commit 2). Matches the Figma layout exactly:
 * viewfinder with corner brackets, "?" capture button, offline badge, wordmark.
 *
 * No animations yet (corner pulse, ring expand, shutter flash all land in
 * Commit 3), and no theme picker wiring yet (also Commit 3) - onOpenThemes
 * is accepted but unused for now so the signature doesn't need to change later.
 */
@Composable
fun CameraScreen(
    theme: Theme,
    onCapture: () -> Unit,
    onOpenThemes: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(theme = theme, onOpenThemes = onOpenThemes)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Viewfinder frame - TODO: swap for CameraX PreviewView in Commit 4
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                        .background(if (theme.isDark) Color(0xFF141420) else Color(0xFFE8DDD8)),
                    contentAlignment = Alignment.Center
                ) {
                    // Rotated square placeholder representing "an object to detect"
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.surface)
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(theme.accent)
                    )
                }

                ViewfinderCorners(accentColor = theme.accent)

                Text(
                    text = "Point at anything curious",
                    color = theme.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CaptureButton(theme = theme, onTap = onCapture)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TAP TO IDENTIFY",
                    color = theme.textMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** Four static corner brackets - pulsing animation added in Commit 3. */
@Composable
private fun ViewfinderCorners(accentColor: Color) {
    val cornerSize = 32.dp
    val strokeWidth = 3.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopStart).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, accentColor),
                    RoundedCornerShape(topStart = 8.dp)
                )
        )
        Box(
            Modifier.align(Alignment.TopEnd).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, accentColor),
                    RoundedCornerShape(topEnd = 8.dp)
                )
        )
        Box(
            Modifier.align(Alignment.BottomStart).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, accentColor),
                    RoundedCornerShape(bottomStart = 8.dp)
                )
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, accentColor),
                    RoundedCornerShape(bottomEnd = 8.dp)
                )
        )
    }
}

/** Static capture button - expanding ring pulse added in Commit 3. */
@Composable
private fun CaptureButton(theme: Theme, onTap: () -> Unit) {
    val tokens = accentTokens(theme.accent)

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(theme.accent, tokens.dim)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "?", color = Color.White, fontSize = 30.sp)
    }
}

@Composable
private fun TopBar(theme: Theme, onOpenThemes: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 48.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Wordmark(theme = theme)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeButton(theme = theme, onClick = onOpenThemes)
            Spacer(modifier = Modifier.width(8.dp))
            OfflineBadge(theme = theme)
        }
    }
}

/**
 * Visual-only for now (Commit 2) - onClick fires but there's no sheet to open
 * yet. Commit 3 wires this up to the real ThemePickerSheet.
 */
@Composable
internal fun ThemeButton(theme: Theme, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .background(theme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "\uD83C\uDFA8", fontSize = 15.sp) // 🎨
    }
}

@Composable
internal fun Wordmark(theme: Theme) {
    Row {
        Text("What", color = theme.text, fontSize = 27.sp)
        Text("Is", color = theme.accent, fontSize = 27.sp)
        Text("That", color = theme.text, fontSize = 27.sp)
        Text("?", color = theme.accent, fontSize = 27.sp)
    }
}

@Composable
internal fun OfflineBadge(theme: Theme) {
    val tokens = accentTokens(theme.accent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, tokens.border, RoundedCornerShape(50))
            .background(if (theme.isDark) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(theme.accent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "OFFLINE", color = theme.accent, fontSize = 11.sp, letterSpacing = 0.5.sp)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun CameraScreenPreview() {
    CameraScreen(
        theme = com.mohammedanaspatel.whatisthat.ui.theme.PRESETS.first { it.id == "obsidian" },
        onCapture = {}
    )
}
