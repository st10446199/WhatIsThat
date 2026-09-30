/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved. See LICENSE file for details.
 * https://github.com/[your username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.mohammedanaspatel.whatisthat.data.FocusTarget
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ClassificationPrediction(
    val label: String,
    val confidencePercent: Int,
    val isReliable: Boolean,
    val agreementCount: Int = 1,
    val marginPercent: Int = 0
)

private data class DetectedCandidate(
    val label: String,
    val score: Float,
    val box: RectF
)

class Classifier(context: Context) {

    companion object {
        private const val TAG = "WhatIsThatDetector"
        private const val DETECTOR_MODEL = "efficientdet_lite4.tflite"

        /*
         * Keep the detector threshold fairly low so small objects are not
         * discarded before our own selection logic can inspect them.
         *
         * Nothing below the reliability thresholds is shown as identified.
         */
        private const val DETECTOR_SCORE_THRESHOLD = 0.12f
        private const val GENERAL_RELIABLE_THRESHOLD = 0.30f
        private const val FOCUSED_RELIABLE_THRESHOLD = 0.22f
        private const val STRONG_DETECTION_THRESHOLD = 0.48f
        private const val MAX_RESULTS = 15
    }

    private val objectDetector: ObjectDetector

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(DETECTOR_MODEL)
            .setDelegate(Delegate.CPU)
            .build()

        val detectorOptions = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(DETECTOR_SCORE_THRESHOLD)
            .setMaxResults(MAX_RESULTS)
            .build()

        objectDetector = ObjectDetector.createFromOptions(
            context,
            detectorOptions
        )

