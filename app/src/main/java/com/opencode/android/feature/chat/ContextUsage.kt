package com.opencode.android.feature.chat

internal fun contextUsagePercent(inputTokens: Long?, contextLimit: Long?): Int? {
    if (inputTokens == null || contextLimit == null || inputTokens < 0 || contextLimit <= 0) {
        return null
    }
    return ((inputTokens.toDouble() / contextLimit.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}
