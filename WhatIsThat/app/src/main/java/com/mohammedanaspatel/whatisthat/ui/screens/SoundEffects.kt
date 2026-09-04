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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mohammedanaspatel.whatisthat.R

/**
 * Shared soft "pop" sound for UI taps (Scan Again, Try Again, theme picker
 * interactions, etc.) - distinct from the sharper camera shutter click.
 * Same volume-matching approach as the shutter sound: scales to the phone's
 * actual media volume rather than always playing at a fixed level.
 *
 * Usage: `val playTap = rememberTapSound(); ... onClick = { playTap(); ... }`
 */
@Composable
fun rememberTapSound(): () -> Unit {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var soundId by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        soundId = soundPool.load(context, R.raw.ui_click, 1)
        onDispose { soundPool.release() }
    }

    return {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeRatio = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
        if (volumeRatio > 0f && soundId != 0) {
            soundPool.play(soundId, volumeRatio, volumeRatio, 1, 0, 1f)
        }
    }
}
