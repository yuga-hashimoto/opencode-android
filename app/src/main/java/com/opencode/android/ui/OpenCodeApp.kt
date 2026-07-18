package com.opencode.android.ui

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opencode.android.R
import com.opencode.android.backend.OpenCodeBackend
import com.opencode.android.hotword.HotwordService
import com.opencode.android.speech.SpeechRecognizerManager
import com.opencode.android.speech.SpeechResult
import com.opencode.android.ui.chat.ChatViewModel
import com.opencode.android.ui.chat.OpenCodeChatScreen
import com.opencode.android.ui.connections.ConnectionsScreen
import com.opencode.android.ui.home.HomeScreen
import com.opencode.android.ui.sessions.SessionsScreen
import com.opencode.android.ui.settings.SettingsScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Default.Home),
    CHAT("chat", R.string.nav_chat, Icons.AutoMirrored.Filled.Chat),
    CONNECTIONS("connections", R.string.nav_connections, Icons.Default.Link),
    SESSIONS("sessions", R.string.nav_sessions, Icons.AutoMirrored.Filled.ViewList),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun OpenCodeApp(
    appViewModel: AppViewModel,
    onOpenAssistantSettings: () -> Unit,
    onHotwordChanged: (Boolean) -> Unit
) {
    val appState by appViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    var pendingSession by remember { mutableStateOf<Pair<String, String>?>(null) }

    val backend = appState.backend
    val chatViewModel: ChatViewModel = viewModel(
        key = "chat-${backend?.id ?: "none"}",
        factory = ChatViewModelFactory(backend)
    )
    val chatState by chatViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val speechManager = remember { SpeechRecognizerManager(context.applicationContext) }
    val voiceScope = rememberCoroutineScope()
    var voiceJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(speechManager) {
        onDispose {
            voiceJob?.cancel()
            speechManager.destroy()
        }
    }

    val requestVoiceInput: () -> Unit = {
        if (voiceJob?.isActive == true) {
            voiceJob?.cancel()
            chatViewModel.stopListening()
            context.sendBroadcast(
                Intent(HotwordService.ACTION_RESUME_HOTWORD).setPackage(context.packageName)
            )
        } else {
            context.sendBroadcast(
                Intent(HotwordService.ACTION_PAUSE_HOTWORD).setPackage(context.packageName)
            )
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
                    context.sendBroadcast(
                        Intent(HotwordService.ACTION_RESUME_HOTWORD).setPackage(context.packageName)
                    )
                }
            }
        }
    }

    LaunchedEffect(
        appState.selectedProviderId,
        appState.selectedModelId,
        appState.selectedAgentId
    ) {
        chatViewModel.selectConfiguration(
            appState.selectedProviderId,
            appState.selectedModelId,
            appState.selectedAgentId
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
                        icon = {
                            Icon(destination.icon, contentDescription = stringResource(destination.labelRes))
                        },
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
                    state = appState,
                    onNewChat = {
                        pendingSession = null
                        chatViewModel.newSession()
                        navController.navigate(Destination.CHAT.route)
                    },
                    onOpenConnections = { navController.navigate(Destination.CONNECTIONS.route) },
                    onOpenSessions = { navController.navigate(Destination.SESSIONS.route) },
                    onOpenSession = { id, title ->
                        pendingSession = id to title
                        navController.navigate(Destination.CHAT.route)
                    },
                    onRefresh = appViewModel::refresh
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
                    providers = appState.providers,
                    agents = appState.agents,
                    selectedProviderId = appState.selectedProviderId,
                    selectedModelId = appState.selectedModelId,
                    selectedAgentId = appState.selectedAgentId,
                    onSelectModel = appViewModel::selectModel,
                    onSelectAgent = appViewModel::selectAgent,
                    onSendMessage = chatViewModel::sendMessage,
                    onPermission = chatViewModel::respondToPermission,
                    onAbort = chatViewModel::abort,
                    onMic = requestVoiceInput
                )
            }
            composable(Destination.CONNECTIONS.route) {
                ConnectionsScreen(
                    connections = appState.connections,
                    selectedConnectionId = appState.selectedConnectionId,
                    onSelect = appViewModel::selectConnection,
                    onSave = appViewModel::saveConnection,
                    onDelete = appViewModel::deleteConnection,
                    onTest = appViewModel::testConnection
                )
            }
            composable(Destination.SESSIONS.route) {
                SessionsScreen(
                    sessions = appState.sessions,
                    isRefreshing = appState.isRefreshing,
                    onRefresh = appViewModel::refresh,
                    onOpenSession = { id, title ->
                        pendingSession = id to title
                        navController.navigate(Destination.CHAT.route)
                    }
                )
            }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    state = appState,
                    onOpenAssistantSettings = onOpenAssistantSettings,
                    onHotwordChange = { enabled ->
                        appViewModel.setHotwordEnabled(enabled)
                        onHotwordChanged(enabled)
                    },
                    onWakeWordChange = appViewModel::setWakeWord,
                    onTtsChange = appViewModel::setTtsEnabled,
                    onContinuousChange = appViewModel::setContinuousConversation
                )
            }
        }
    }
}

private class ChatViewModelFactory(
    private val backend: OpenCodeBackend?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChatViewModel::class.java))
        return ChatViewModel(backend) as T
    }
}
