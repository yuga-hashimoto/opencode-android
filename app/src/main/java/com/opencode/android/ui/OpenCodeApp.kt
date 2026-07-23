package com.opencode.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.opencode.android.feature.activity.SessionImportSheet
import com.opencode.android.feature.assistant.SpeechRecognizerManager
import com.opencode.android.feature.assistant.SpeechResult
import com.opencode.android.feature.chat.ChatHomeScreen
import com.opencode.android.feature.chat.ChatViewModel
import com.opencode.android.feature.chat.buildHandoffPrompt
import com.opencode.android.feature.onboarding.AndroidSetupScreen
import com.opencode.android.feature.onboarding.OnboardingChoiceScreen
import com.opencode.android.feature.schedule.ScheduleScreen
import com.opencode.android.feature.schedule.ScheduleViewModel
import com.opencode.android.feature.settings.DiagnosticsSheet
import com.opencode.android.feature.settings.ProviderSettingsScreen
import com.opencode.android.feature.settings.GitHubRepo
import com.opencode.android.feature.settings.SettingsScreenV2
import com.opencode.android.feature.settings.SettingsViewModel
import com.opencode.android.feature.settings.UsageScreen
import com.opencode.android.feature.settings.VoiceSettingsScreen
import com.opencode.android.feature.search.CommandPaletteSheet
import com.opencode.android.feature.workspace.CodeViewerScreen
import com.opencode.android.feature.workspace.LocalRuntimeManagementScreen
import com.opencode.android.feature.workspace.LocalRuntimeManagementViewModel
import com.opencode.android.feature.workspace.RemoteConnectionScreen
import com.opencode.android.feature.workspace.WorkspaceExplorerScreen
import com.opencode.android.feature.workspace.WorkspaceExplorerViewModel
import com.opencode.android.feature.workspace.WorkspaceViewModel
import com.opencode.android.feature.workspace.WorkspacesScreen
import com.opencode.android.ui.theme.AppTheme
import com.opencode.android.ui.theme.OpenCodeAndroidTheme
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.runtime.local.GitCloneResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_ANDROID_SETUP = "android-setup"
private const val ROUTE_REMOTE_CONNECTION = "remote-connection"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SETTINGS_VOICE = "settings-voice"
private const val ROUTE_SETTINGS_PROVIDERS = "settings-providers"
private const val ROUTE_SETTINGS_MCP = "settings-mcp"
private const val ROUTE_SETTINGS_SERVER_INFO = "settings-server-info"
private const val ROUTE_WORKSPACES = "workspaces"
private const val ROUTE_ACTIVITY = "activity"
private const val WORKSPACE_DETAIL_ROUTE = "workspace-detail"
private const val SESSION_DETAIL_ROUTE = "session-detail"
private const val LOCAL_RUNTIME_MANAGEMENT_ROUTE = "local-runtime-management"
private const val ROUTE_CODE_VIEWER = "code-viewer"
private const val ROUTE_USAGE = "usage"

/** Routes whose top bar exposes the hamburger menu / drawer swipe gesture. */
private val DRAWER_ROOT_ROUTES = setOf(ROUTE_CHAT, ROUTE_SETTINGS, ROUTE_SCHEDULE)

private fun speechLocaleTag(context: android.content.Context): String {
    val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale
    }
    return locale?.toLanguageTag()?.takeIf { it.isNotBlank() } ?: "en-US"
}

/** Relative-time label ("3時間前" / "3 hours ago") for the drawer's recent-chats list. */
private fun relativeTimeLabel(context: android.content.Context, epochMillis: Long): String {
    val diffMillis = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> context.getString(R.string.drawer_time_just_now)
        hours < 1L -> context.getString(R.string.drawer_time_minutes_ago, minutes)
        days < 1L -> context.getString(R.string.drawer_time_hours_ago, hours)
        else -> context.getString(R.string.drawer_time_days_ago, days)
    }
}

