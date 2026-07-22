package com.opencode.android

import com.opencode.android.runtime.LocalRuntimeStatus

/**
 * Returns whether the app has at least one complete, currently usable execution path.
 *
 * A remote connection is independently usable. A local runtime is usable once it is
 * installed and can start. Provider credentials are NOT required to finish onboarding:
 * OpenCode works with providers that need no key (e.g. local Ollama), and users can
 * connect any provider later from Settings.
 */
internal fun hasUsableRuntimeSetup(
    localRuntimeStatus: LocalRuntimeStatus,
    hasRemoteConnection: Boolean
): Boolean {
    if (hasRemoteConnection) return true

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
