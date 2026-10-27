package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppStartupTimer
import com.example.core.configureAppCheck
import com.example.domain.model.ThemePreference
import com.example.ui.screens.ApkBuildAndInstallGuideScreen
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.GuidanceCenterScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.SettingsAndPrivacyScreen
import com.example.ui.theme.KhotwaAITheme
import com.example.ui.viewmodel.KhotwaScreen
import com.example.ui.viewmodel.KhotwaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            KhotwaAppRoot()
        }
    }
}

@Composable
fun KhotwaAppRoot(
    viewModel: KhotwaViewModel = viewModel()
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val liveProgress by viewModel.liveProgress.collectAsStateWithLifecycle()
    val forceSearch by viewModel.forceSearchNextRequest.collectAsStateWithLifecycle()
    val guidanceSession by viewModel.guidanceSession.collectAsStateWithLifecycle()
    val isA11yConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()
    val diagnosticResult by viewModel.diagnosticState.collectAsStateWithLifecycle()
    val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var pendingGuidanceGoal by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        AppStartupTimer.markFirstFrameRendered()
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    val isDark = when (settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    KhotwaAITheme(darkTheme = isDark, forceRtl = true) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentScreen == KhotwaScreen.HOME,
                        onClick = { viewModel.navigateTo(KhotwaScreen.HOME) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == KhotwaScreen.HOME) {
                                    Icons.Filled.Home
                                } else {
                                    Icons.Outlined.Home
                                },
                                contentDescription = "الرئيسية"
                            )
                        },
                        label = { Text("الرئيسية") },
                        modifier = Modifier.testTag("nav_home")
                    )

                    NavigationBarItem(
                        selected = currentScreen == KhotwaScreen.CHAT,
                        onClick = { viewModel.navigateTo(KhotwaScreen.CHAT) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == KhotwaScreen.CHAT) {
                                    Icons.Filled.Chat
                                } else {
                                    Icons.Outlined.Chat
                                },
                                contentDescription = "المحادثة"
                            )
                        },
                        label = { Text("المحادثة") },
                        modifier = Modifier.testTag("nav_chat")
                    )

                    NavigationBarItem(
                        selected = currentScreen == KhotwaScreen.GUIDANCE,
                        onClick = { viewModel.navigateTo(KhotwaScreen.GUIDANCE) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == KhotwaScreen.GUIDANCE) {
                                    Icons.Filled.CompassCalibration
                                } else {
                                    Icons.Outlined.CompassCalibration
                                },
                                contentDescription = "الإرشاد"
                            )
                        },
                        label = { Text("الإرشاد") },
                        modifier = Modifier.testTag("nav_guidance")
                    )

                    NavigationBarItem(
                        selected = currentScreen == KhotwaScreen.HISTORY,
                        onClick = { viewModel.navigateTo(KhotwaScreen.HISTORY) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == KhotwaScreen.HISTORY) {
                                    Icons.Filled.History
                                } else {
                                    Icons.Outlined.History
                                },
                                contentDescription = "السجل"
                            )
                        },
                        label = { Text("السجل") },
                        modifier = Modifier.testTag("nav_history")
                    )

                    NavigationBarItem(
                        selected = currentScreen == KhotwaScreen.SETTINGS,
                        onClick = { viewModel.navigateTo(KhotwaScreen.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == KhotwaScreen.SETTINGS) {
                                    Icons.Filled.Settings
                                } else {
                                    Icons.Outlined.Settings
                                },
                                contentDescription = "الإعدادات"
                            )
                        },
                        label = { Text("الإعدادات") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        ) { innerPadding ->
            when (currentScreen) {
                KhotwaScreen.HOME -> {
                    HomeHubScreen(
                        settings = settings,
                        recentConversations = conversations,
                        forceSearchActive = forceSearch,
                        onToggleForceSearch = { viewModel.toggleForceSearchMode() },
                        onSendPrompt = { prompt, searchFlag ->
                            viewModel.sendUserPrompt(prompt, explicitForceSearch = searchFlag)
                        },
                        onOpenGuidanceCenter = { viewModel.navigateTo(KhotwaScreen.GUIDANCE) },
                        onOpenHistory = { viewModel.navigateTo(KhotwaScreen.HISTORY) },
                        onOpenSettings = { viewModel.navigateTo(KhotwaScreen.SETTINGS) },
                        onOpenApkGuide = { viewModel.navigateTo(KhotwaScreen.APK_GUIDE) },
                        onSelectConversation = { id -> viewModel.openConversation(id) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                KhotwaScreen.CHAT -> {
                    ChatWorkspaceScreen(
                        messages = activeMessages,
                        liveProgress = liveProgress,
                        forceSearchActive = forceSearch,
                        onToggleForceSearch = { viewModel.toggleForceSearchMode() },
                        onSendPrompt = { prompt, searchFlag ->
                            viewModel.sendUserPrompt(prompt, explicitForceSearch = searchFlag)
                        },
                        onStopGeneration = { viewModel.stopCurrentGeneration() },
                        onRetryLast = { searchFlag -> viewModel.retryLastUserMessage(searchFlag) },
                        onToggleStepCheck = { msg, stepNum ->
                            viewModel.toggleStepCompletion(msg, stepNum)
                        },
                        onLaunchGuidanceForGoal = { goal ->
                            pendingGuidanceGoal = goal
                            viewModel.setScreenInspectionConsent(true)
                            viewModel.startInAppGuidanceSession(goal)
                        },
                        onNewChat = { viewModel.startNewConversation() },
                        onNavigateBack = { viewModel.navigateTo(KhotwaScreen.HOME) },
                        onCopiedFeedback = { viewModel.showToastMessage("تم نسخ الإجابة إلى الحافظة") },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                KhotwaScreen.GUIDANCE -> {
                    GuidanceCenterScreen(
                        settings = settings,
                        sessionState = guidanceSession,
                        isServiceConnected = isA11yConnected,
                        initialGoal = pendingGuidanceGoal,
                        onToggleScreenConsent = { viewModel.setScreenInspectionConsent(it) },
                        onToggleFloatingHud = { viewModel.setFloatingOverlayEnabled(it) },
                        onStartGuidance = { goal -> viewModel.startInAppGuidanceSession(goal) },
                        onNextStep = { viewModel.advanceGuidanceToNextStep() },
                        onRefreshScreen = { viewModel.refreshGuidanceForCurrentScreen() },
                        onStopGuidanceNow = { viewModel.stopGuidanceImmediately() },
                        onNavigateBack = { viewModel.navigateTo(KhotwaScreen.HOME) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                KhotwaScreen.HISTORY -> {
                    HistoryScreen(
                        conversations = conversations,
                        onSelectConversation = { id -> viewModel.openConversation(id) },
                        onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                        onClearAll = { viewModel.clearAllHistoryAndCache() },
                        onNavigateBack = { viewModel.navigateTo(KhotwaScreen.HOME) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                KhotwaScreen.SETTINGS -> {
                    SettingsAndPrivacyScreen(
                        settings = settings,
                        diagnosticResult = diagnosticResult,
                        isTestingConnection = isTestingConnection,
                        isGuidanceActive = guidanceSession.isActive,
                        onSelectAiProvider = { viewModel.setAiProvider(it) },
                        onSelectModelTier = { viewModel.setAiModelTier(it) },
                        onUpdateCustomEndpoint = { viewModel.setCustomEndpoint(it) },
                        onSelectSearchProvider = { viewModel.setSearchProvider(it) },
                        onToggleAutoSearch = { viewModel.setAutoWebSearch(it) },
                        onToggleParallelSearch = { viewModel.setParallelSearch(it) },
                        onToggleSafeCache = { viewModel.setSafeCache(it) },
                        onToggleExternalConsent = { viewModel.setExternalAiConsent(it) },
                        onToggleScreenConsent = { viewModel.setScreenInspectionConsent(it) },
                        onSelectTheme = { viewModel.setThemePreference(it) },
                        onUpdateDailyLimit = { viewModel.setDailySoftLimit(it) },
                        onRunDiagnostic = { viewModel.runConnectionDiagnostic() },
                        onClearHistoryAndCache = { viewModel.clearAllHistoryAndCache() },
                        onStopGuidanceNow = { viewModel.stopGuidanceImmediately() },
                        onNavigateBack = { viewModel.navigateTo(KhotwaScreen.HOME) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                KhotwaScreen.APK_GUIDE -> {
                    ApkBuildAndInstallGuideScreen(
                        onNavigateBack = { viewModel.navigateTo(KhotwaScreen.HOME) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
