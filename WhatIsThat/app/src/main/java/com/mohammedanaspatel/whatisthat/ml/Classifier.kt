/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Thin wrapper around a TensorFlow Lite image classification model - this is
 * the "brain" behind WhatIsThat's object identification.
 *
 * REQUIRED SETUP (not included automatically - see project README):
 * Two files must be placed in app/src/main/assets/ before this class works:
 * - model.tflite  - a pre-trained MobileNetV2 (or similar) image classifier
 * - labels.txt    - one class name per line, in the same order the model
 *                   was trained on (e.g. ImageNet's 1000 classes)
 *
 * Both files are free to download from TensorFlow Hub or Kaggle Models
 * (search "mobilenet tflite") - see Google (2026c) in the project references.
 * This class reads the model's own input size and data type at load time,
 * so it works with either a float or quantized (uint8) MobileNet variant
 * without needing to know in advance which one you downloaded.
 */
class Classifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize: Int
    private val isQuantized: Boolean

    init {
        val modelBuffer = loadModelFile(context, "model.tflite")
        interpreter = Interpreter(modelBuffer)
        labels = context.assets.open("labels.txt").bufferedReader().readLines()

        // Read expected input size/type directly from the model rather than
        // hardcoding it - different MobileNet exports use different input
        // sizes (commonly 224x224) and either float32 or uint8 tensors.
        val inputTensor = interpreter.getInputTensor(0)
        inputSize = inputTensor.shape()[1] // shape is [1, height, width, channels]
        isQuantized = inputTensor.dataType() == DataType.UINT8
    }

    /** Memory-maps the .tflite file from assets - avoids loading the whole thing into a byte array. */
    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Runs inference on a single photo and returns the single most likely
     * label plus a confidence score from 0-100, or null if something in the
     * pipeline failed (e.g. model/labels missing or mismatched).
     */
    fun classify(bitmap: Bitmap): Pair<String, Int>? {
        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resized)
            val outputSize = labels.size

            if (isQuantized) {
                val output = Array(1) { ByteArray(outputSize) }
                interpreter.run(inputBuffer, output)
                val (bestIndex, confidencePercent) = argmaxByte(output[0])
                labels.getOrNull(bestIndex)?.let { label -> label to confidencePercent }
            } else {
                val output = Array(1) { FloatArray(outputSize) }
                interpreter.run(inputBuffer, output)
                val (bestIndex, bestScore) = argmaxFloat(output[0])
                labels.getOrNull(bestIndex)?.let { label -> label to (bestScore * 100).toInt() }
            }
        } catch (e: Exception) {
            // Model/labels missing, wrong format, or a mismatched label count -
            // all treated as "couldn't identify" rather than crashing the app.
            null
        }
    }

    /**
     * Converts a bitmap into the flat pixel buffer the model expects: either
     * raw 0-255 bytes (quantized models) or normalized -1..1 floats (float
     * models) - this normalization range is standard for MobileNet specifically.
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = if (isQuantized) 1 else 4
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * bytesPerChannel)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            if (isQuantized) {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            } else {
                buffer.putFloat((r - 127.5f) / 127.5f)
                buffer.putFloat((g - 127.5f) / 127.5f)
                buffer.putFloat((b - 127.5f) / 127.5f)
            }
        }
        return buffer
    }

    /** Finds the highest-scoring class in a quantized (0-255 byte) output. */
    private fun argmaxByte(array: ByteArray): Pair<Int, Int> {
        var bestIndex = 0
        var bestValue = Int.MIN_VALUE
        for (i in array.indices) {
            val unsigned = array[i].toInt() and 0xFF
            if (unsigned > bestValue) {
                bestValue = unsigned
                bestIndex = i
            }
        }
        val confidencePercent = (bestValue * 100) / 255
        return bestIndex to confidencePercent
    }

    /** Finds the highest-scoring class in a float (0-1 probability) output. */
    private fun argmaxFloat(array: FloatArray): Pair<Int, Float> {
        var bestIndex = 0
        var bestValue = Float.MIN_VALUE
        for (i in array.indices) {
            if (array[i] > bestValue) {
                bestValue = array[i]
                bestIndex = i
            }
        }
        return bestIndex to bestValue
    }

    /** Releases native memory held by the interpreter - call when done with this Classifier. */
    fun close() {
        interpreter.close()
    }
}
