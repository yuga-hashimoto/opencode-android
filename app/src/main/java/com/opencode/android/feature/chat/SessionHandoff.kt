package com.opencode.android.feature.chat

private const val HANDOFF_HEADER =
    "以下は別の実行先から引き継いだ会話の要約です。続きから対応してください。"

fun buildHandoffPrompt(messages: List<ChatMessage>, maxChars: Int = 6000): String {
    val lines = messages.mapNotNull { message ->
        val text = message.text.trim()
        if (text.isEmpty()) return@mapNotNull null
        val role = if (message.isUser) "ユーザー" else "アシスタント"
        "$role: $text"
    }
    if (lines.isEmpty()) return HANDOFF_HEADER

    val kept = ArrayDeque<String>()
    var length = HANDOFF_HEADER.length + 2
    for (line in lines.asReversed()) {
        val addedLength = line.length + 2
        if (kept.isNotEmpty() && length + addedLength > maxChars) break
        kept.addFirst(line)
        length += addedLength
    }

    return buildString {
        append(HANDOFF_HEADER)
        append("\n\n")
        append(kept.joinToString("\n\n"))
    }
}
