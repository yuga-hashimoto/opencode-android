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
import com.opencode.android.feature.activity.ActivityScreen
import com.opencode.android.feature.activity.ActivityViewModel
import com.opencode.android.feature.assistant.SpeechRecognizerManager
import com.opencode.android.feature.assistant.SpeechResult
import com.opencode.android.feature.chat.ChatViewModel
import com.opencode.android.feature.chat.OpenCodeChatScreen
import com.opencode.android.feature.home.HomeScreen
import com.opencode.android.feature.home.HomeViewModel
import com.opencode.android.feature.settings.SettingsScreen
import com.opencode.android.feature.settings.SettingsViewModel
import com.opencode.android.feature.workspace.WorkspaceViewModel
import com.opencode.android.feature.workspace.WorkspacesScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

@Composable
fun OpenCodeApp(
    onOpenAssistantSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OpenCodeApplication
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    var pendingSession by remember { mutableStateOf<Pair<String, String>?>(null) }

    val selectedRuntime by app.runtimeRegistry.selected.collectAsState()
    val preferences by app.preferences.state.collectAsState()
    val homeViewModel: HomeViewModel = viewModel(
        key = "home",
        factory = ViewModelFactory { HomeViewModel(app.catalogRepository, app.preferences) }
    )
    val homeState by homeViewModel.state.collectAsState()

    val workspaceViewModel: WorkspaceViewModel = viewModel(
        key = "workspaces",
        factory = ViewModelFactory {
            WorkspaceViewModel(app.runtimeRegistry, app.catalogRepository, app.localRuntimeManager)
        }
    )
    val workspaceState by workspaceViewModel.state.collectAsState()

    val activityViewModel: ActivityViewModel = viewModel(
        key = "activity",
        factory = ViewModelFactory {
            ActivityViewModel(app.catalogRepository, app.activityRepository)
        }
    )
    val activityState by activityViewModel.state.collectAsState()

    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings",
        factory = ViewModelFactory {
            SettingsViewModel(app.catalogRepository, app.preferences)
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
                speechManager.startListening("ja-JP").collect { result ->
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

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                OpenCodeChatScreen(
                    state = chatState,
                    providers = settingsState.providers,
                    agents = settingsState.agents,
                    selectedProviderId = settingsState.providerId,
                    selectedModelId = settingsState.modelId,
                    selectedAgentId = settingsState.agentId,
                    onSelectModel = settingsViewModel::selectModel,
                    onSelectAgent = settingsViewModel::selectAgent,
                    onSendMessage = chatViewModel::sendMessage,
                    onPermission = chatViewModel::respondToPermission,
                    onAbort = chatViewModel::abort,
                    onMic = requestVoiceInput
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
                    onSetupLocal = { }
                )
            }

            composable(Destination.ACTIVITY.route) {
                ActivityScreen(
                    state = activityState,
                    onRefresh = activityViewModel::refresh,
                    onOpenSession = { id, title ->
                        pendingSession = id to title
                        navController.navigate(Destination.CHAT.route)
                    }
                )
            }

            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    state = settingsState,
                    onOpenAssistantSettings = onOpenAssistantSettings,
                    onTtsChange = settingsViewModel::setTtsEnabled,
                    onContinuousChange = settingsViewModel::setContinuousConversation
                )
            }
        }
    }
}
