package com.opencode.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

data class Draft(
    val text: String,
    val attachments: List<String> = emptyList(),
    val model: String? = null,
    val agent: String? = null
)

class DraftRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = Gson()

    @Synchronized
    fun save(sessionId: String, draft: Draft) {
        preferences.edit()
            .putString(key(sessionId), gson.toJson(draft))
            .apply()
    }

    @Synchronized
    fun load(sessionId: String): Draft? = runCatching {
        preferences.getString(key(sessionId), null)?.let { gson.fromJson(it, Draft::class.java) }
    }.getOrNull()

    @Synchronized
    fun clear(sessionId: String) {
        preferences.edit().remove(key(sessionId)).apply()
    }

    @Synchronized
    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun key(sessionId: String): String = "$KEY_PREFIX$sessionId"

    companion object {
        private const val PREFS_NAME = "opencode_android_drafts"
        private const val KEY_PREFIX = "draft_"
    }
}
