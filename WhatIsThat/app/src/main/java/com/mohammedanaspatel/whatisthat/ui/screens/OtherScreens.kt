/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedanaspatel.whatisthat.data.CONFIDENCE_THRESHOLD
import com.mohammedanaspatel.whatisthat.data.ScanResult
import com.mohammedanaspatel.whatisthat.ml.Classifier
import com.mohammedanaspatel.whatisthat.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scanning screen - now runs real TFLite inference (Commit 5) on the photo
 * captured from CameraScreen, on top of the sweeping scan-line animation
 * from Commit 3.
 *
 * The classifier runs on a background dispatcher (Dispatchers.Default) since
 * inference is CPU-heavy and would freeze the UI if run on the main thread.
 * A minimum 1200ms display time is enforced regardless of how fast inference
 * actually finishes, so the animation always gets to play out fully rather
 * than flashing by in a few milliseconds on a fast device.
 *
 * onResult receives the real ScanResult, or null if confidence came back
 * below CONFIDENCE_THRESHOLD - the parent (MainActivity) routes null to the
 * Stumped screen and everything else to the Result screen.
 */
@Composable
fun ScanningScreen(
    theme: Theme,
    bitmap: Bitmap,
    classifier: Classifier?,
    onResult: (ScanResult) -> Unit,
    onUnknown: (Int?) -> Unit,
    onError: (String) -> Unit,
    onOpenThemes: () -> Unit = {}
) {
    LaunchedEffect(bitmap, classifier) {
        val activeClassifier = classifier ?: return@LaunchedEffect
        val minimumDisplayTime = launch { delay(1200) }

        val prediction = withContext(Dispatchers.Default) {
            activeClassifier.classify(bitmap)
        }

        minimumDisplayTime.join()

        when {
            prediction == null -> onError(
                "The AI model could not analyze this photo. Please try another scan."
            )

            prediction.second < CONFIDENCE_THRESHOLD -> onUnknown(prediction.second)

            else -> onResult(
                ScanResult(
                    label = prediction.first,
                    confidencePercent = prediction.second,
                    emoji = "🔍"
                )
            )
        }
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

    val isPreparingModel = classifier == null

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        ScreenTopBar(theme = theme, onOpenThemes = onOpenThemes)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured object being analyzed",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

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
                text = if (isPreparingModel) "Getting the AI ready..." else "Figuring it out...",
                color = theme.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPreparingModel) "Loading local model" else "Analyzing your photo on device",
                color = theme.textMuted,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * Successful recognition result with the captured photo, animated confidence
 * feedback, local processing information, and a clear route into another scan.
 */
@Composable
fun ResultScreen(
    theme: Theme,
    result: ScanResult,
    capturedBitmap: Bitmap? = null,
    onScanAgain: () -> Unit,
    onOpenThemes: () -> Unit = {}
) {
    val playTap = rememberTapSound()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(result) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "resultBounce"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "resultFadeIn"
    )
    val confidenceProgress by animateFloatAsState(
        targetValue = if (visible) result.confidencePercent.coerceIn(0, 100) / 100f else 0f,
        animationSpec = tween(700, delayMillis = 180),
        label = "confidenceProgress"
    )

    val confidenceLabel = when {
        result.confidencePercent >= 70 -> "Strong match"
        result.confidencePercent >= 40 -> "Good match"
        result.confidencePercent >= 20 -> "Likely match"
        else -> "Possible match"
    }

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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.43f)
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, theme.border, RoundedCornerShape(20.dp))
                .background(theme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (capturedBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = capturedBitmap.asImageBitmap(),
                    contentDescription = "Captured object identified as ${result.label}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(text = result.emoji, fontSize = 64.sp)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.accent)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "✓ IDENTIFIED",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, theme.bg.copy(alpha = 0.82f))
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 14.dp)
            ) {
                Text(
                    text = "Analyzed privately on this device",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.57f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "I think this is",
                color = theme.textMuted,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.label,
                color = theme.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.borderSubtle, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CONFIDENCE",
                            color = theme.textMuted,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = confidenceLabel,
                            color = theme.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "${result.confidencePercent}%",
                        color = theme.accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(theme.borderSubtle)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(confidenceProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(theme.accent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🔒", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Private by design",
                        color = theme.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Recognition happens locally. Your photo is not uploaded for this scan.",
                        color = theme.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    playTap()
                    onScanAgain()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Scan another object",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI predictions can be wrong. Use the confidence score as a guide.",
                color = theme.textMuted,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Stumped screen - now with a wobbling 🤔 emoji (Commit 3). Playful,
 * in-character copy instead of a generic error message.
 */
@Composable
fun StumpedScreen(
    theme: Theme,
    capturedBitmap: Bitmap? = null,
    confidencePercent: Int? = null,
    onTryAgain: () -> Unit,
    onOpenThemes: () -> Unit = {}
) {
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
            if (capturedBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = capturedBitmap.asImageBitmap(),
                    contentDescription = "Photo the AI could not identify confidently",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(theme.surface.copy(alpha = 0.9f))
                        .border(1.dp, theme.border, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "NOT SURE",
                        color = theme.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                Text(
                    text = "🤔",
                    fontSize = 56.sp,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "I'm not sure about this one 🤔",
                color = theme.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (confidencePercent != null) {
                    "The best match was only $confidencePercent% confident, so I won't pretend I know what it is."
                } else {
                    "I couldn't identify this photo confidently enough to give you a reliable answer."
                },
                color = theme.textMuted,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

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
                TipRow(emoji = "✨", text = "Use brighter, even lighting", theme = theme)
                Spacer(modifier = Modifier.height(10.dp))
                TipRow(emoji = "📐", text = "Move closer and fill more of the frame", theme = theme)
                Spacer(modifier = Modifier.height(10.dp))
                TipRow(emoji = "🔄", text = "Try another angle with less background", theme = theme)
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
                Text("Try Another Scan")
            }
        }
    }
}

@Composable
fun ErrorScreen(
    theme: Theme,
    message: String,
    onRetry: () -> Unit,
    onBackToCamera: () -> Unit,
    onOpenThemes: () -> Unit = {}
) {
    val playTap = rememberTapSound()

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        ScreenTopBar(theme = theme, onOpenThemes = onOpenThemes)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "⚠️", fontSize = 58.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Something went wrong",
                color = theme.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = theme.textMuted,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    playTap()
                    onRetry()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = Color.White
                )
            ) {
                Text("Retry AI")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    playTap()
                    onBackToCamera()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.surface,
                    contentColor = theme.text
                )
            ) {
                Text("Back to Camera")
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

// Note: no @Preview for ScanningScreen - it now requires a real, loaded
// Classifier (which loads a .tflite model file at construction time), so
// it can't be meaningfully previewed without a device/emulator running the
// full app. Test this screen by actually running the app instead.

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
