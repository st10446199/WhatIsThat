/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mohammedanaspatel.whatisthat.R
import com.mohammedanaspatel.whatisthat.data.FocusTarget
import com.mohammedanaspatel.whatisthat.ui.theme.Theme
import com.mohammedanaspatel.whatisthat.ui.theme.accentTokens
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Converts a captured JPEG ImageProxy (from ImageCapture's in-memory
 * callback) into a correctly-oriented Bitmap. Camera sensors are physically
 * mounted at a fixed rotation, so the raw image data often needs rotating to
 * match what the person actually saw on screen - imageInfo.rotationDegrees
 * tells us exactly how much.
 */
private fun ImageProxy.toRotatedBitmapOrNull(): Bitmap? {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotationDegrees = imageInfo.rotationDegrees
    if (rotationDegrees == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Camera screen - now captures a real photo and hands it off for actual
 * TFLite inference (Commit 5), on top of the live CameraX feed (Commit 4)
 * and animations/sound from Commit 3.
 *
 * Camera permission is requested the first time this screen appears. If the
 * person denies it, a fallback message with a "Grant Permission" button is
 * shown instead of the live feed - the rest of the screen (theme button,
 * offline badge, etc.) still works normally either way.
 *
 * onCaptured now delivers the actual captured Bitmap (instead of just a
 * "tapped" signal) - the parent (MainActivity) passes this along to
 * ScanningScreen, which runs it through the real Classifier.
 */
@Composable
fun CameraScreen(
    theme: Theme,
    onCaptured: (Bitmap, FocusTarget?) -> Unit,
    onCameraError: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenThemes: () -> Unit = {}
) {
    var showFlash by remember { mutableStateOf(false) }
    var focusTarget by remember { mutableStateOf<FocusTarget?>(null) }

    // --- Camera permission handling ---
    // Check current permission state once, then offer a launcher that shows
    // the system permission dialog. hasCameraPermission drives whether we
    // show the live feed or the fallback "please grant permission" message.
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // Ask for permission once, the first time this screen is shown, if we
    // don't already have it.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

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
        // the shutter gets quieter, mute it and it's silent, same as any
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
        }
    }

    // Holds the CameraX ImageCapture use case once CameraPreview finishes
    // binding it - this is what actually takes a real photo on tap.
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                theme = theme,
                onOpenThemes = onOpenThemes
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Viewfinder frame: shows the live CameraX feed once permission is
                // granted, or a fallback message with a retry button if not.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                        .background(if (theme.isDark) Color(0xFF141420) else Color(0xFFE8DDD8)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onImageCaptureReady = { imageCapture = it },
                            onFocusTargetChanged = { focusTarget = it },
                            onCameraError = onCameraError
                        )
                    } else {
                        PermissionFallback(theme = theme) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }

                ViewfinderCorners(accentColor = theme.accent)

                Text(
                    text = if (focusTarget == null) {
                        "Tap an object to make the AI prioritize it"
                    } else {
                        "AI target selected • tap elsewhere to change it"
                    },
                    color = theme.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 48.dp)
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

                        // Take an actual photo via CameraX's ImageCapture use
                        // case. The result arrives asynchronously in the
                        // callback below - we convert it to a Bitmap and hand
                        // it up to the parent, which routes it to
                        // ScanningScreen for real classification.
                        imageCapture?.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = image.toRotatedBitmapOrNull()
                                    image.close()
                                    if (bitmap != null) {
                                        onCaptured(bitmap, focusTarget)
                                    } else {
                                        onCameraError(
                                            "The photo could not be prepared for scanning. Please try again."
                                        )
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    onCameraError(
                                        "The camera could not capture a photo. Please try again."
                                    )
                                }
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TAP TO IDENTIFY",
                    color = theme.textMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                RecentScansButton(
                    theme = theme,
                    onClick = onOpenHistory
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

/**
 * Wraps CameraX's PreviewView (a regular Android View) so it can be used
 * inside Compose. This is the standard "AndroidView" bridge pattern -
 * Compose doesn't have its own camera preview widget, so we host the
 * View-based one from CameraX directly.
 *
 * What happens here:
 * 1. Create a PreviewView (the actual View that renders camera frames).
 * 2. Get a ProcessCameraProvider - CameraX's entry point, tied to the app's
 *    process rather than a single Activity.
 * 3. Build a Preview use case and point its output at our PreviewView.
 * 4. Bind everything to this screen's lifecycle, so the camera automatically
 *    starts/stops as the screen appears/disappears - no manual start/stop
 *    calls needed, CameraX handles it via the lifecycle owner.
 *
 * Also wires up 3 standard camera interactions:
 * - Tap-to-focus: taps create a CameraX "metering point" at that spot and
 *   ask the camera to focus/expose there. A ring shows where you tapped.
 * - Reset focus: a small pill button appears after a manual focus, letting
 *   you cancel it and return to normal continuous autofocus.
 * - Pinch-to-zoom: a standard two-finger pinch gesture reads the camera's
 *   supported zoom range and moves within it smoothly.
 */
@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit = {},
    onFocusTargetChanged: (FocusTarget?) -> Unit = {},
    onCameraError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Holds the bound Camera once CameraX finishes setup - this is what lets
    // us call focus/zoom controls later, since those live on the Camera
    // object CameraX hands back from bindToLifecycle(), not on PreviewView.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Where the user last tapped to focus, in pixels relative to the preview.
    // Null means "no manual focus point right now" - normal continuous
    // autofocus is running instead.
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var currentZoomRatio by remember { mutableStateOf(1f) }
    var zoomBounds by remember { mutableStateOf(1f..1f) }
    var zoomChangeKey by remember { mutableStateOf(0) }
    var showZoomIndicator by remember { mutableStateOf(false) }

    // Hides the zoom indicator ~1s after the last pinch update - restarting
    // this effect on every zoomChangeKey bump is what makes it "debounce":
    // each new pinch event cancels the previous hide-timer and starts a new one.
    LaunchedEffect(zoomChangeKey) {
        if (zoomChangeKey > 0) {
            showZoomIndicator = true
            delay(1000)
            showZoomIndicator = false
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                // Tap-to-focus: converts the tap position into a CameraX
                // MeteringPoint and asks the camera to focus/expose there.
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        val view = previewView ?: return@detectTapGestures
                        val cam = camera ?: return@detectTapGestures

                        val meteringPoint = view.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(
                            meteringPoint,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        )
                            // Auto-cancels back to continuous autofocus after
                            // 5s if the person doesn't tap "Reset Focus" first
                            .setAutoCancelDuration(5, TimeUnit.SECONDS)
                            .build()

                        cam.cameraControl.startFocusAndMetering(action)
                        focusPoint = offset

                        val previewWidth = view.width.toFloat()
                        val previewHeight = view.height.toFloat()
                        if (previewWidth > 0f && previewHeight > 0f) {
                            onFocusTargetChanged(
                                FocusTarget(
                                    xRatio = (offset.x / previewWidth).coerceIn(0f, 1f),
                                    yRatio = (offset.y / previewHeight).coerceIn(0f, 1f),
                                    previewAspectRatio = previewWidth / previewHeight
                                )
                            )
                        }
                    }
                }
                // Pinch-to-zoom: reads how much the pinch gesture scaled by,
                // multiplies it into our running zoom ratio, and clamps it to
                // whatever range this specific camera/lens actually supports.
                .pointerInput(camera) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val zoomState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures

                        val newZoomRatio = (currentZoomRatio * gestureZoom)
                            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)

                        currentZoomRatio = newZoomRatio
                        zoomBounds = zoomState.minZoomRatio..zoomState.maxZoomRatio
                        zoomChangeKey++
                        cam.cameraControl.setZoomRatio(newZoomRatio)
                    }
                },
            factory = { ctx ->
                val view = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewView = view
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview use case: streams live camera frames into previewView
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

                    // ImageCapture use case: this is what actually grabs a
                    // full-resolution still photo when the capture button is
                    // tapped - separate from the live preview stream.
                    val imageCapture = ImageCapture.Builder().build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        // unbindAll() first, since re-binding without it throws if
                        // this screen's Composable re-runs (e.g. on theme change)
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                        onImageCaptureReady(imageCapture)
                    } catch (exception: Exception) {
                        onCameraError(
                            "The camera could not start. Close any other app using the camera and try again."
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))

                view
            }
        )

        // Focus ring - shows exactly where the last tap-to-focus happened
        focusPoint?.let { point ->
            FocusRing(offsetPx = point)
        }

        // "Reset Focus" pill - only shown while a manual focus point is active
        if (focusPoint != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            camera?.cameraControl?.cancelFocusAndMetering()
                            focusPoint = null
                            onFocusTargetChanged(null)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(text = "Reset Target", color = Color.White, fontSize = 12.sp)
            }
        }

        // Zoom indicator - a radial ring gauge (like a premium camera app),
        // fills up as you zoom in, fades out ~1s after you stop pinching.
        AnimatedVisibility(
            visible = showZoomIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) {
            ZoomIndicator(zoomRatio = currentZoomRatio, bounds = zoomBounds)
        }
    }
}

