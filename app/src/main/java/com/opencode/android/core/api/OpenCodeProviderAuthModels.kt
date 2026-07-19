package com.opencode.android.core.api

data class ProviderAuthMethod(
    val type: String,
    val label: String
)

data class ProviderAuthAuthorization(
    val url: String,
    val method: String,
    val instructions: String
)
