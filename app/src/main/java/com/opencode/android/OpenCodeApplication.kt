package com.opencode.android

import android.app.Application
import android.os.Build
import com.opencode.android.core.notification.RuntimeNotificationHelper
import com.opencode.android.data.connection.SecureSettingsRepository
import com.opencode.android.data.repository.RuntimeActivityRepository
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.settings.AppPreferencesRepository
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.local.DefaultLocalRuntimeUpdateEngine
import com.opencode.android.runtime.local.LocalRuntimeAccessCoordinator
import com.opencode.android.runtime.local.LocalRuntimeCommandRunner
import com.opencode.android.runtime.local.LocalRuntimeDiagnosticsCollector
import com.opencode.android.runtime.local.LocalRuntimeInstaller
import com.opencode.android.runtime.local.LocalRuntimeManager
import com.opencode.android.runtime.local.LocalRuntimeProcessLauncher
import com.opencode.android.runtime.local.LocalRuntimeReleaseClient
import com.opencode.android.runtime.local.LocalRuntimeServiceController
import com.opencode.android.runtime.local.LocalRuntimeTarget
import com.opencode.android.runtime.local.LocalRuntimeUpdater
import com.opencode.android.runtime.local.LocalProviderCredentialStore
import com.opencode.android.runtime.local.GitCredentialHelper
import com.opencode.android.runtime.local.GitCloneRepository
import com.opencode.android.runtime.local.VerifiedRuntimeDownloader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class OpenCodeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settings: SecureSettingsRepository
        private set

    lateinit var preferences: AppPreferencesRepository
        private set

    lateinit var localRuntimeManager: LocalRuntimeManager
        private set

    lateinit var localRuntimeController: LocalRuntimeServiceController
        private set

    lateinit var localRuntimeDiagnosticsCollector: LocalRuntimeDiagnosticsCollector
        private set

    lateinit var runtimeRegistry: RuntimeRegistry
        private set

    lateinit var catalogRepository: RuntimeCatalogRepository
        private set

    lateinit var activityRepository: RuntimeActivityRepository
        private set

    lateinit var notifications: RuntimeNotificationHelper
        private set

    lateinit var providerCredentials: LocalProviderCredentialStore
        private set

    lateinit var gitCloneRepository: GitCloneRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsRepository(this)
        preferences = AppPreferencesRepository(settings)
        notifications = RuntimeNotificationHelper(this)
        providerCredentials = LocalProviderCredentialStore(settings)
        val runtimeDirectory = File(filesDir, "runtime")
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val accessCoordinator = LocalRuntimeAccessCoordinator()
        val installer = LocalRuntimeInstaller(
            context = this,
            runtimeDirectory = runtimeDirectory,
            abi = abi,
            accessCoordinator = accessCoordinator
        )
        val launcher = LocalRuntimeProcessLauncher(
            runtimeDirectory = runtimeDirectory,
            portProbe = LocalRuntimeManager::defaultPortProbe,
            githubToken = { settings.githubToken },
            beforeStart = { installed ->
                runCatching { providerCredentials.syncToRuntime(installed.rootfs) }
                runCatching {
                    GitCredentialHelper(installed.rootfs) { settings.githubToken }.let { helper ->
                        if (settings.githubToken.isNullOrBlank()) helper.remove() else helper.install()
                    }
                }
            }
        )
        val commandRunner = LocalRuntimeCommandRunner(
            runtimeDirectory = runtimeDirectory,
            installedRuntimeProvider = installer::installedRuntime,
            accessCoordinator = accessCoordinator
        )
        gitCloneRepository = GitCloneRepository(
            runtimeDirectory = runtimeDirectory,
            installedRuntimeProvider = installer::installedRuntime,
            accessCoordinator = accessCoordinator,
            githubToken = { settings.githubToken }
        )
        val httpClient = OkHttpClient()
        val verifiedDownloader = VerifiedRuntimeDownloader(httpClient)
        val updater = LocalRuntimeUpdater(
            runtimeDirectory = runtimeDirectory,
            abi = abi,
            downloadAsset = { asset, destination, progress ->
                verifiedDownloader.download(
                    url = asset.url,
                    destination = destination,
                    expectedSha256 = asset.sha256,
                    expectedSizeBytes = asset.sizeBytes,
                    onProgress = progress
                )
            },
            candidateVersionProvider = { candidate ->
                val result = commandRunner.runShell(
                    commandText = "/usr/local/bin/${candidate.name} --version",
                    timeoutSeconds = 30L
                )
                require(result.exitCode == 0) {
                    "OpenCode update candidate validation failed: ${result.output}"
                }
                result.output.lineSequence().firstOrNull(String::isNotBlank)
                    ?: error("OpenCode update candidate returned no version")
            },
            accessCoordinator = accessCoordinator
        )
        val updateEngine = DefaultLocalRuntimeUpdateEngine(
            releaseClient = LocalRuntimeReleaseClient(httpClient),
            updater = updater
        )
        localRuntimeManager = LocalRuntimeManager(
            runtimeDirectory = runtimeDirectory,
            abi = abi,
            installer = installer,
            processLauncher = launcher,
            updateEngine = updateEngine
        )
        localRuntimeDiagnosticsCollector = LocalRuntimeDiagnosticsCollector(
            runtimeDirectory = runtimeDirectory,
            abi = abi,
            statusProvider = localRuntimeManager::status,
            processMetricsProvider = launcher::metrics,
            commandExecutor = commandRunner::run
        )
        localRuntimeController = LocalRuntimeServiceController(this)
        runtimeRegistry = RuntimeRegistry(
            store = settings,
            localTarget = LocalRuntimeTarget(localRuntimeManager)
        )
        val setupConfigured = hasUsableRuntimeSetup(
            localRuntimeStatus = localRuntimeManager.status(),
            hasRemoteConnection = settings.connections().isNotEmpty()
        )
        if (settings.onboardingCompleted != setupConfigured) {
            settings.onboardingCompleted = setupConfigured
        }
        catalogRepository = RuntimeCatalogRepository(runtimeRegistry, applicationScope)
        activityRepository = RuntimeActivityRepository(
            registry = runtimeRegistry,
            scope = applicationScope,
            onPermissionAsked = notifications::notifyPermission,
            onSessionIdle = notifications::notifySessionComplete,
            onSessionError = notifications::notifySessionError
        )
        applicationScope.launch {
            catalogRepository.state.collectLatest { catalog ->
                if (catalog.health != null) {
                    preferences.reconcile(catalog.providers, catalog.agents)
                }
            }
        }
        autoStartLocalRuntimeIfNeeded()
    }

    private var catalogWarmupJob: kotlinx.coroutines.Job? = null

    private fun autoStartLocalRuntimeIfNeeded() {
        if (!settings.onboardingCompleted) return
        if (localRuntimeManager.status() is LocalRuntimeStatus.NotInstalled) return
        val selectedId = settings.selectedRuntimeId
        if (selectedId != null && selectedId != LOCAL_RUNTIME_ID) return
        // The runtime is already installed at this point. Starting through the
        // install path recreates the environment on every cold launch, delaying
        // catalog synchronization and making the model picker look unavailable.
        localRuntimeController.start()
        applicationScope.launch {
            localRuntimeManager.state.collect { status ->
                if (status is LocalRuntimeStatus.Ready) {
                    if (runtimeRegistry.selected.value?.id != LOCAL_RUNTIME_ID) {
                        runtimeRegistry.select(LOCAL_RUNTIME_ID)
                    }
                    catalogWarmupJob?.cancel()
                    catalogWarmupJob = applicationScope.launch catalogWarmup@{
                        repeat(CATALOG_WARMUP_ATTEMPTS) {
                            catalogRepository.refresh()
                            kotlinx.coroutines.delay(CATALOG_WARMUP_DELAY_MS)
                            if (catalogRepository.state.value.providers.all.isNotEmpty()) return@catalogWarmup
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val LOCAL_RUNTIME_ID = "local-android"
        const val CATALOG_WARMUP_ATTEMPTS = 4
        const val CATALOG_WARMUP_DELAY_MS = 2500L
    }
}