@Composable
fun OpenCodeApp(
    onOpenAssistantSettings: () -> Unit,
    appTheme: AppTheme = AppTheme.DARK,
    uiFontSize: Int = 16,
    targetSessionId: String? = null,
    deepLinkConnectionUrl: String? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as OpenCodeApplication
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    var pendingSession by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingHandoffPrompt by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedWorkspace by remember { mutableStateOf<WorkspaceRef?>(null) }
    var selectedSession by remember { mutableStateOf<OpenCodeSession?>(null) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showSessionImport by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val selectedRuntime by app.runtimeRegistry.selected.collectAsState()
    val runtimeTargets by app.runtimeRegistry.targets.collectAsState()
    val preferences by app.preferences.state.collectAsState()

    var sidebarGrouping by remember { mutableStateOf(preferences.sidebarGrouping) }
    var collapsedSections by remember { mutableStateOf(setOf<String>()) }

    val workspaceViewModel: WorkspaceViewModel = viewModel(
        key = "workspaces",
        factory = ViewModelFactory {
            WorkspaceViewModel(
                app.runtimeRegistry,
                app.catalogRepository,
                app.localRuntimeManager,
                app.localRuntimeController,
                app.settings,
                java.io.File(context.filesDir, "runtime/workspace")
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
                onPermissionResolved = app.activityRepository::resolvePermission,
                onSessionCreated = app.catalogRepository::refreshSessionsOnly
            )
        }
    )
    val chatState by chatViewModel.uiState.collectAsState()

    val speechManager = remember { SpeechRecognizerManager(context.applicationContext) }
    val voiceScope = rememberCoroutineScope()
    settingsViewModel.onLocalRuntimeRestartNeeded = {
        voiceScope.launch {
            workspaceViewModel.stopLocalRuntime()
            kotlinx.coroutines.delay(2000)
            workspaceViewModel.startLocalRuntime()
        }
    }
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    var startVoiceAfterPermission by remember { mutableStateOf(false) }
    var startWakeWordAfterPermission by remember { mutableStateOf(false) }

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
                        SpeechResult.Listening -> Unit
                        SpeechResult.Processing -> chatViewModel.showSpeechProcessing()
                        is SpeechResult.PartialResult -> chatViewModel.updateSpeechPartial(result.text)
                        is SpeechResult.Result -> {
                            chatViewModel.updateSpeechPartial(result.text)
                            chatViewModel.stopListening()
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
        } else if (granted && startWakeWordAfterPermission) {
            startWakeWordAfterPermission = false
            com.opencode.android.feature.wakeword.WakeWordService.start(context)
        } else if (!granted) {
            startVoiceAfterPermission = false
            startWakeWordAfterPermission = false
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
                workspaceViewModel.addProject("/workspace/${imported.name}")
                workspaceViewModel.refresh()
                chatViewModel.selectWorkspace("/workspace/${imported.name}")
            }
        }
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        voiceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    com.opencode.android.runtime.local.AttachmentImporter(context).import(uri)
                }
            }.onSuccess { attachment ->
                chatViewModel.addAttachment(attachment)
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

    LaunchedEffect(preferences.wakeWordEnabled) {
        if (preferences.wakeWordEnabled && hasMicrophonePermission()) {
            if (!com.opencode.android.feature.wakeword.WakeWordService.isRunning(context)) {
                com.opencode.android.feature.wakeword.WakeWordService.start(context)
            }
        } else if (!preferences.wakeWordEnabled) {
            com.opencode.android.feature.wakeword.WakeWordService.stop(context)
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

    LaunchedEffect(preferences.autoAcceptPermissions) {
        chatViewModel.setAutoAcceptPermissions(preferences.autoAcceptPermissions)
    }

    LaunchedEffect(selectedRuntime?.id, workspaceState.workspaces, chatState.sessionId) {
        if (chatState.sessionId != null) return@LaunchedEffect
        val currentPath = chatState.selectedWorkspacePath
        val available = workspaceState.workspaces
        if (currentPath == null && available.isNotEmpty()) {
            chatViewModel.selectWorkspace(available.first().path)
        }
    }

    LaunchedEffect(targetSessionId) {
        targetSessionId?.let { id ->
            pendingSession = id to id
            navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
        }
    }

    LaunchedEffect(deepLinkConnectionUrl) {
        deepLinkConnectionUrl?.let {
            navController.navigate(ROUTE_REMOTE_CONNECTION) { launchSingleTop = true }
        }
    }

    val onHandoff: (String) -> Unit = { targetRuntimeId ->
        val prompt = buildHandoffPrompt(chatState.messages)
        pendingHandoffPrompt = targetRuntimeId to prompt
        app.runtimeRegistry.select(targetRuntimeId)
    }

    // First-run gate: onboarding is the start destination until completed or skipped.
    val startDestination = remember { if (app.settings.onboardingCompleted) ROUTE_CHAT else ROUTE_ONBOARDING }
    val completeOnboardingAndGoToChat: () -> Unit = {
        app.settings.onboardingCompleted = true
        navController.navigate(ROUTE_CHAT) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    val recentSessions = remember(activityState.sessions, activityState.activeSessionIds, activityState.completedSessionIds) {
        activityState.sessions.take(25).map { session ->
            DrawerRecentSession(
                id = session.id,
                title = session.title.ifBlank { session.slug ?: session.id },
                relativeTime = relativeTimeLabel(context, session.time.updated ?: session.time.created),
                directory = session.directory,
                isActive = session.id in activityState.activeSessionIds,
                hasUnread = session.id in activityState.completedSessionIds
            )
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val currentRoute = backStackEntry?.destination?.route

    fun closeDrawer() {
        drawerScope.launch { drawerState.close() }
    }

    OpenCodeAndroidTheme(
        appTheme = AppTheme.fromKey(preferences.theme),
        uiFontSize = preferences.uiFontSize
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute in DRAWER_ROOT_ROUTES,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawerContent(
                    recentSessions = recentSessions,
                    workspaces = workspaceState.workspaces,
                    selectedWorkspacePath = chatState.selectedWorkspacePath,
                    onNewChat = {
                        closeDrawer()
                        pendingSession = null
                        chatViewModel.newSession()
                        chatViewModel.selectWorkspace(null)
                        navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
                    },
                    onSelectProject = { workspace ->
                        closeDrawer()
                        pendingSession = null
                        chatViewModel.newSession()
                        chatViewModel.selectWorkspace(workspace.path)
                        navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
                    },
                    onOpenSession = { id, title ->
                        closeDrawer()
                        app.activityRepository.markSessionRead(id)
                        pendingSession = id to title
                        navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
                    },
                    onNavigate = { route ->
                        closeDrawer()
                        if (route == "command-palette") {
                            showCommandPalette = true
                        } else if (route == "session-import") {
                            showSessionImport = true
                        } else {
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    },
                    onDeleteSession = { sessionId ->
                        voiceScope.launch {
                            runCatching { selectedRuntime?.deleteSession(sessionId) }
                            app.catalogRepository.refreshSessionsOnly()
                        }
                    },
                    onArchiveSession = { sessionId ->
                        voiceScope.launch {
                            runCatching { selectedRuntime?.archiveSession(sessionId) }
                            app.catalogRepository.refreshSessionsOnly()
                        }
                    },
                    sidebarGrouping = sidebarGrouping,
                    onGroupingChange = { sidebarGrouping = it },
                    collapsedSections = collapsedSections,
                    onToggleSection = { section ->
                        collapsedSections = if (section in collapsedSections) {
                            collapsedSections - section
                        } else {
                            collapsedSections + section
                        }
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(ROUTE_ONBOARDING) {
                OnboardingChoiceScreen(
                    onSelectAndroid = { navController.navigate(ROUTE_ANDROID_SETUP) },
                    onSelectRemote = { navController.navigate(ROUTE_REMOTE_CONNECTION) },
                    onAddRemoteLater = { navController.navigate(ROUTE_REMOTE_CONNECTION) }
                )
            }

            composable(ROUTE_ANDROID_SETUP) {
                val localRuntimeStatus by app.localRuntimeManager.state.collectAsState()
                AndroidSetupScreen(
                    runtimeStatus = localRuntimeStatus,
                    onStartRuntimeSetup = workspaceViewModel::setupLocalRuntime,
                    settingsState = settingsState,
                    onOpenProviderAuth = settingsViewModel::openProviderAuth,
                    onSelectProviderAuthMethod = settingsViewModel::selectProviderAuthMethod,
                    onProviderAuthInput = settingsViewModel::updateProviderAuthInput,
                    onProviderApiKey = settingsViewModel::updateProviderApiKey,
                    onSubmitProviderAuth = settingsViewModel::submitProviderAuth,
                    onCompleteProviderOAuth = settingsViewModel::completeProviderOAuth,
                    onDisconnectProvider = settingsViewModel::disconnectProvider,
                    onDismissProviderAuth = settingsViewModel::dismissProviderAuth,
                    onRefreshProviderAuth = settingsViewModel::refreshProviderAuth,
                    onRefreshCatalog = app.catalogRepository::refreshProvidersOnly,
                    onConnectGitHub = { settingsViewModel.beginGitHubDeviceFlow() },
                    onOpenGitHubVerification = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    },
                    onDisconnectGitHub = settingsViewModel::disconnectGitHub,
                    onBack = { navController.popBackStack() },
                    onFinish = completeOnboardingAndGoToChat
                )
            }

            composable(ROUTE_REMOTE_CONNECTION) {
                RemoteConnectionScreen(
                    onTestConnection = workspaceViewModel::testConnection,
                    onSaveConnection = workspaceViewModel::saveConnection,
                    onBack = { navController.popBackStack() },
                    onConnected = completeOnboardingAndGoToChat
                )
            }

            composable(ROUTE_CHAT) {
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
                ChatHomeScreen(
                    state = chatState,
                    providers = settingsState.providers,
                    agents = settingsState.agents,
                    workspaces = workspaceState.workspaces,
                    selectedProviderId = settingsState.providerId,
                    selectedModelId = settingsState.modelId,
                    selectedAgentId = settingsState.agentId,
                    runtimeTargets = runtimeTargets,
                    selectedRuntimeId = selectedRuntime?.id,
                    onSelectRuntime = { id ->
                        if (id != selectedRuntime?.id) {
                            if (chatState.messages.isNotEmpty()) {
                                onHandoff(id)
                            } else {
                                app.runtimeRegistry.select(id)
                            }
                        }
                    },
                    onSelectModel = settingsViewModel::selectModel,
                    onSelectAgent = settingsViewModel::selectAgent,
                    onSelectWorkspace = chatViewModel::selectWorkspace,
                    selectedVariant = chatState.selectedVariant,
                    onSelectVariant = chatViewModel::selectVariant,
                    onAttach = { attachmentLauncher.launch("*/*") },
                    onRemoveAttachment = chatViewModel::removeAttachment,
                    favoriteModelKeys = settingsState.favoriteModelKeys,
                    recentModelKeys = settingsState.recentModelKeys,
                    onToggleFavorite = settingsViewModel::toggleFavoriteModel,
                    onSelectQuestionAnswer = chatViewModel::selectQuestionAnswer,
                    onSubmitQuestion = chatViewModel::submitQuestion,
                    autoAcceptPermissions = settingsState.autoAcceptPermissions,
                    onToggleAutoAccept = settingsViewModel::setAutoAcceptPermissions,
                    onSendMessage = chatViewModel::sendMessage,
                    onPermission = chatViewModel::respondToPermission,
                    onAbort = chatViewModel::abort,
                    onMic = requestVoiceInput,
                    onNewChat = {
                        pendingSession = null
                        chatViewModel.newSession()
                    },
                    onOpenHistory = {
                        navController.navigate(ROUTE_ACTIVITY) { launchSingleTop = true }
                    },
                    onOpenLocalSetup = {
                        navController.navigate(ROUTE_ANDROID_SETUP) { launchSingleTop = true }
                    },
                    onOpenRemoteSetup = {
                        navController.navigate(ROUTE_REMOTE_CONNECTION) { launchSingleTop = true }
                    },
                    onRefreshCatalog = app.catalogRepository::refreshProvidersOnly,
                    onOpenDrawer = {
                        // Refresh sessions when the drawer is opened so a chat
                        // created during this run is visible immediately.
                        app.catalogRepository.refreshSessionsOnly()
                        drawerScope.launch { drawerState.open() }
                    }
                )
            }

            composable(ROUTE_SCHEDULE) {
                val scheduleViewModel: ScheduleViewModel = viewModel(
                    key = "schedule",
                    factory = ViewModelFactory { ScheduleViewModel() }
                )
                val scheduleItems by scheduleViewModel.filteredSchedules.collectAsState()
                val activeOnly by scheduleViewModel.activeOnly.collectAsState()
                ScheduleScreen(
                    items = scheduleItems,
                    activeOnly = activeOnly,
                    onActiveOnlyChange = scheduleViewModel::setActiveOnly,
                    onToggle = scheduleViewModel::toggleSchedule,
                    onAdd = scheduleViewModel::addSchedule,
                    onDelete = scheduleViewModel::deleteSchedule,
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } }
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreenV2(
                    assistantConfigured = settingsState.assistantRuntimeId != null,
                    notificationsEnabled = notificationsEnabled,
                    onToggleNotifications = { enabled ->
                        // Local UI toggle only — Android has no API to programmatically revoke
                        // notification access, so turning this off is visual until the user
                        // also disables notifications from system settings. TODO: reflect the
                        // real system permission state instead of local-only UI state.
                        notificationsEnabled = enabled
                        if (enabled && android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    appVersion = appVersion,
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    onOpenAssistantSettings = onOpenAssistantSettings,
                    onOpenVoiceSettings = { navController.navigate(ROUTE_SETTINGS_VOICE) },
                    onOpenProviderSettings = { navController.navigate(ROUTE_SETTINGS_PROVIDERS) },
                    onOpenLocalRuntime = { navController.navigate(LOCAL_RUNTIME_MANAGEMENT_ROUTE) },
                    onOpenRemoteConnection = { navController.navigate(ROUTE_REMOTE_CONNECTION) },
                    onOpenWorkspaces = { navController.navigate(ROUTE_WORKSPACES) },
                    onOpenDiagnostics = { showDiagnostics = true },
                    onOpenMcp = { navController.navigate(ROUTE_SETTINGS_MCP) },
                    onOpenServerInfo = { navController.navigate(ROUTE_SETTINGS_SERVER_INFO) },
                    onOpenUsage = { navController.navigate(ROUTE_USAGE) },
                    currentTheme = preferences.theme,
                    onThemeChange = { app.preferences.setTheme(it) },
                    uiFontSize = preferences.uiFontSize,
                    onUiFontSizeChange = { app.preferences.setUiFontSize(it) },
                    codeFontSize = preferences.codeFontSize,
                    onCodeFontSizeChange = { app.preferences.setCodeFontSize(it) },
                    syntaxTheme = preferences.syntaxTheme,
                    onSyntaxThemeChange = { app.preferences.setSyntaxTheme(it) },
                    toolCallDetailLevel = preferences.toolCallDetailLevel,
                    onToolCallDetailLevelChange = { app.preferences.setToolCallDetailLevel(it) },
                    autoExpandReasoning = preferences.autoExpandReasoning,
                    onAutoExpandReasoningChange = { app.preferences.setAutoExpandReasoning(it) },
                    sendBehavior = preferences.sendBehavior,
                    onSendBehaviorChange = { app.preferences.setSendBehavior(it) }
                )
            }

            composable(ROUTE_SETTINGS_VOICE) {
                VoiceSettingsScreen(
                    ttsEnabled = settingsState.ttsEnabled,
                    continuousConversation = settingsState.continuousConversation,
                    wakeWordEnabled = settingsState.wakeWordEnabled,
                    onTtsChange = settingsViewModel::setTtsEnabled,
                    onContinuousChange = settingsViewModel::setContinuousConversation,
                    onWakeWordChange = { enabled ->
                        settingsViewModel.setWakeWordEnabled(enabled)
                        if (enabled) {
                            if (hasMicrophonePermission()) {
                                com.opencode.android.feature.wakeword.WakeWordService.start(context)
                            } else {
                                startWakeWordAfterPermission = true
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            com.opencode.android.feature.wakeword.WakeWordService.stop(context)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SETTINGS_PROVIDERS) {
                ProviderSettingsScreen(
                    state = settingsState,
                    onOpenProviderAuth = settingsViewModel::openProviderAuth,
                    onSelectProviderAuthMethod = settingsViewModel::selectProviderAuthMethod,
                    onProviderAuthInput = settingsViewModel::updateProviderAuthInput,
                    onProviderApiKey = settingsViewModel::updateProviderApiKey,
                    onSubmitProviderAuth = settingsViewModel::submitProviderAuth,
                    onCompleteProviderOAuth = settingsViewModel::completeProviderOAuth,
                    onDisconnectProvider = settingsViewModel::disconnectProvider,
                    onLaunchOAuthBrowser = { url ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            )
                        }.onFailure { error ->
                            settingsViewModel.reportOAuthError(error.message.orEmpty())
                        }
                    },
                    onDismissProviderAuth = settingsViewModel::dismissProviderAuth,
                    onConnectGitHub = { settingsViewModel.beginGitHubDeviceFlow() },
                    onDisconnectGitHub = settingsViewModel::disconnectGitHub,
                    onOpenGitHubVerification = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }.onFailure { error -> settingsViewModel.reportOAuthError(error.message.orEmpty()) }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SETTINGS_MCP) {
                com.opencode.android.feature.settings.McpScreen(
                    registry = app.runtimeRegistry,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SETTINGS_SERVER_INFO) {
                com.opencode.android.feature.settings.ServerInfoScreen(
                    registry = app.runtimeRegistry,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_WORKSPACES) {
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
                    },
                    onImportFolder = { workspaceImportLauncher.launch(null) },
                    onCloneGithub = { showCloneDialog = true },
                    onRemoveProject = workspaceViewModel::removeProject,
                    onDeleteProjectFiles = workspaceViewModel::deleteProjectFiles,
                    onBack = { navController.popBackStack() }
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

            composable(ROUTE_ACTIVITY) {
                ActivityScreen(
                    state = activityState,
                    onRefresh = activityViewModel::refresh,
                    onInspectSession = { session ->
                        selectedSession = session
                        navController.navigate(SESSION_DETAIL_ROUTE)
                    },
                    onOpenSession = { id, title ->
                        app.activityRepository.markSessionRead(id)
                        pendingSession = id to title
                        navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
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
                            navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
                        }
                    )
                }
            }

            composable("$ROUTE_CODE_VIEWER?filePath={filePath}") { backStack ->
                val filePath = backStack.arguments?.getString("filePath").orEmpty()
                CodeViewerScreen(
                    fileName = filePath.substringAfterLast('/'),
                    content = "",
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_USAGE) {
                UsageScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (showCloneDialog) {
        GithubCloneDialog(
            githubConfigured = !app.settings.githubToken.isNullOrBlank(),
            onClone = { url ->
                val name = url.trim().removeSuffix("/").removeSuffix(".git").substringAfterLast('/')
                withContext(Dispatchers.IO) { app.gitCloneRepository.clone(url, name) }
            },
            onListRepos = { settingsViewModel.listGitHubRepos() },
            onCloned = { serverPath ->
                workspaceViewModel.addProject(serverPath)
                workspaceViewModel.refresh()
                chatViewModel.newSession()
                chatViewModel.selectWorkspace(serverPath)
            },
            onDismiss = { showCloneDialog = false }
        )
    }

    if (showCommandPalette) {
        CommandPaletteSheet(
            onDismiss = { showCommandPalette = false },
            onNavigate = { route ->
                showCommandPalette = false
                navController.navigate(route) { launchSingleTop = true }
            },
            onOpenSession = { id, title ->
                showCommandPalette = false
                app.activityRepository.markSessionRead(id)
                pendingSession = id to title
                navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
            },
            sessions = activityState.sessions.map { it.id to it.title.ifBlank { it.slug ?: it.id } }
        )
    }

    if (showSessionImport) {
        SessionImportSheet(
            onDismiss = { showSessionImport = false },
            onImport = { _ ->
                showSessionImport = false
                navController.navigate(ROUTE_CHAT)
            }
        )
    }

    if (showDiagnostics) {
        DiagnosticsSheet(
            onDismiss = { showDiagnostics = false },
            appVersion = "0.3.0",
            connectionStatus = "connected",
            runtimeStatus = "ready"
        )
    }
    }
}

private enum class CloneSource { REPOS, URL }

@Composable
private fun GithubCloneDialog(
    githubConfigured: Boolean,
    onClone: suspend (String) -> GitCloneResult,
    onListRepos: suspend () -> List<GitHubRepo>,
    onCloned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var source by remember { mutableStateOf(if (githubConfigured) CloneSource.REPOS else CloneSource.URL) }
    var url by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var isLoadingRepos by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var isCloning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(githubConfigured) {
        if (githubConfigured) {
            isLoadingRepos = true
            repos = onListRepos()
            isLoadingRepos = false
        }
    }

    fun startClone(cloneUrl: String) {
        isCloning = true
        error = null
        scope.launch {
            val result = onClone(cloneUrl)
            if (result.exitCode == 0) {
                onCloned(result.serverPath)
                onDismiss()
            } else {
                error = result.output.lineSequence().lastOrNull { it.isNotBlank() }
                    ?: "Clone failed (${result.exitCode})"
                isCloning = false
            }
        }
    }

    val filteredRepos = remember(repos, search) {
        if (search.isBlank()) repos else repos.filter {
            it.fullName.contains(search, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        title = { Text(stringResource(R.string.workspace_clone_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!githubConfigured) {
                    Text(
                        text = stringResource(R.string.workspace_clone_requires_auth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = source == CloneSource.REPOS,
                            onClick = { source = CloneSource.REPOS },
                            label = { Text(stringResource(R.string.workspace_clone_my_repos)) }
                        )
                        FilterChip(
                            selected = source == CloneSource.URL,
                            onClick = { source = CloneSource.URL },
                            label = { Text(stringResource(R.string.workspace_clone_url_tab)) }
                        )
                    }
                }

                if (source == CloneSource.REPOS && githubConfigured) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text(stringResource(R.string.workspace_clone_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isLoadingRepos) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.workspace_clone_loading_repos), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(filteredRepos, key = { it.fullName }) { repo ->
                                RepoRow(
                                    repo = repo,
                                    enabled = !isCloning,
                                    onClick = { startClone(repo.cloneUrl) }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            error = null
                        },
                        label = { Text(stringResource(R.string.workspace_clone_url_label)) },
                        placeholder = { Text(stringResource(R.string.workspace_clone_url_placeholder)) },
                        singleLine = true,
                        enabled = !isCloning,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (isCloning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.workspace_clone_running),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (source == CloneSource.URL || !githubConfigured) {
                Button(
                    enabled = url.isNotBlank() && !isCloning,
                    onClick = { startClone(url.trim()) }
                ) {
                    Text(stringResource(R.string.workspace_clone_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCloning) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RepoRow(
    repo: GitHubRepo,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                repo.fullName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
