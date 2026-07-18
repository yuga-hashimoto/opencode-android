package com.opencode.android

import android.app.Application
import android.os.Build
import com.opencode.android.data.connection.SecureSettingsRepository
import com.opencode.android.data.repository.RuntimeActivityRepository
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.settings.AppPreferencesRepository
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.local.LocalRuntimeManager
import com.opencode.android.runtime.local.LocalRuntimeTarget
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OpenCodeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settings: SecureSettingsRepository
        private set

    lateinit var preferences: AppPreferencesRepository
        private set

    lateinit var localRuntimeManager: LocalRuntimeManager
        private set

    lateinit var runtimeRegistry: RuntimeRegistry
        private set

    lateinit var catalogRepository: RuntimeCatalogRepository
        private set

    lateinit var activityRepository: RuntimeActivityRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsRepository(this)
        preferences = AppPreferencesRepository(settings)
        localRuntimeManager = LocalRuntimeManager(
            runtimeDirectory = File(filesDir, "runtime"),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        )
        runtimeRegistry = RuntimeRegistry(
            store = settings,
            localTarget = LocalRuntimeTarget(localRuntimeManager)
        )
        catalogRepository = RuntimeCatalogRepository(runtimeRegistry, applicationScope)
        activityRepository = RuntimeActivityRepository(runtimeRegistry, applicationScope)
        applicationScope.launch {
            catalogRepository.state.collectLatest { catalog ->
                if (catalog.health != null) {
                    preferences.reconcile(catalog.providers, catalog.agents)
                }
            }
        }
    }
}
