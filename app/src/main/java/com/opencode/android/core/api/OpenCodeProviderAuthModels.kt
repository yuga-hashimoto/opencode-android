package com.opencode.android.core.api

import com.google.gson.annotations.SerializedName

data class ProviderAuthWhen(
    val key: String,
    val op: String,
    val value: String
) {
    fun matches(inputs: Map<String, String>): Boolean {
        val actual = inputs[key] ?: return false
        return when (op) {
            "eq" -> actual == value
            "neq" -> actual != value
            else -> false
        }
    }
}

data class ProviderAuthOption(
    val label: String,
    val value: String,
    val hint: String? = null
)

data class ProviderAuthPrompt(
    val type: String,
    val key: String,
    val message: String,
    val placeholder: String? = null,
    val options: List<ProviderAuthOption> = emptyList(),
    @SerializedName("when") val whenCondition: ProviderAuthWhen? = null
) {
    fun isVisible(inputs: Map<String, String>): Boolean = whenCondition?.matches(inputs) ?: true
}

data class ProviderAuthMethod(
    val type: String,
    val label: String,
    val prompts: List<ProviderAuthPrompt> = emptyList()
)

data class ProviderAuthAuthorization(
    val url: String,
    val method: String,
    val instructions: String
)
