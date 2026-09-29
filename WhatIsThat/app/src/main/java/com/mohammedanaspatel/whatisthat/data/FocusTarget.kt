/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.data

/**
 * A tap selected point inside the live camera preview.
 *
 * xRatio and yRatio are stored from zero to one so the target survives the
 * jump from the PreviewView coordinate system to the full resolution photo.
 * previewAspectRatio lets the classifier account for PreviewView cropping
 * when it maps the selected point back onto the captured bitmap.
 */
data class FocusTarget(
    val xRatio: Float,
    val yRatio: Float,
    val previewAspectRatio: Float
)
