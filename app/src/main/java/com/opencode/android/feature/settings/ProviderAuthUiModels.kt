package com.opencode.android.feature.settings

import com.opencode.android.core.api.ProviderAuthAuthorization
import com.opencode.android.core.api.ProviderAuthMethod

data class ProviderAuthDialogState(
    val providerId: String,
    val providerName: String,
    val methods: List<ProviderAuthMethod>,
    val methodIndex: Int? = null,
    val inputs: Map<String, String> = emptyMap(),
    val apiKey: String = "",
    val authorization: ProviderAuthAuthorization? = null,
    val isSubmitting: Boolean = false,
    val failed: Boolean = false,
    val error: String? = null
) {
    val selectedMethod: ProviderAuthMethod?
        get() = methodIndex?.let(methods::getOrNull)

    val visiblePrompts
        get() = selectedMethod?.prompts.orEmpty().filter { it.isVisible(inputs) }

    val promptsComplete: Boolean
        get() = visiblePrompts.all { prompt ->
            when (prompt.type) {
                "text", "select" -> !inputs[prompt.key].isNullOrBlank()
                else -> true
            }
        }
}

enum class ProviderAuthNotice {
    CONNECTED,
    DISCONNECTED
}