/** A simple ring drawn at the last tap-to-focus point, in pixel coordinates. */
@Composable
private fun FocusRing(offsetPx: Offset) {
    val density = LocalDensity.current
    val xDp = with(density) { offsetPx.x.toDp() }
    val yDp = with(density) { offsetPx.y.toDp() }
    val ringSize = 56.dp

    Box(
        modifier = Modifier
            .offset(x = xDp - ringSize / 2, y = yDp - ringSize / 2)
            .size(ringSize)
            .border(2.dp, Color.White, RoundedCornerShape(50))
    )
}

/**
 * Premium-style zoom gauge: a dark circular badge with a thin progress ring
 * around it showing where the current zoom sits between the camera's min
 * and max, plus a numeric readout in the middle (e.g. "2.3×"). Same visual
 * language as native camera apps, but built from scratch with Canvas rather
 * than a system widget.
 */
@Composable
private fun ZoomIndicator(zoomRatio: Float, bounds: ClosedFloatingPointRange<Float>) {
    val range = (bounds.endInclusive - bounds.start).coerceAtLeast(0.01f)
    val fraction = ((zoomRatio - bounds.start) / range).coerceIn(0f, 1f)
    val badgeSize = 64.dp

    Box(
        modifier = Modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize().padding(4.dp)) {
            val strokeWidth = 3.dp.toPx()
            // Background track - full circle, low opacity
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Progress arc - fills clockwise from the top based on zoom fraction
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = String.format("%.1f×", zoomRatio),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Shown instead of the camera feed if permission hasn't been granted (yet). */
@Composable
private fun PermissionFallback(theme: Theme, onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            text = "Camera access needed",
            color = theme.text,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "WhatIsThat needs your camera to identify objects.",
            color = theme.textMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.accent,
                contentColor = Color.White
            )
        ) {
            Text("Grant Permission")
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
private fun TopBar(
    theme: Theme,
    onOpenThemes: () -> Unit
) {
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

@Composable
private fun RecentScansButton(
    theme: Theme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(theme.surface)
            .border(
                width = 1.dp,
                color = theme.borderSubtle,
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "↺",
            color = theme.accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Recent scans",
            color = theme.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
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
        onCaptured = { _, _ -> }
    )
}
