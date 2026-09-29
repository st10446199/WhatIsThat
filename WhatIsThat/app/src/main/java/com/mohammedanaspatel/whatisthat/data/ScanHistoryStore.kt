/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved. See LICENSE file for details.
 * https://github.com/[your username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.data

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class HistoryLayout {
    LIST,
    GRID
}

data class ScanHistoryItem(
    val label: String,
    val confidencePercent: Int,
    val scannedAtMillis: Long,
    val imagePath: String? = null
)

class ScanHistoryStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(
        "what_is_that_history",
        Context.MODE_PRIVATE
    )

    fun load(): List<ScanHistoryItem> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)

                    add(
                        ScanHistoryItem(
                            label = item.getString(KEY_LABEL),
                            confidencePercent = item.getInt(KEY_CONFIDENCE),
                            scannedAtMillis = item.getLong(KEY_TIME),
                            imagePath = item.optString(KEY_IMAGE_PATH)
                                .takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadLayout(): HistoryLayout {
        val savedValue = preferences.getString(
            KEY_LAYOUT,
            HistoryLayout.LIST.name
        )

        return runCatching {
            HistoryLayout.valueOf(savedValue ?: HistoryLayout.LIST.name)
        }.getOrDefault(HistoryLayout.LIST)
    }

    fun saveLayout(layout: HistoryLayout) {
        preferences.edit()
            .putString(KEY_LAYOUT, layout.name)
            .apply()
    }

    fun add(
        result: ScanResult,
        bitmap: Bitmap
    ): List<ScanHistoryItem> {
        val timestamp = System.currentTimeMillis()
        val imagePath = saveThumbnail(bitmap, timestamp)

        val updated = listOf(
            ScanHistoryItem(
                label = result.label,
                confidencePercent = result.confidencePercent,
                scannedAtMillis = timestamp,
                imagePath = imagePath
            )
        ) + load()

        val limited = updated.take(MAX_HISTORY_ITEMS)
        deleteRemovedImages(
            allItems = updated,
            keptItems = limited
        )
        save(limited)
        return limited
    }

    fun clear() {
        load().forEach { item ->
            item.imagePath?.let { path ->
                runCatching { File(path).delete() }
            }
        }

        preferences.edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    private fun saveThumbnail(
        bitmap: Bitmap,
        timestamp: Long
    ): String? {
        return runCatching {
            val directory = File(context.filesDir, THUMBNAIL_DIRECTORY)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(
                directory,
                "scan_${timestamp}.jpg"
            )

            val targetWidth = 480
            val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
            val targetHeight = (bitmap.height * ratio)
                .toInt()
                .coerceAtLeast(1)

            val thumbnail = if (bitmap.width > targetWidth) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    targetWidth,
                    targetHeight,
                    true
                )
            } else {
                bitmap
            }

            file.outputStream().use { stream ->
                thumbnail.compress(
                    Bitmap.CompressFormat.JPEG,
                    82,
                    stream
                )
            }

            if (thumbnail !== bitmap && !thumbnail.isRecycled) {
                thumbnail.recycle()
            }

            file.absolutePath
        }.getOrNull()
    }

    private fun deleteRemovedImages(
        allItems: List<ScanHistoryItem>,
        keptItems: List<ScanHistoryItem>
    ) {
        val keptPaths = keptItems
            .mapNotNull { it.imagePath }
            .toSet()

        allItems
            .mapNotNull { it.imagePath }
            .filterNot { it in keptPaths }
            .forEach { path ->
                runCatching { File(path).delete() }
            }
    }

    private fun save(items: List<ScanHistoryItem>) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(KEY_LABEL, item.label)
                    put(KEY_CONFIDENCE, item.confidencePercent)
                    put(KEY_TIME, item.scannedAtMillis)
                    put(KEY_IMAGE_PATH, item.imagePath ?: "")
                }
            )
        }

        preferences.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_LAYOUT = "layout"
        const val KEY_LABEL = "label"
        const val KEY_CONFIDENCE = "confidence"
        const val KEY_TIME = "time"
        const val KEY_IMAGE_PATH = "image_path"
        const val THUMBNAIL_DIRECTORY = "scan_history"
        const val MAX_HISTORY_ITEMS = 20
    }
}
