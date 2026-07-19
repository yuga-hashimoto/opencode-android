package com.opencode.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opencode.android.OpenCodeApplication
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.feature.activity.ActivityScreen
import com.opencode.android.feature.activity.ActivityViewModel
import com.opencode.android.feature.activity.SessionDetailScreen
import com.opencode.android.feature.activity.SessionDetailViewModel
import com.opencode.android.feature.assistant.SpeechRecognizerManager
import com.opencode.android.feature.assistant.SpeechResult
import com.opencode.android.feature.chat.ChatViewModel
import com.opencode.android.feature.chat.OpenCodeChatScreen
import com.opencode.android.feature.chat.buildHandoffPrompt
import com.opencode.android.feature.home.HomeScreen
import com.opencode.android.feature.home.HomeViewModel
import com.opencode.android.feature.settings.SettingsScreen
import com.opencode.android.feature.settings.SettingsViewModel
import com.opencode.android.feature.workspace.LocalRuntimeManagementScreen
import com.opencode.android.feature.workspace.LocalRuntimeManagementViewModel
import com.opencode.android.feature.workspace.WorkspaceExplorerScreen
import com.opencode.android.feature.workspace.WorkspaceExplorerViewModel
import com.opencode.android.feature.workspace.WorkspaceViewModel
import com.opencode.android.feature.workspace.WorkspacesScreen
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Default.Dashboard),
    CHAT("chat", R.string.nav_chat, Icons.AutoMirrored.Filled.Chat),
    WORKSPACES("workspaces", R.string.nav_workspaces, Icons.Default.Folder),
    ACTIVITY("activity", R.string.nav_activity, Icons.Default.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings)
}

private const val WORKSPACE_DETAIL_ROUTE = "workspace-detail"
private const val SESSION_DETAIL_ROUTE = "session-detail"
private const val LOCAL_RUNTIME_MANAGEMENT_ROUTE = "local-runtime-management"

private fun speechLocaleTag(context: android.content.Context): String {
    val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale
    }
    return locale?.toLanguageTag()?.takeIf { it.isNotBlank() } ?: "en-US"
}

