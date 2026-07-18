package com.opencode.android.backend

sealed interface LocalRuntimeStatus {
    data object NotInstalled : LocalRuntimeStatus
    data class Installing(val progress: Float?) : LocalRuntimeStatus
    data class Ready(val version: String, val port: Int) : LocalRuntimeStatus
    data class Broken(val reason: String) : LocalRuntimeStatus
    data class UnsupportedAbi(val abi: String) : LocalRuntimeStatus
}
