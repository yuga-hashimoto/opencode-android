package com.opencode.android.feature.chat

internal enum class ChatErrorKind {
    RUNTIME_NOT_READY,
    TRANSIENT_CONNECTION,
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
    val transientSignals = listOf(
        "unexpected end of stream",
        "stream was reset",
        "connection reset",
        "connection closed",
        "connection aborted",
        "failed to connect",
        "socket closed",
        "socket is closed",
        "closed by peer",
        "timeout",
        "timed out",
        "event stream closed",
        "openCode event stream closed"
    )
    return when {
        runtimeNotReadySignals.any(normalized::contains) -> ChatErrorKind.RUNTIME_NOT_READY
        transientSignals.any(normalized::contains) -> ChatErrorKind.TRANSIENT_CONNECTION
        else -> ChatErrorKind.GENERIC
    }
}
