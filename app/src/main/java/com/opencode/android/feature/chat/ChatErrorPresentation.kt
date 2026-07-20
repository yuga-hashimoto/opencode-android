package com.opencode.android.feature.chat

internal enum class ChatErrorKind {
    RUNTIME_NOT_READY,
    GENERIC
}

internal fun classifyChatError(message: String?): ChatErrorKind? {
    val normalized = message?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    val runtimeNotReadySignals = listOf(
        "runtime is not installed",
        "connection is not configured",
        "runtime is not configured",
        "no runtime configured"
    )
    return if (runtimeNotReadySignals.any(normalized::contains)) {
        ChatErrorKind.RUNTIME_NOT_READY
    } else {
        ChatErrorKind.GENERIC
    }
}
