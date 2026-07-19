package com.opencode.android

import android.app.Application
import android.os.Build
import com.opencode.android.core.notification.RuntimeNotificationHelper
import com.opencode.android.data.connection.SecureSettingsRepository
import com.opencode.android.data.repository.RuntimeActivityRepository
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.settings.AppPreferencesRepository
import com.opencode.android.runtime.RuntimeRegistry
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

    lateinit var nsdDiscovery: com.opencode.android.feature.connection.OpenCodeNsdDiscovery
        private set

    lateinit var wakeWordPackManager: com.opencode.android.feature.assistant.WakeWordPackManager
        private set

    lateinit var wakeWordListeningController: com.opencode.android.feature.assistant.WakeWordListeningController
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsRepository(this)
        // Anyone with a runtime/connection already configured set that up before onboarding
        // existed — treat them as onboarded instead of showing the first-run wizard on update.
        if (!settings.onboardingCompleted &&
            (settings.selectedRuntimeId != null || settings.connections().isNotEmpty())
        ) {
            settings.onboardingCompleted = true
        }
        preferences = AppPreferencesRepository(settings)
        notifications = RuntimeNotificationHelper(this)
        providerCredentials = LocalProviderCredentialStore(settings)
        nsdDiscovery = com.opencode.android.feature.connection.OpenCodeNsdDiscovery(this)
        val wakeWordPublicKey = assets.open("wakeword-pack-public-key.pem")
            .bufferedReader()
            .use { reader ->
                com.opencode.android.feature.assistant.WakeWordPackManager.parseRsaPublicKeyPem(
                    reader.readText()
                )
            }
        wakeWordPackManager = com.opencode.android.feature.assistant.WakeWordPackManager(
            rootDirectory = File(filesDir, "assistant").apply { mkdirs() },
            trustedPublicKey = wakeWordPublicKey
        )
        wakeWordListeningController =
            com.opencode.android.feature.assistant.WakeWordListeningController(this)
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
            beforeStart = { installed ->
                providerCredentials.syncToRuntime(installed.rootfs)
            }
        )
        val commandRunner = LocalRuntimeCommandRunner(
            runtimeDirectory = runtimeDirectory,
            installedRuntimeProvider = installer::installedRuntime,
            accessCoordinator = accessCoordinator
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
        catalogRepository = RuntimeCatalogRepository(runtimeRegistry, applicationScope)
        activityRepository = RuntimeActivityRepository(
            registry = runtimeRegistry,
            scope = applicationScope,
            onPermissionAsked = notifications::notifyPermission,
            onSessionIdle = notifications::notifySessionComplete,
            onSessionError = notifications::notifySessionError,
            autoAllowReadOnlyTools = { settings.autoAllowReadOnlyTools }
        )
        applicationScope.launch {
            catalogRepository.state.collectLatest { catalog ->
                if (catalog.health != null) {
                    preferences.reconcile(catalog.providers, catalog.agents)
                }
            }
        }
    }
}
