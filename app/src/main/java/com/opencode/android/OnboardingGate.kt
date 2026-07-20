package com.opencode.android

import com.opencode.android.runtime.LocalRuntimeStatus

/**
 * Returns whether the app has at least one complete, currently usable execution path.
 *
 * A remote connection is independently usable. A local runtime is usable only
 * after installation and after at least one provider credential is stored.
 */
internal fun hasUsableRuntimeSetup(
    localRuntimeStatus: LocalRuntimeStatus,
    hasLocalProviderCredential: Boolean,
    hasRemoteConnection: Boolean
): Boolean {
    if (hasRemoteConnection) return true
    if (!hasLocalProviderCredential) return false

    return when (localRuntimeStatus) {
        is LocalRuntimeStatus.Stopped,
        is LocalRuntimeStatus.Starting,
        is LocalRuntimeStatus.Updating,
        is LocalRuntimeStatus.Ready -> true
        LocalRuntimeStatus.NotInstalled,
        is LocalRuntimeStatus.Installing,
        is LocalRuntimeStatus.Broken,
        is LocalRuntimeStatus.UnsupportedAbi -> false
    }
}
