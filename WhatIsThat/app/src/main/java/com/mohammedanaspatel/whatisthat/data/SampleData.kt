/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.data

/** Mock result used everywhere until Commit 5 wires in the real TFLite model. */
data class ScanResult(
    val label: String,
    val confidencePercent: Int,
    val emoji: String
)

val SAMPLE_RESULTS = listOf(
    ScanResult("Rubber Duck", 94, "🦆"),
    ScanResult("Coffee Mug", 89, "☕"),
    ScanResult("Houseplant", 91, "🪴"),
    ScanResult("Sneaker", 87, "👟"),
    ScanResult("Wristwatch", 96, "⌚")
)

/** Used for Commit 2 static screens - swap for real predictions in Commit 5. */
val MOCK_RESULT = SAMPLE_RESULTS.first()

/** Confidence threshold below which the app shows the Stumped screen instead. */
const val CONFIDENCE_THRESHOLD = 60
