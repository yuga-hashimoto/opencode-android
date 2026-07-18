package com.opencode.android

import android.app.Application
import android.os.Build
import com.opencode.android.data.connection.SecureSettingsRepository
import com.opencode.android.data.repository.AppRepository
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.local.LocalRuntimeManager
import com.opencode.android.runtime.local.LocalRuntimeTarget
import java.io.File

class OpenCodeApplication : Application() {
    lateinit var settings: SecureSettingsRepository
        private set

    lateinit var localRuntimeManager: LocalRuntimeManager
        private set

    lateinit var runtimeRegistry: RuntimeRegistry
        private set

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsRepository(this)
        localRuntimeManager = LocalRuntimeManager(
            runtimeDirectory = File(filesDir, "runtime"),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        )
        runtimeRegistry = RuntimeRegistry(
            store = settings,
            localTarget = LocalRuntimeTarget(localRuntimeManager)
        )
        repository = AppRepository(runtimeRegistry)
    }
}