        Log.i(
            TAG,
            "EfficientDet Lite4 object detector ready"
        )
    }

    /**
     * WhatIsThat now uses object detection only.
     *
     * There is deliberately no generic image label fallback. If the detector
     * cannot find a supported object with enough confidence, the app returns
     * Unknown instead of inventing an unrelated label.
     */
    suspend fun classify(
        bitmap: Bitmap,
        focusTarget: FocusTarget? = null
    ): ClassificationPrediction? {
        return try {
            if (focusTarget == null) {
                classifyGeneral(bitmap)
            } else {
                classifyFocused(
                    bitmap = bitmap,
                    focusTarget = focusTarget
                )
            }
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Object detection failed",
                exception
            )
            null
        }
    }

    private fun classifyGeneral(
        bitmap: Bitmap
    ): ClassificationPrediction {
        val fullCandidates = detectObjects(bitmap)

        val fullSelection = selectGeneralCandidate(
            bitmap = bitmap,
            candidates = fullCandidates
        )

        /*
         * If the first pass is already strong, do not waste time running
         * another detector pass.
         */
        if (
            fullSelection != null &&
            fullSelection.score >= STRONG_DETECTION_THRESHOLD
        ) {
            return predictionFromCandidate(
                candidate = fullSelection,
                reliableThreshold = GENERAL_RELIABLE_THRESHOLD,
                source = "full image"
            )
        }

        /*
         * A second centre crop effectively gives smaller objects more pixels
         * without discarding the original full image result.
         */
        val centreCrop = createCrop(
            bitmap = bitmap,
            centreXRatio = 0.5f,
            centreYRatio = 0.5f,
            scale = 0.78f
        )

        val cropSelection = try {
            selectGeneralCandidate(
                bitmap = centreCrop,
                candidates = detectObjects(centreCrop)
            )
        } finally {
            if (
                centreCrop !== bitmap &&
                !centreCrop.isRecycled
            ) {
                centreCrop.recycle()
            }
        }

        val selected = chooseBetterCandidate(
            first = fullSelection,
            second = cropSelection
        )

        return if (selected == null) {
            unknownPrediction()
        } else {
            predictionFromCandidate(
                candidate = selected,
                reliableThreshold = GENERAL_RELIABLE_THRESHOLD,
                source = "general multi pass"
            )
        }
    }

    private fun classifyFocused(
        bitmap: Bitmap,
        focusTarget: FocusTarget
    ): ClassificationPrediction {
        val mappedTarget = mapFocusTargetToBitmap(
            bitmap = bitmap,
            focusTarget = focusTarget
        )

        val targetX = mappedTarget.first * bitmap.width
        val targetY = mappedTarget.second * bitmap.height

        /*
         * Pass one keeps the entire image. If the tapped point is already
         * inside a detected box, this is the best possible answer because no
         * context was removed.
         */
        val fullCandidates = detectObjects(bitmap)

        val directHit = fullCandidates
            .filter { candidate ->
                candidate.box.contains(
                    targetX,
                    targetY
                )
            }
            .maxByOrNull { candidate ->
                candidate.score
            }

        if (directHit != null) {
            return predictionFromCandidate(
                candidate = directHit,
                reliableThreshold = FOCUSED_RELIABLE_THRESHOLD,
                source = "focus direct hit"
            )
        }

        /*
         * If the object was too small for the full image pass, run a generous
         * crop around the tapped area. This magnifies the target but still
         * leaves enough surrounding context for the detector.
         */
        val wideCrop = createCrop(
            bitmap = bitmap,
            centreXRatio = mappedTarget.first,
            centreYRatio = mappedTarget.second,
            scale = 0.72f
        )

        val wideSelection = try {
            selectCropTarget(
                crop = wideCrop,
                candidates = detectObjects(wideCrop)
            )
        } finally {
            if (
                wideCrop !== bitmap &&
                !wideCrop.isRecycled
            ) {
                wideCrop.recycle()
            }
        }

        if (
            wideSelection != null &&
            wideSelection.score >= STRONG_DETECTION_THRESHOLD
        ) {
            return predictionFromCandidate(
                candidate = wideSelection,
                reliableThreshold = FOCUSED_RELIABLE_THRESHOLD,
                source = "focus wide crop"
            )
        }

        /*
         * Final pass zooms further only when the previous two passes were not
         * decisive. This is especially useful for small mice, remotes, phones,
         * bottles and similar objects.
         */
        val closeCrop = createCrop(
            bitmap = bitmap,
            centreXRatio = mappedTarget.first,
            centreYRatio = mappedTarget.second,
            scale = 0.52f
        )

        val closeSelection = try {
            selectCropTarget(
                crop = closeCrop,
                candidates = detectObjects(closeCrop)
            )
        } finally {
            if (
                closeCrop !== bitmap &&
                !closeCrop.isRecycled
            ) {
                closeCrop.recycle()
            }
        }

        val selected = chooseBetterCandidate(
            first = wideSelection,
            second = closeSelection
        )

        return if (selected == null) {
            unknownPrediction()
        } else {
            predictionFromCandidate(
                candidate = selected,
                reliableThreshold = FOCUSED_RELIABLE_THRESHOLD,
                source = "focus multi pass"
            )
        }
    }

    private fun detectObjects(
        bitmap: Bitmap
    ): List<DetectedCandidate> {
        val mpImage = BitmapImageBuilder(bitmap).build()

        val result = objectDetector.detect(mpImage)

        val candidates = result.detections()
            .mapNotNull { detection ->
                val category = detection.categories()
                    .maxByOrNull { category ->
                        category.score()
                    }
                    ?: return@mapNotNull null

                val label = category.categoryName()
                    .trim()
                    .takeIf { value ->
                        value.isNotEmpty() &&
                            value != "???"
                    }
                    ?: return@mapNotNull null

                DetectedCandidate(
                    label = friendlyLabel(label),
                    score = category.score(),
                    box = detection.boundingBox()
                )
            }
            .sortedByDescending { candidate ->
                candidate.score
            }

        if (candidates.isEmpty()) {
            Log.d(
                TAG,
                "No detector candidates"
            )
        } else {
            val summary = candidates
                .take(5)
                .joinToString { candidate ->
                    "${candidate.label}=" +
                        "${(candidate.score * 100f).roundToInt()}%"
                }

            Log.d(
                TAG,
                "Detector candidates: $summary"
            )
        }

        return candidates
    }

    /**
     * General mode favours confidence first, then objects which are reasonably
     * large and close to the centre of the photograph.
     */
    private fun selectGeneralCandidate(
        bitmap: Bitmap,
        candidates: List<DetectedCandidate>
    ): DetectedCandidate? {
        if (candidates.isEmpty()) {
            return null
        }

        val imageArea = (
            bitmap.width.toFloat() *
                bitmap.height.toFloat()
            ).coerceAtLeast(1f)

        val centreX = bitmap.width / 2f
        val centreY = bitmap.height / 2f

        val halfDiagonal = hypot(
            centreX,
            centreY
        ).coerceAtLeast(1f)

        return candidates.maxByOrNull { candidate ->
            val boxArea = (
                candidate.box.width() *
                    candidate.box.height()
                ).coerceAtLeast(0f)

            val areaFraction = (
                boxArea / imageArea
                ).coerceIn(0f, 1f)

            val centreDistance = hypot(
                candidate.box.centerX() - centreX,
                candidate.box.centerY() - centreY
            ) / halfDiagonal

            val centreScore = (
                1f - centreDistance
                ).coerceIn(0f, 1f)

            val sizeScore = sqrt(areaFraction)

            candidate.score * 0.78f +
                centreScore * 0.14f +
                sizeScore * 0.08f
        }
    }

    /**
     * A focus crop is centred on the object the user tapped, so candidates
     * nearer the middle of that crop receive a small preference.
     */
    private fun selectCropTarget(
        crop: Bitmap,
        candidates: List<DetectedCandidate>
    ): DetectedCandidate? {
        if (candidates.isEmpty()) {
            return null
        }

        val centreX = crop.width / 2f
        val centreY = crop.height / 2f

        val halfDiagonal = hypot(
            centreX,
            centreY
        ).coerceAtLeast(1f)

        return candidates.maxByOrNull { candidate ->
            val distance = hypot(
                candidate.box.centerX() - centreX,
                candidate.box.centerY() - centreY
            ) / halfDiagonal

            val centreScore = (
                1f - distance
                ).coerceIn(0f, 1f)

            candidate.score * 0.86f +
                centreScore * 0.14f
        }
    }

    private fun chooseBetterCandidate(
        first: DetectedCandidate?,
        second: DetectedCandidate?
    ): DetectedCandidate? {
        return when {
            first == null -> second
            second == null -> first
            second.score > first.score -> second
            else -> first
        }
    }

    private fun predictionFromCandidate(
        candidate: DetectedCandidate,
        reliableThreshold: Float,
        source: String
    ): ClassificationPrediction {
        val confidence = (
            candidate.score * 100f
            ).roundToInt().coerceIn(0, 100)

        val reliable = candidate.score >= reliableThreshold

        Log.d(
            TAG,
            "Selected ${candidate.label} " +
                "($confidence%), " +
                "reliable=$reliable, " +
                "source=$source"
        )

        return ClassificationPrediction(
            label = candidate.label,
            confidencePercent = confidence,
            isReliable = reliable
        )
    }

    private fun unknownPrediction(): ClassificationPrediction {
        Log.d(
            TAG,
            "No supported object was detected reliably"
        )

        return ClassificationPrediction(
            label = "Unknown object",
            confidencePercent = 0,
            isReliable = false
        )
    }

    private fun createCrop(
        bitmap: Bitmap,
        centreXRatio: Float,
        centreYRatio: Float,
        scale: Float
    ): Bitmap {
        val safeScale = scale.coerceIn(
            0.35f,
            1f
        )

        val cropWidth = (
            bitmap.width * safeScale
            ).roundToInt()
            .coerceIn(
                1,
                bitmap.width
            )

        val cropHeight = (
            bitmap.height * safeScale
            ).roundToInt()
            .coerceIn(
                1,
                bitmap.height
            )

        val centreX = (
            centreXRatio.coerceIn(0f, 1f) *
                bitmap.width
            ).roundToInt()

        val centreY = (
            centreYRatio.coerceIn(0f, 1f) *
                bitmap.height
            ).roundToInt()

        val maxLeft = (
            bitmap.width - cropWidth
            ).coerceAtLeast(0)

        val maxTop = (
            bitmap.height - cropHeight
            ).coerceAtLeast(0)

        val left = (
            centreX - cropWidth / 2
            ).coerceIn(
                0,
                maxLeft
            )

        val top = (
            centreY - cropHeight / 2
            ).coerceIn(
                0,
                maxTop
            )

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            cropWidth,
            cropHeight
        )
    }

    /**
     * PreviewView uses FILL_CENTER, so the live preview can crop part of the
     * camera image. This converts the tapped preview coordinate back into the
     * corresponding full photograph coordinate.
     */
    private fun mapFocusTargetToBitmap(
        bitmap: Bitmap,
        focusTarget: FocusTarget
    ): Pair<Float, Float> {
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        val bitmapAspectRatio =
            bitmapWidth / bitmapHeight

        val previewAspectRatio =
            focusTarget.previewAspectRatio
                .coerceAtLeast(0.01f)

        val mappedX: Float
        val mappedY: Float

        if (bitmapAspectRatio > previewAspectRatio) {
            val visibleWidthFraction = (
                previewAspectRatio /
                    bitmapAspectRatio
                ).coerceIn(0f, 1f)

            val sideCrop = (
                1f - visibleWidthFraction
                ) / 2f

            mappedX = (
                sideCrop +
                    focusTarget.xRatio
                        .coerceIn(0f, 1f) *
                    visibleWidthFraction
                ).coerceIn(0f, 1f)

            mappedY = focusTarget.yRatio
                .coerceIn(0f, 1f)
        } else if (
            bitmapAspectRatio <
            previewAspectRatio
        ) {
            val visibleHeightFraction = (
                bitmapAspectRatio /
                    previewAspectRatio
                ).coerceIn(0f, 1f)

            val topCrop = (
                1f - visibleHeightFraction
                ) / 2f

            mappedX = focusTarget.xRatio
                .coerceIn(0f, 1f)

            mappedY = (
                topCrop +
                    focusTarget.yRatio
                        .coerceIn(0f, 1f) *
                    visibleHeightFraction
                ).coerceIn(0f, 1f)
        } else {
            mappedX = focusTarget.xRatio
                .coerceIn(0f, 1f)

            mappedY = focusTarget.yRatio
                .coerceIn(0f, 1f)
        }

        return mappedX to mappedY
    }

    private fun friendlyLabel(
        label: String
    ): String {
        val normalized = label
            .trim()
            .lowercase()

        val friendlier = when (normalized) {
            "cell phone" -> "Phone"
            "tv" -> "Television"
            "sports ball" -> "Ball"
            "potted plant" -> "Plant"
            "dining table" -> "Table"
            "hair drier" -> "Hair dryer"
            else -> label.trim()
        }

        return friendlier
            .replaceFirstChar { character ->
                if (character.isLowerCase()) {
                    character.titlecase()
                } else {
                    character.toString()
                }
            }
    }

    fun close() {
        runCatching {
            objectDetector.close()
        }
    }
}
