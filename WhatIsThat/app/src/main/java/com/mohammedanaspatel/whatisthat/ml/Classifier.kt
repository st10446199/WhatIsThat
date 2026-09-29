/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.mohammedanaspatel.whatisthat.data.FocusTarget
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

/**
 * Local TensorFlow Lite image classifier used by WhatIsThat.
 *
 * The bundled model is MobileNetV2 trained on ImageNet. It expects a
 * 224 x 224 RGB FLOAT32 image normalized to the range -1..1 and produces
 * 1001 probabilities, where index 0 is the ImageNet background class.
 *
 * The implementation still reads the tensor dimensions at runtime so the
 * preprocessing stays tied to the actual model rather than a magic size.
 */
class Classifier(context: Context) {

    companion object {
        private const val TAG = "WhatIsThatClassifier"
    }

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputDataType: DataType
    private val outputDataType: DataType
    private val outputClassCount: Int
    private val backgroundIndex: Int?

    init {
        val modelBuffer = loadModelFile(context, "model.tflite")
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(4)
        })

        labels = context.assets
            .open("labels.txt")
            .bufferedReader()
            .useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toList() }

        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[3] == 3) {
            "Unsupported model input shape: ${inputShape.contentToString()}"
        }

        inputHeight = inputShape[1]
        inputWidth = inputShape[2]
        inputDataType = inputTensor.dataType()

        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        outputClassCount = outputShape.last()
        outputDataType = outputTensor.dataType()

        require(labels.size >= outputClassCount) {
            "labels.txt has ${labels.size} labels but the model outputs $outputClassCount classes"
        }

        backgroundIndex = labels.indexOfFirst { it.equals("background", ignoreCase = true) }
            .takeIf { it >= 0 }

        Log.i(
            TAG,
            "Loaded classifier: input=${inputWidth}x$inputHeight $inputDataType, " +
                "output=$outputClassCount $outputDataType, labels=${labels.size}"
        )
    }

    /** Memory-map the TFLite file directly from the APK assets. */
    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val descriptor = context.assets.openFd(filename)
        FileInputStream(descriptor.fileDescriptor).use { inputStream ->
            return inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        }
    }

    /**
     * Classify one captured photo and return the strongest non-background
     * ImageNet label plus a confidence percentage.
     *
     * Camera photos are normally much taller/wider than the model input.
     * Stretching the whole photo into a 224 x 224 square distorts objects and
     * includes lots of irrelevant background. We therefore centre-crop to the
     * model's aspect ratio first, then resize.
     */
    fun classify(bitmap: Bitmap, focusTarget: FocusTarget? = null): Pair<String, Int>? {
        return try {
            val crops = buildAnalysisCrops(bitmap, focusTarget)
            val combinedScores = FloatArray(outputClassCount)

            crops.forEach { weightedCrop ->
                val resized = Bitmap.createScaledBitmap(
                    weightedCrop.bitmap,
                    inputWidth,
                    inputHeight,
                    true
                )

                val inputBuffer = bitmapToByteBuffer(resized)
                val scores = runInference(inputBuffer)

                for (index in scores.indices) {
                    if (index != backgroundIndex) {
                        combinedScores[index] += scores[index] * weightedCrop.weight
                    }
                }

                if (resized !== weightedCrop.bitmap && !resized.isRecycled) {
                    resized.recycle()
                }

                if (weightedCrop.bitmap !== bitmap && !weightedCrop.bitmap.isRecycled) {
                    weightedCrop.bitmap.recycle()
                }
            }

            val bestIndex = findBestFloatIndex(combinedScores) ?: return null
            val confidence = (combinedScores[bestIndex] * 100f)
                .roundToInt()
                .coerceIn(0, 100)

            val prediction = friendlyLabel(bestIndex) to confidence
            val mode = if (focusTarget == null) {
                "multi scale full image"
            } else {
                "multi scale focus target"
            }

            Log.d(
                TAG,
                "Prediction: ${prediction.first} (${prediction.second}%) using $mode"
            )

            logTopPredictions(combinedScores)
            prediction
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            null
        }
    }

    private data class WeightedCrop(
        val bitmap: Bitmap,
        val weight: Float
    )

    /**
     * One crop is often not enough for a real camera image.
     *
     * For a user selected target we analyse the tapped area at three zoom
     * levels plus a small amount of global context. This makes recognition
     * much less sensitive to the exact tap location and object size.
     *
     * Without a target we analyse three centre crops at different scales.
     */
    private fun buildAnalysisCrops(
        bitmap: Bitmap,
        focusTarget: FocusTarget?
    ): List<WeightedCrop> {
        return if (focusTarget == null) {
            listOf(
                WeightedCrop(
                    cropToModelAspectRatio(bitmap, 1.00f, 0.5f, 0.5f),
                    0.45f
                ),
                WeightedCrop(
                    cropToModelAspectRatio(bitmap, 0.78f, 0.5f, 0.5f),
                    0.35f
                ),
                WeightedCrop(
                    cropToModelAspectRatio(bitmap, 0.58f, 0.5f, 0.5f),
                    0.20f
                )
            )
        } else {
            val mappedTarget = mapFocusTargetToBitmap(bitmap, focusTarget)

            listOf(
                WeightedCrop(
                    cropToModelAspectRatio(
                        bitmap,
                        0.34f,
                        mappedTarget.first,
                        mappedTarget.second
                    ),
                    0.40f
                ),
                WeightedCrop(
                    cropToModelAspectRatio(
                        bitmap,
                        0.50f,
                        mappedTarget.first,
                        mappedTarget.second
                    ),
                    0.32f
                ),
                WeightedCrop(
                    cropToModelAspectRatio(
                        bitmap,
                        0.68f,
                        mappedTarget.first,
                        mappedTarget.second
                    ),
                    0.20f
                ),
                WeightedCrop(
                    cropToModelAspectRatio(bitmap, 1.00f, 0.5f, 0.5f),
                    0.08f
                )
            )
        }
    }

    private fun runInference(inputBuffer: ByteBuffer): FloatArray {
        return when (outputDataType) {
            DataType.FLOAT32 -> {
                val output = Array(1) { FloatArray(outputClassCount) }
                interpreter.run(inputBuffer, output)
                output[0]
            }

            DataType.UINT8 -> {
                val output = Array(1) { ByteArray(outputClassCount) }
                interpreter.run(inputBuffer, output)

                FloatArray(outputClassCount) { index ->
                    (output[0][index].toInt() and 0xFF) / 255f
                }
            }

            else -> throw IllegalStateException(
                "Unsupported output type: $outputDataType"
            )
        }
    }

    private fun mapFocusTargetToBitmap(
        bitmap: Bitmap,
        focusTarget: FocusTarget
    ): Pair<Float, Float> {
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        val sourceAspectRatio = bitmapWidth / bitmapHeight
        val previewAspectRatio = focusTarget.previewAspectRatio.coerceAtLeast(0.01f)

        val mappedXRatio: Float
        val mappedYRatio: Float

        if (sourceAspectRatio > previewAspectRatio) {
            val visibleWidthFraction =
                (previewAspectRatio / sourceAspectRatio).coerceIn(0f, 1f)
            val croppedSideFraction = (1f - visibleWidthFraction) / 2f

            mappedXRatio = (
                croppedSideFraction +
                    focusTarget.xRatio.coerceIn(0f, 1f) * visibleWidthFraction
                ).coerceIn(0f, 1f)

            mappedYRatio = focusTarget.yRatio.coerceIn(0f, 1f)
        } else if (sourceAspectRatio < previewAspectRatio) {
            val visibleHeightFraction =
                (sourceAspectRatio / previewAspectRatio).coerceIn(0f, 1f)
            val croppedTopFraction = (1f - visibleHeightFraction) / 2f

            mappedXRatio = focusTarget.xRatio.coerceIn(0f, 1f)

            mappedYRatio = (
                croppedTopFraction +
                    focusTarget.yRatio.coerceIn(0f, 1f) * visibleHeightFraction
                ).coerceIn(0f, 1f)
        } else {
            mappedXRatio = focusTarget.xRatio.coerceIn(0f, 1f)
            mappedYRatio = focusTarget.yRatio.coerceIn(0f, 1f)
        }

        return mappedXRatio to mappedYRatio
    }

    private fun cropToModelAspectRatio(
        bitmap: Bitmap,
        scale: Float,
        centerXRatio: Float,
        centerYRatio: Float
    ): Bitmap {
        val safeScale = scale.coerceIn(0.2f, 1f)
        val targetRatio = inputWidth.toFloat() / inputHeight.toFloat()
        val maximumCropWidth = bitmap.width * safeScale
        val maximumCropHeight = bitmap.height * safeScale

        val cropWidth: Int
        val cropHeight: Int

        if (maximumCropWidth / maximumCropHeight > targetRatio) {
            cropHeight = maximumCropHeight
                .roundToInt()
                .coerceIn(1, bitmap.height)

            cropWidth = (cropHeight * targetRatio)
                .roundToInt()
                .coerceIn(1, bitmap.width)
        } else {
            cropWidth = maximumCropWidth
                .roundToInt()
                .coerceIn(1, bitmap.width)

            cropHeight = (cropWidth / targetRatio)
                .roundToInt()
                .coerceIn(1, bitmap.height)
        }

        val centerX = (
            centerXRatio.coerceIn(0f, 1f) * bitmap.width
            ).roundToInt()

        val centerY = (
            centerYRatio.coerceIn(0f, 1f) * bitmap.height
            ).roundToInt()

        val maximumLeft = (bitmap.width - cropWidth).coerceAtLeast(0)
        val maximumTop = (bitmap.height - cropHeight).coerceAtLeast(0)

        val left = (centerX - cropWidth / 2).coerceIn(0, maximumLeft)
        val top = (centerY - cropHeight / 2).coerceIn(0, maximumTop)

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            cropWidth,
            cropHeight
        )
    }

    private fun logTopPredictions(scores: FloatArray) {
        scores.indices
            .asSequence()
            .filter { it != backgroundIndex }
            .sortedByDescending { scores[it] }
            .take(3)
            .joinToString { index ->
                "${friendlyLabel(index)}=${(scores[index] * 100f).roundToInt()}%"
            }
            .also { topPredictions ->
                Log.d(TAG, "Top predictions: $topPredictions")
            }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = when (inputDataType) {
            DataType.FLOAT32 -> 4
            DataType.UINT8 -> 1
            else -> throw IllegalStateException("Unsupported input type: $inputDataType")
        }

        val buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (pixel in pixels) {
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            when (inputDataType) {
                DataType.FLOAT32 -> {
                    buffer.putFloat((red - 127.5f) / 127.5f)
                    buffer.putFloat((green - 127.5f) / 127.5f)
                    buffer.putFloat((blue - 127.5f) / 127.5f)
                }

                DataType.UINT8 -> {
                    buffer.put(red.toByte())
                    buffer.put(green.toByte())
                    buffer.put(blue.toByte())
                }

                else -> Unit
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun findBestFloatIndex(scores: FloatArray): Int? {
        var bestIndex = -1
        var bestScore = Float.NEGATIVE_INFINITY

        for (index in scores.indices) {
            if (index == backgroundIndex) continue
            if (scores[index] > bestScore) {
                bestScore = scores[index]
                bestIndex = index
            }
        }

        return bestIndex.takeIf { it >= 0 }
    }


    /** ImageNet labels often contain comma-separated synonyms; show the shortest useful name. */
    private fun friendlyLabel(index: Int): String {
        return labels[index]
            .substringBefore(',')
            .trim()
            .replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
    }

    fun close() {
        interpreter.close()
    }
}
