/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedanaspatel.whatisthat.data.ScanResult
import com.mohammedanaspatel.whatisthat.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * Scanning screen - now with a sweeping scan-line animation (Commit 3),
 * moving top-to-bottom on a 1.5s loop.
 */
@Composable
fun ScanningScreen(theme: Theme, onScanComplete: () -> Unit, onOpenThemes: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        delay(1200) // matches the ~1.2s "thinking" delay in the Figma design
        onScanComplete()
    }

    val transition = rememberInfiniteTransition(label = "scanLine")
    val lineY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lineY"
    )

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        ScreenTopBar(theme = theme, onOpenThemes = onOpenThemes)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Viewfinder box - same size as the Camera screen's, with the
            // scan line sweeping from top to bottom on a loop
            var boxHeightPx by remember { mutableStateOf(0) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                    .background(theme.surface)
                    .onGloballyPositioned { boxHeightPx = it.size.height }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .graphicsLayer {
                            translationY = boxHeightPx * lineY
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, theme.accent, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row {
                Text(text = "❓", fontSize = 20.sp, color = theme.accent)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "❓", fontSize = 28.sp, color = theme.accent)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "❓", fontSize = 20.sp, color = theme.accent)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Figuring it out...",
                color = theme.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Running local AI model",
                color = theme.textMuted,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * Result screen - now with a spring bounce-in on appear (Commit 3).
 * "it's a [Object]!" reveal, matching the app's name/voice. Photo placeholder
 * instead of the real captured frame (Commit 4).
 */
@Composable
fun ResultScreen(theme: Theme, result: ScanResult, onScanAgain: () -> Unit, onOpenThemes: () -> Unit = {}) {
    val playTap = rememberTapSound()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(result) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "resultBounce"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "resultFadeIn"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = contentAlpha
            }
    ) {
        ScreenTopBar(theme = theme, onOpenThemes = onOpenThemes)

        // Placeholder for the captured photo - Commit 4 swaps this for the real frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                .background(theme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(text = result.emoji, fontSize = 64.sp)

            // "IDENTIFIED" badge, top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.accent)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "IDENTIFIED",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "IT'S A —",
                color = theme.textMuted,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${result.label}!",
                color = theme.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Confidence label + percentage row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "CONFIDENCE", color = theme.textMuted, fontSize = 11.sp, letterSpacing = 0.5.sp)
                Text(
                    text = "${result.confidencePercent}%",
                    color = theme.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Confidence progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(result.confidencePercent / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(theme.accent)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // "Fun fact" callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.borderSubtle, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Fun fact: Recognized entirely on-device — no internet, no cloud, just local AI doing its thing. 🐷",
                    color = theme.textSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    playTap()
                    onScanAgain()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = Color.White
                )
            ) {
                Text("Scan Again ?")
            }
        }
    }
}

/**
 * Stumped screen - now with a wobbling 🤔 emoji (Commit 3). Playful,
 * in-character copy instead of a generic error message.
 */
@Composable
fun StumpedScreen(theme: Theme, onTryAgain: () -> Unit, onOpenThemes: () -> Unit = {}) {
    val playTap = rememberTapSound()
    val transition = rememberInfiniteTransition(label = "wobble")
    val rotation by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobbleRotation"
    )

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        ScreenTopBar(theme = theme, onOpenThemes = onOpenThemes)

        // Placeholder box matching the photo-frame area on other screens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                .background(theme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🤔",
                fontSize = 56.sp,
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hmm... you got me 🤔",
                color = theme.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Stumped me on this one — I've seen a lot of things, but not quite that.",
                color = theme.textMuted,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            // "TRY THESE:" tips card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.borderSubtle, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "TRY THESE:",
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                TipRow(emoji = "✨", text = "Better lighting goes a long way", theme = theme)
                Spacer(modifier = Modifier.height(10.dp))
                TipRow(emoji = "📐", text = "Get closer — fill the frame", theme = theme)
                Spacer(modifier = Modifier.height(10.dp))
                TipRow(emoji = "🔄", text = "Try a different angle", theme = theme)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    playTap()
                    onTryAgain()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = Color.White
                )
            ) {
                Text("Try Again")
            }
        }
    }
}

@Composable
private fun TipRow(emoji: String, text: String, theme: Theme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, fontSize = 15.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, color = theme.accent, fontSize = 13.sp)
    }
}

/**
 * Shared top bar (wordmark + offline badge) - reused across Scanning, Result,
 * and Stumped so every screen matches the Figma reference, which shows this
 * bar consistently at the top no matter which screen you're on.
 */
@Composable
private fun ScreenTopBar(theme: Theme, onOpenThemes: () -> Unit) {
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

private val previewTheme = com.mohammedanaspatel.whatisthat.ui.theme.PRESETS.first { it.id == "obsidian" }

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ScanningScreenPreview() {
    ScanningScreen(theme = previewTheme, onScanComplete = {})
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ResultScreenPreview() {
    ResultScreen(
        theme = previewTheme,
        result = com.mohammedanaspatel.whatisthat.data.MOCK_RESULT,
        onScanAgain = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun StumpedScreenPreview() {
    StumpedScreen(theme = previewTheme, onTryAgain = {})
}
