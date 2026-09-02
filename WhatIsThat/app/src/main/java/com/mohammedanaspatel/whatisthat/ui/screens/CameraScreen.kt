/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedanaspatel.whatisthat.R
import com.mohammedanaspatel.whatisthat.ui.theme.Theme
import com.mohammedanaspatel.whatisthat.ui.theme.accentTokens
import kotlinx.coroutines.delay

/**
 * Camera screen - now with animations (Commit 3): pulsing corner brackets,
 * expanding ring behind the capture button, a shutter flash on tap, and a
 * shutter click sound played through SoundPool at the phone's actual media
 * volume (res/raw/shutter_click.wav) - simpler and more predictable than
 * MediaActionSound, which is intentionally hard for apps to control.
 * Theme button now wired to open the real ThemePickerSheet via onOpenThemes.
 */
@Composable
fun CameraScreen(
    theme: Theme,
    onCapture: () -> Unit,
    onOpenThemes: () -> Unit = {}
) {
    var showFlash by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var shutterSoundId by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        shutterSoundId = soundPool.load(context, R.raw.shutter_click, 1)
        onDispose { soundPool.release() }
    }

    fun playShutterSound() {
        // Volume matches the phone's current media volume (0f-1f), so it
        // naturally follows the volume rocker - turn media volume down and
        // the shutter gets quieter, mute it ,and it's silent, same as any
        // normal app sound.
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeRatio = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
        if (volumeRatio > 0f && shutterSoundId != 0) {
            soundPool.play(shutterSoundId, volumeRatio, volumeRatio, 1, 0, 1f)
        }
    }

    // Shutter flash: fade in fast, then trigger the capture callback, fade out.
    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlash) 0.85f else 0f,
        animationSpec = tween(durationMillis = if (showFlash) 80 else 220),
        label = "shutterFlash"
    )

    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(220)
            showFlash = false
            onCapture()
        }
    }

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
                CaptureButton(
                    theme = theme,
                    onTap = {
                        playShutterSound()
                        showFlash = true
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TAP TO IDENTIFY",
                    color = theme.textMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }
    }
}

/** Four pulsing corner brackets - fades between 0.5 and 1.0 alpha on a loop. */
@Composable
private fun ViewfinderCorners(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "cornerPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerAlpha"
    )

    val cornerSize = 32.dp
    val strokeWidth = 3.dp
    val color = accentColor.copy(alpha = alpha)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopStart).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, color),
                    RoundedCornerShape(topStart = 8.dp)
                )
        )
        Box(
            Modifier.align(Alignment.TopEnd).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, color),
                    RoundedCornerShape(topEnd = 8.dp)
                )
        )
        Box(
            Modifier.align(Alignment.BottomStart).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, color),
                    RoundedCornerShape(bottomStart = 8.dp)
                )
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(cornerSize)
                .border(
                    androidx.compose.foundation.BorderStroke(strokeWidth, color),
                    RoundedCornerShape(bottomEnd = 8.dp)
                )
        )
    }
}

/** Capture button with an expanding/fading ring pulsing behind it. */
@Composable
private fun CaptureButton(theme: Theme, onTap: () -> Unit) {
    val tokens = accentTokens(theme.accent)
    val transition = rememberInfiniteTransition(label = "ringExpand")

    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing)),
        label = "ringScale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing)),
        label = "ringAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = ringScale
                    scaleY = ringScale
                    alpha = ringAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .border(2.dp, theme.accent, RoundedCornerShape(50))
        )

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

/** Opens the theme picker bottom sheet (wired in MainActivity via onOpenThemes). */
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
