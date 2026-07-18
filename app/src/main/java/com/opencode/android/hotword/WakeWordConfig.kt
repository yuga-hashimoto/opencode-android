package com.opencode.android.hotword

object WakeWordConfig {
    const val DEFAULT_PHRASE = "open code"

    fun normalize(value: String): String =
        value.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .ifBlank { DEFAULT_PHRASE }

    fun grammarJson(value: String): String {
        val phrase = normalize(value)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "[\"$phrase\", \"[unk]\"]"
    }
}
