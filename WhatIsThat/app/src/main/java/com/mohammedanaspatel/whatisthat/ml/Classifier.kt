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
    fun classify(bitmap: Bitmap): Pair<String, Int>? {
        return try {
            val cropped = centerCropToModelAspectRatio(bitmap)
            val resized = Bitmap.createScaledBitmap(cropped, inputWidth, inputHeight, true)
            val inputBuffer = bitmapToByteBuffer(resized)

            val prediction = when (outputDataType) {
                DataType.FLOAT32 -> classifyFloat(inputBuffer)
                DataType.UINT8 -> classifyUInt8(inputBuffer)
                else -> throw IllegalStateException("Unsupported output type: $outputDataType")
            }

            if (resized !== cropped && !resized.isRecycled) {
                resized.recycle()
            }
            if (cropped !== bitmap && !cropped.isRecycled) {
                cropped.recycle()
            }

            prediction?.also { (label, confidence) ->
                Log.d(TAG, "Prediction: $label ($confidence%)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            null
        }
    }

    private fun classifyFloat(inputBuffer: ByteBuffer): Pair<String, Int>? {
        val output = Array(1) { FloatArray(outputClassCount) }
        interpreter.run(inputBuffer, output)

        val bestIndex = findBestFloatIndex(output[0]) ?: return null
        val confidence = (output[0][bestIndex] * 100f)
            .roundToInt()
            .coerceIn(0, 100)

        return friendlyLabel(bestIndex) to confidence
    }

    private fun classifyUInt8(inputBuffer: ByteBuffer): Pair<String, Int>? {
        val output = Array(1) { ByteArray(outputClassCount) }
        interpreter.run(inputBuffer, output)

        val bestIndex = findBestUInt8Index(output[0]) ?: return null
        val rawScore = output[0][bestIndex].toInt() and 0xFF
        val confidence = ((rawScore / 255f) * 100f)
            .roundToInt()
            .coerceIn(0, 100)

        return friendlyLabel(bestIndex) to confidence
    }

    /**
     * Converts the image to the input tensor format expected by the model.
     * The bundled FLOAT32 MobileNetV2 uses RGB values normalized to -1..1.
     */
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

    private fun centerCropToModelAspectRatio(bitmap: Bitmap): Bitmap {
        val targetRatio = inputWidth.toFloat() / inputHeight.toFloat()
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        return if (bitmapRatio > targetRatio) {
            val cropWidth = (bitmap.height * targetRatio).roundToInt().coerceAtMost(bitmap.width)
            val left = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, left, 0, cropWidth, bitmap.height)
        } else if (bitmapRatio < targetRatio) {
            val cropHeight = (bitmap.width / targetRatio).roundToInt().coerceAtMost(bitmap.height)
            val top = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, cropHeight)
        } else {
            bitmap
        }
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

    private fun findBestUInt8Index(scores: ByteArray): Int? {
        var bestIndex = -1
        var bestScore = Int.MIN_VALUE

        for (index in scores.indices) {
            if (index == backgroundIndex) continue
            val score = scores[index].toInt() and 0xFF
            if (score > bestScore) {
                bestScore = score
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
