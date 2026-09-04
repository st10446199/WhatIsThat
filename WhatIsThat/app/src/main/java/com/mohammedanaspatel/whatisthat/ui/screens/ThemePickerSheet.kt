/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedanaspatel.whatisthat.ui.theme.PRESETS
import com.mohammedanaspatel.whatisthat.ui.theme.Theme
import com.mohammedanaspatel.whatisthat.ui.theme.buildCustomTheme
import kotlin.math.roundToInt

/**
 * Theme picker bottom sheet - two tabs:
 * - "Presets": the original 3-column grid of all 9 built-in themes
 * - "Custom": Figma-style pickers (a saturation/brightness square + a hue
 *   strip) for 2 colors - Background and Accent - instead of raw RGB
 *   sliders. buildCustomTheme() derives every other color automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    currentTheme: Theme,
    onThemeSelected: (Theme) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedTab by remember { mutableStateOf(0) }
    val playTap = rememberTapSound()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = currentTheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Choose a theme",
                color = currentTheme.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = currentTheme.accent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { playTap(); selectedTab = 0 },
                    text = { Text("Presets", color = if (selectedTab == 0) currentTheme.accent else currentTheme.textMuted) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { playTap(); selectedTab = 1 },
                    text = { Text("Custom", color = if (selectedTab == 1) currentTheme.accent else currentTheme.textMuted) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedTab == 0) {
                PresetsGrid(
                    currentTheme = currentTheme,
                    onThemeSelected = { playTap(); onThemeSelected(it) }
                )
            } else {
                CustomThemeEditor(
                    currentTheme = currentTheme,
                    onThemeSelected = { playTap(); onThemeSelected(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PresetsGrid(currentTheme: Theme, onThemeSelected: (Theme) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().height(280.dp)
    ) {
        items(PRESETS) { theme ->
            ThemeCard(
                theme = theme,
                isSelected = theme.id == currentTheme.id,
                onClick = { onThemeSelected(theme) }
            )
        }
    }
}

@Composable
private fun ThemeCard(theme: Theme, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(theme.bg)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) theme.accent else theme.border,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = theme.emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = theme.displayName, color = theme.text, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(theme.accent)
        )
    }
}

/** HSV state for one color being edited - hue 0-360, saturation/value 0-1. */
private class HsvState(initial: Color) {
    private val hsvArray = FloatArray(3).also {
        AndroidColor.RGBToHSV(
            (initial.red * 255).roundToInt(),
            (initial.green * 255).roundToInt(),
            (initial.blue * 255).roundToInt(),
            it
        )
    }
    var hue by mutableStateOf(hsvArray[0])
    var saturation by mutableStateOf(hsvArray[1])
    var value by mutableStateOf(hsvArray[2])

    val color: Color
        get() = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
}

/**
 * Two Figma-style color pickers - Background and Accent - each with a
 * saturation/brightness square and a hue strip. An "Apply" button builds
 * the final custom theme from both picks.
 */
@Composable
private fun CustomThemeEditor(currentTheme: Theme, onThemeSelected: (Theme) -> Unit) {
    val bgState = remember { HsvState(currentTheme.bg) }
    val accentState = remember { HsvState(currentTheme.accent) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ColorPickerSection(label = "Background", state = bgState, theme = currentTheme)
        Spacer(modifier = Modifier.height(24.dp))
        ColorPickerSection(label = "Accent", state = accentState, theme = currentTheme)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onThemeSelected(buildCustomTheme(bg = bgState.color, accent = accentState.color)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentState.color,
                contentColor = Color.White
            )
        ) {
            Text("Apply Custom Theme")
        }
    }
}

@Composable
private fun ColorPickerSection(label: String, state: HsvState, theme: Theme) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(state.color)
                    .border(1.dp, theme.border, RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = label, color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(10.dp))
        SaturationValueSquare(state = state)
        Spacer(modifier = Modifier.height(12.dp))
        HueStrip(state = state)
    }
}

/**
 * The big square from Figma's picker: X axis = saturation, Y axis = value
 * (brightness). Drag or tap anywhere to pick a color at the current hue.
 */
@Composable
private fun SaturationValueSquare(state: HsvState) {
    val density = LocalDensity.current
    var boxSizePx by remember { mutableStateOf(Offset.Zero) }

    fun updateFromOffset(offset: Offset) {
        if (boxSizePx.x <= 0f || boxSizePx.y <= 0f) return
        val x = offset.x.coerceIn(0f, boxSizePx.x)
        val y = offset.y.coerceIn(0f, boxSizePx.y)
        state.saturation = x / boxSizePx.x
        state.value = 1f - (y / boxSizePx.y)
    }

    val pureHueColor = Color(AndroidColor.HSVToColor(floatArrayOf(state.hue, 1f, 1f)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(pureHueColor)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    boxSizePx = Offset(size.width.toFloat(), size.height.toFloat())
                    updateFromOffset(offset)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        boxSizePx = Offset(size.width.toFloat(), size.height.toFloat())
                        updateFromOffset(offset)
                    }
                ) { change, _ ->
                    updateFromOffset(change.position)
                }
            }
    ) {
        // Selection indicator - a small ring at the current saturation/value position
        val indicatorX = with(density) { (state.saturation * boxSizePx.x).toDp() }
        val indicatorY = with(density) { ((1f - state.value) * boxSizePx.y).toDp() }
        Box(
            modifier = Modifier
                .offset(x = indicatorX - 10.dp, y = indicatorY - 10.dp)
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
        )
    }
}

/** Horizontal rainbow strip for picking hue (0-360°), same idea as Figma's hue bar. */
@Composable
private fun HueStrip(state: HsvState) {
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0f) }

    val hueColors = remember {
        (0..360 step 60).map { h -> Color(AndroidColor.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f))) }
    }

    fun updateFromX(x: Float) {
        if (widthPx <= 0f) return
        state.hue = (x.coerceIn(0f, widthPx) / widthPx) * 360f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(hueColors))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    widthPx = size.width.toFloat()
                    updateFromX(offset.x)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        widthPx = size.width.toFloat()
                        updateFromX(offset.x)
                    }
                ) { change, _ ->
                    updateFromX(change.position.x)
                }
            }
    ) {
        val thumbX = with(density) { ((state.hue / 360f) * widthPx).toDp() }
        Box(
            modifier = Modifier
                .offset(x = thumbX - 3.dp)
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
                .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ThemePickerSheetPreview() {
    ThemePickerSheet(
        currentTheme = PRESETS.first { it.id == "obsidian" },
        onThemeSelected = {},
        onDismiss = {}
    )
}
