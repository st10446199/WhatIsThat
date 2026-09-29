/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScanHistoryItem(
    val label: String,
    val confidencePercent: Int,
    val scannedAtMillis: Long
)

class ScanHistoryStore(context: Context) {
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
                            scannedAtMillis = item.getLong(KEY_TIME)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(result: ScanResult): List<ScanHistoryItem> {
        val updated = listOf(
            ScanHistoryItem(
                label = result.label,
                confidencePercent = result.confidencePercent,
                scannedAtMillis = System.currentTimeMillis()
            )
        ) + load()

        val limited = updated.take(MAX_HISTORY_ITEMS)
        save(limited)
        return limited
    }

    fun clear() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(items: List<ScanHistoryItem>) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(KEY_LABEL, item.label)
                    put(KEY_CONFIDENCE, item.confidencePercent)
                    put(KEY_TIME, item.scannedAtMillis)
                }
            )
        }

        preferences.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_LABEL = "label"
        const val KEY_CONFIDENCE = "confidence"
        const val KEY_TIME = "time"
        const val MAX_HISTORY_ITEMS = 20
    }
}