@Composable
fun OpenCodeApp(
    onOpenAssistantSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OpenCodeApplication
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    var pendingSession by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingHandoffPrompt by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedWorkspace by remember { mutableStateOf<WorkspaceRef?>(null) }
    var selectedSession by remember { mutableStateOf<OpenCodeSession?>(null) }

    val selectedRuntime by app.runtimeRegistry.selected.collectAsState()
    val runtimeTargets by app.runtimeRegistry.targets.collectAsState()
    val preferences by app.preferences.state.collectAsState()
    val homeViewModel: HomeViewModel = viewModel(
        key = "home",
        factory = ViewModelFactory { HomeViewModel(app.catalogRepository, app.preferences) }
    )
    val homeState by homeViewModel.state.collectAsState()

    val workspaceViewModel: WorkspaceViewModel = viewModel(
        key = "workspaces",
        factory = ViewModelFactory {
            WorkspaceViewModel(
                app.runtimeRegistry,
                app.catalogRepository,
                app.localRuntimeManager,
                app.localRuntimeController
            )
        }
    )
    val workspaceState by workspaceViewModel.state.collectAsState()

    val activityViewModel: ActivityViewModel = viewModel(
        key = "activity",
        factory = ViewModelFactory {
            ActivityViewModel(
                catalog = app.catalogRepository,
                activity = app.activityRepository,
                registry = app.runtimeRegistry,
                onPermissionResolved = app.notifications::cancelPermission
            )
        }
    )
    val activityState by activityViewModel.state.collectAsState()

    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings",
        factory = ViewModelFactory {
            SettingsViewModel(
                catalog = app.catalogRepository,
                preferences = app.preferences,
                credentials = app.providerCredentials,
                settings = app.settings,
                registry = app.runtimeRegistry
            )
        }
    )
    val settingsState by settingsViewModel.state.collectAsState()

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat-${selectedRuntime?.id ?: "none"}",
        factory = ViewModelFactory {
            ChatViewModel(
                backend = selectedRuntime,
                eventFlow = app.activityRepository.events,
                onPermissionResolved = app.activityRepository::resolvePermission
            )
        }
    )
    val chatState by chatViewModel.uiState.collectAsState()

    val speechManager = remember { SpeechRecognizerManager(context.applicationContext) }
    val voiceScope = rememberCoroutineScope()
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    var startVoiceAfterPermission by remember { mutableStateOf(false) }

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startOrStopVoiceInput() {
        if (voiceJob?.isActive == true) {
            voiceJob?.cancel()
            chatViewModel.stopListening()
            return
        }
        chatViewModel.startListening()
        voiceJob = voiceScope.launch {
            try {
                speechManager.startListening(speechLocaleTag(context)).collect { result ->
                    when (result) {
                        SpeechResult.Ready,
                        SpeechResult.Listening,
                        SpeechResult.Processing,
                        is SpeechResult.RmsChanged -> Unit
                        is SpeechResult.PartialResult -> chatViewModel.updateSpeechPartial(result.text)
                        is SpeechResult.Result -> {
                            chatViewModel.stopListening()
                            chatViewModel.sendMessage(result.text)
                        }
                        is SpeechResult.Error -> chatViewModel.reportSpeechError(result.message)
                    }
                }
            } finally {
                chatViewModel.stopListening()
            }
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && startVoiceAfterPermission) {
            startVoiceAfterPermission = false
            startOrStopVoiceInput()
        } else if (!granted) {
            startVoiceAfterPermission = false
            chatViewModel.reportSpeechError(context.getString(R.string.mic_permission_required))
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    val workspaceImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        voiceScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val imported = withContext(Dispatchers.IO) {
                    com.opencode.android.runtime.local.SafWorkspaceImporter(context).importTree(uri)
                }
                val existing = app.settings.safWorkspaceUris.toMutableList()
                if (uri.toString() !in existing) {
                    existing += uri.toString()
                    app.settings.safWorkspaceUris = existing
                }
                settingsViewModel.setAssistantWorkspacePath(imported.absolutePath)
                workspaceViewModel.refresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(speechManager) {
        onDispose {
            voiceJob?.cancel()
            speechManager.destroy()
        }
    }

    val requestVoiceInput: () -> Unit = {
        if (hasMicrophonePermission()) {
            startOrStopVoiceInput()
        } else {
            startVoiceAfterPermission = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(preferences.providerId, preferences.modelId, preferences.agentId) {
        chatViewModel.selectConfiguration(
            preferences.providerId,
            preferences.modelId,
            preferences.agentId
        )
    }

    LaunchedEffect(selectedRuntime?.id, workspaceState.workspaces, chatState.sessionId) {
        if (chatState.sessionId != null) return@LaunchedEffect
        val currentPath = chatState.selectedWorkspacePath
        val available = workspaceState.workspaces
        if (available.isNotEmpty() && available.none { it.path == currentPath }) {
            chatViewModel.selectWorkspace(available.first().path)
        }
    }

    val onHandoff: (String) -> Unit = { targetRuntimeId ->
        val prompt = buildHandoffPrompt(chatState.messages)
        pendingHandoffPrompt = targetRuntimeId to prompt
        app.runtimeRegistry.select(targetRuntimeId)
    }

    val showBottomBar = Destination.entries.any { destination ->
        destination.route == backStackEntry?.destination?.route
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(Destination.HOME.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    state = homeState,
                    onNewChat = {
                        pendingSession = null
                        chatViewModel.newSession()
                        navController.navigate(Destination.CHAT.route)
                    },
                    onOpenWorkspaces = { navController.navigate(Destination.WORKSPACES.route) },
                    onOpenActivity = { navController.navigate(Destination.ACTIVITY.route) },
                    onOpenSession = { id, title ->
                        pendingSession = id to title
                        navController.navigate(Destination.CHAT.route)
                    },
                    onRefresh = homeViewModel::refresh
                )
            }

            composable(Destination.CHAT.route) {
                LaunchedEffect(pendingSession) {
                    pendingSession?.let { (id, title) ->
                        chatViewModel.openSession(id, title)
                        pendingSession = null
                    }
                }
                LaunchedEffect(pendingHandoffPrompt, selectedRuntime?.id) {
                    val pending = pendingHandoffPrompt
                    if (pending != null && selectedRuntime?.id == pending.first) {
                        chatViewModel.sendMessage(pending.second)
                        pendingHandoffPrompt = null
                    }
                }
                OpenCodeChatScreen(
                    state = chatState,
                    providers = settingsState.providers,
                    agents = settingsState.agents,
                    workspaces = workspaceState.workspaces,
                    selectedProviderId = settingsState.providerId,
                    selectedModelId = settingsState.modelId,
                    selectedAgentId = settingsState.agentId,
                    otherRuntimes = runtimeTargets.filter { it.id != selectedRuntime?.id },
                    onSelectModel = settingsViewModel::selectModel,
                    onSelectAgent = settingsViewModel::selectAgent,
                    onSelectWorkspace = chatViewModel::selectWorkspace,
                    onSendMessage = chatViewModel::sendMessage,
                    onPermission = chatViewModel::respondToPermission,
                    onAbort = chatViewModel::abort,
                    onMic = requestVoiceInput,
                    onHandoff = onHandoff
                )
            }

            composable(Destination.WORKSPACES.route) {
                WorkspacesScreen(
                    state = workspaceState,
                    onSelectRuntime = workspaceViewModel::selectRuntime,
                    onSaveConnection = workspaceViewModel::saveConnection,
                    onDeleteConnection = workspaceViewModel::deleteConnection,
                    onTestConnection = workspaceViewModel::testConnection,
                    onRefresh = workspaceViewModel::refresh,
                    onOpenWorkspace = { workspace ->
                        selectedWorkspace = workspace
                        navController.navigate(WORKSPACE_DETAIL_ROUTE)
                    },
                    onSetupLocal = workspaceViewModel::setupLocalRuntime,
                    onStartLocal = workspaceViewModel::startLocalRuntime,
                    onStopLocal = workspaceViewModel::stopLocalRuntime,
                    onReinstallLocal = workspaceViewModel::reinstallLocalRuntime,
                    onOpenLocalManagement = {
                        navController.navigate(LOCAL_RUNTIME_MANAGEMENT_ROUTE)
                    }
                )
            }

            composable(LOCAL_RUNTIME_MANAGEMENT_ROUTE) {
                val managementViewModel: LocalRuntimeManagementViewModel = viewModel(
                    key = "local-runtime-management",
                    factory = ViewModelFactory {
                        LocalRuntimeManagementViewModel(
                            runtimeState = app.localRuntimeManager.state,
                            lastOperationState = app.localRuntimeManager.lastOperation,
                            diagnosticsProvider = {
                                withContext(Dispatchers.IO) {
                                    app.localRuntimeDiagnosticsCollector.collect()
                                }
                            },
                            updateCheckProvider = app.localRuntimeManager::checkForUpdate,
                            rollbackVersionProvider = app.localRuntimeManager::rollbackVersion,
                            repairAction = app.localRuntimeController::reinstall,
                            updateAction = app.localRuntimeController::update,
                            rollbackAction = app.localRuntimeController::rollback,
                            deleteAction = app.localRuntimeController::delete
                        )
                    }
                )
                val managementState by managementViewModel.state.collectAsState()
                LaunchedEffect(managementState.deleteCompleted) {
                    if (managementState.deleteCompleted) {
                        managementViewModel.consumeDeleteCompleted()
                        workspaceViewModel.refresh()
                        navController.popBackStack()
                    }
                }
                LocalRuntimeManagementScreen(
                    state = managementState,
                    onBack = { navController.popBackStack() },
                    onRefresh = managementViewModel::refresh,
                    onCheckForUpdate = managementViewModel::checkForUpdate,
                    onRepair = managementViewModel::repair,
                    onRequestUpdate = managementViewModel::requestUpdate,
                    onDismissUpdate = managementViewModel::dismissUpdate,
                    onConfirmUpdate = managementViewModel::confirmUpdate,
                    onRequestRollback = managementViewModel::requestRollback,
                    onDismissRollback = managementViewModel::dismissRollback,
                    onConfirmRollback = managementViewModel::confirmRollback,
                    onRequestDelete = managementViewModel::requestDelete,
                    onDismissDelete = managementViewModel::dismissDelete,
                    onConfirmDelete = managementViewModel::confirmDelete
                )
            }

            composable(WORKSPACE_DETAIL_ROUTE) {
                val workspace = selectedWorkspace
                val runtime = selectedRuntime
                if (workspace == null || runtime == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val explorerViewModel: WorkspaceExplorerViewModel = viewModel(
                        key = "workspace-explorer-${runtime.id}-${workspace.id}",
                        factory = ViewModelFactory {
                            WorkspaceExplorerViewModel(runtime, workspace)
                        }
                    )
                    val explorerState by explorerViewModel.state.collectAsState()
                    WorkspaceExplorerScreen(
                        state = explorerState,
                        onBack = { navController.popBackStack() },
                        onRefresh = explorerViewModel::refresh,
                        onOpenNode = explorerViewModel::open,
                        onCloseFile = explorerViewModel::closeFile,
                        onNavigateUp = explorerViewModel::navigateUp,
                        onSearch = explorerViewModel::search,
                        onRefreshChanges = explorerViewModel::refreshChanges
                    )
                }
            }

            composable(Destination.ACTIVITY.route) {
                ActivityScreen(
                    state = activityState,
                    onRefresh = activityViewModel::refresh,
                    onInspectSession = { session ->
                        selectedSession = session
                        navController.navigate(SESSION_DETAIL_ROUTE)
                    },
                    onOpenSession = { id, title ->
                        pendingSession = id to title
                        navController.navigate(Destination.CHAT.route)
                    },
                    onPermission = activityViewModel::respondToPermission,
                    onRenameSession = activityViewModel::renameSession,
                    onDeleteSession = activityViewModel::deleteSession
                )
            }

            composable(SESSION_DETAIL_ROUTE) {
                val session = selectedSession
                val runtime = selectedRuntime
                if (session == null || runtime == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val detailViewModel: SessionDetailViewModel = viewModel(
                        key = "session-detail-${runtime.id}-${session.id}",
                        factory = ViewModelFactory {
                            SessionDetailViewModel(runtime, session)
                        }
                    )
                    val detailState by detailViewModel.state.collectAsState()
                    SessionDetailScreen(
                        state = detailState,
                        onBack = { navController.popBackStack() },
                        onRefresh = detailViewModel::refresh,
                        onContinueChat = {
                            pendingSession = session.id to session.title
                            navController.navigate(Destination.CHAT.route)
                        }
                    )
                }
            }

            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    state = settingsState,
                    onOpenAssistantSettings = onOpenAssistantSettings,
                    onTtsChange = settingsViewModel::setTtsEnabled,
                    onContinuousChange = settingsViewModel::setContinuousConversation,
                    onDraftProviderId = settingsViewModel::updateDraftProviderId,
                    onDraftApiKey = settingsViewModel::updateDraftApiKey,
                    onSaveApiKey = settingsViewModel::saveApiKey,
                    onClearApiKey = settingsViewModel::clearApiKey,
                    onAssistantRuntime = settingsViewModel::setAssistantRuntimeId,
                    onAssistantWorkspace = settingsViewModel::setAssistantWorkspacePath,
                    onImportWorkspace = { workspaceImportLauncher.launch(null) },
                    onRequestNotifications = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }
}
