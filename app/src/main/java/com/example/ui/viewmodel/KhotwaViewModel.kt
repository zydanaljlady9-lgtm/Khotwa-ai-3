package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppStartupTimer
import com.example.data.ai.AiStreamEvent
import com.example.data.ai.ConnectionDiagnosticResult
import com.example.data.ai.KhotwaAiEngine
import com.example.data.local.ConversationEntity
import com.example.data.local.JsonModelSerializer
import com.example.data.local.KhotwaDatabase
import com.example.data.local.KhotwaSettingsState
import com.example.data.local.MessageEntity
import com.example.data.local.SettingsDataStore
import com.example.data.search.MultiSourceWebSearchEngine
import com.example.data.search.SearchProvider
import com.example.domain.model.ActionStep
import com.example.domain.model.AiModelTier
import com.example.domain.model.AiProviderType
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.SearchCitation
import com.example.domain.model.SearchProviderType
import com.example.domain.model.ThemePreference
import com.example.domain.model.UserIntent
import com.example.service.ActiveGuidanceSession
import com.example.service.GuidanceSessionCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class KhotwaScreen {
    HOME,
    CHAT,
    GUIDANCE,
    HISTORY,
    SETTINGS,
    APK_GUIDE
}

data class LiveGenerationProgress(
    val isGenerating: Boolean = false,
    val stageTitle: String = "",
    val activeIntent: UserIntent? = null,
    val activeModelId: String = "",
    val searchStatusMessage: String? = null,
    val discoveredCitations: List<SearchCitation> = emptyList(),
    val streamingText: String = "",
    val liveTtftMs: Long = 0L
)

class KhotwaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = KhotwaDatabase.getInstance(application)
    private val dao = database.khotwaDao()
    private val settingsStore = SettingsDataStore(application)
    private val searchEngine: SearchProvider = MultiSourceWebSearchEngine(dao = dao)
    private val aiEngine = KhotwaAiEngine()

    private val _currentScreen = MutableStateFlow(KhotwaScreen.HOME)
    val currentScreen: StateFlow<KhotwaScreen> = _currentScreen.asStateFlow()

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    private val _forceSearchNextRequest = MutableStateFlow(false)
    val forceSearchNextRequest: StateFlow<Boolean> = _forceSearchNextRequest.asStateFlow()

    private val _liveProgress = MutableStateFlow(LiveGenerationProgress())
    val liveProgress: StateFlow<LiveGenerationProgress> = _liveProgress.asStateFlow()

    private val _diagnosticState = MutableStateFlow<ConnectionDiagnosticResult?>(null)
    val diagnosticState: StateFlow<ConnectionDiagnosticResult?> = _diagnosticState.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _lastUserPrompt = MutableStateFlow("")
    val lastUserPrompt: StateFlow<String> = _lastUserPrompt.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val settingsState: StateFlow<KhotwaSettingsState> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KhotwaSettingsState()
    )

    val conversations: StateFlow<List<ConversationEntity>> = dao.observeConversations().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessageUi>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId == null) {
                flowOf(emptyList())
            } else {
                dao.observeMessages(convId).map { entities ->
                    entities.map { entity -> mapEntityToUi(entity) }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val guidanceSession: StateFlow<ActiveGuidanceSession> = GuidanceSessionCoordinator.sessionState
    val isAccessibilityConnected: StateFlow<Boolean> = GuidanceSessionCoordinator.isAccessibilityServiceConnected

    private var activeGenerationJob: Job? = null
    private var activeGuidanceJob: Job? = null
    private var inFlightPromptSignature: String? = null

    init {
        AppStartupTimer.markFirstFrameRendered()
        GuidanceSessionCoordinator.registerNextStepHandler {
            advanceGuidanceToNextStep()
        }
    }

    fun navigateTo(screen: KhotwaScreen) {
        _currentScreen.value = screen
    }

    fun toggleForceSearchMode() {
        _forceSearchNextRequest.value = !_forceSearchNextRequest.value
    }

    fun setForceSearchMode(enabled: Boolean) {
        _forceSearchNextRequest.value = enabled
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showToastMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun startNewConversation() {
        stopCurrentGeneration()
        _activeConversationId.value = null
        _currentScreen.value = KhotwaScreen.CHAT
    }

    fun openConversation(conversationId: Long) {
        stopCurrentGeneration()
        _activeConversationId.value = conversationId
        _currentScreen.value = KhotwaScreen.CHAT
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            dao.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
            _snackbarMessage.value = "تم حذف المحادثة بنجاح"
        }
    }

    fun clearAllHistoryAndCache() {
        stopCurrentGeneration()
        viewModelScope.launch {
            dao.clearAllConversations()
            dao.clearSearchCache()
            aiEngine.clearSessionCache()
            settingsStore.resetUsageCounters()
            _activeConversationId.value = null
            _snackbarMessage.value = "تم مسح سجل المحادثات والذاكرة المؤقتة بالكامل"
        }
    }

    fun sendUserPrompt(rawPrompt: String, explicitForceSearch: Boolean? = null) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return

        val currentSettings = settingsState.value
        if (!currentSettings.externalAiConsentGranted) {
            _snackbarMessage.value = "يرجى تفعيل الموافقة على إرسال الاستعلام لخدمة الذكاء الاصطناعي من الإعدادات والخصوصية أولًا."
            _currentScreen.value = KhotwaScreen.SETTINGS
            return
        }

        if (currentSettings.totalRequestsUsed >= currentSettings.dailySoftLimit) {
            _snackbarMessage.value = "وصلت إلى حد الاستخدام اليومي المحدد (${currentSettings.dailySoftLimit} طلبًا). يمكنك زيادة الحد من الإعدادات."
            return
        }

        val forceSearch = explicitForceSearch ?: _forceSearchNextRequest.value
        val signature = "${prompt.lowercase()}:$forceSearch"
        if (_liveProgress.value.isGenerating && inFlightPromptSignature == signature) {
            _snackbarMessage.value = "جاري بالفعل معالجة هذا الطلب..."
            return
        }

        stopCurrentGeneration()
        inFlightPromptSignature = signature
        _lastUserPrompt.value = prompt
        _currentScreen.value = KhotwaScreen.CHAT

        activeGenerationJob = viewModelScope.launch {
            try {
                val initialIntent = aiEngine.analyzeUserIntent(prompt, forceSearch)
                val convId = ensureActiveConversation(prompt, initialIntent)
                val priorHistory = activeMessages.value

                // Insert user message into Room
                dao.insertMessage(
                    MessageEntity(
                        conversationId = convId,
                        isUser = true,
                        content = prompt,
                        intentName = initialIntent.name
                    )
                )

                _liveProgress.value = LiveGenerationProgress(
                    isGenerating = true,
                    stageTitle = "جاري تحليل النية (${initialIntent.arabicTitle})...",
                    activeIntent = initialIntent
                )

                val shouldSearch = (forceSearch || currentSettings.autoWebSearchEnabled) &&
                    searchEngine.shouldTriggerSearch(prompt, initialIntent, forceSearch)

                var citations: List<SearchCitation> = emptyList()
                var conflictSummary: String? = null
                var searchDurationMs = 0L

                if (shouldSearch) {
                    _liveProgress.value = _liveProgress.value.copy(
                        stageTitle = "جاري البحث عبر الإنترنت واستخراج المصادر...",
                        searchStatusMessage = "يتم الآن البحث في المصادر الموثوقة ومقارنة المعلومات..."
                    )
                    val searchReport = searchEngine.searchWeb(
                        query = prompt,
                        providerType = currentSettings.searchProvider,
                        useCache = currentSettings.safeCacheEnabled
                    )
                    citations = searchReport.citations
                    conflictSummary = searchReport.conflictSummary
                    searchDurationMs = searchReport.durationMs

                    _liveProgress.value = _liveProgress.value.copy(
                        stageTitle = "تم جمع ${citations.size} مصادر — جاري توليد الإجابة المنظمة...",
                        searchStatusMessage = searchReport.statusMessage,
                        discoveredCitations = citations
                    )
                }

                // Reset one-shot force search toggle if enabled
                if (_forceSearchNextRequest.value) {
                    _forceSearchNextRequest.value = false
                }

                aiEngine.streamStructuredResponse(
                    userPrompt = prompt,
                    conversationHistory = priorHistory,
                    citations = citations,
                    conflictSummary = conflictSummary,
                    searchDurationMs = searchDurationMs,
                    settings = currentSettings,
                    forcedIntent = if (forceSearch) UserIntent.WEB_SEARCH else initialIntent
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.IntentDetected -> {
                            _liveProgress.value = _liveProgress.value.copy(
                                activeIntent = event.intent,
                                activeModelId = event.selectedModel,
                                stageTitle = "النموذج النشط: ${event.selectedModel}"
                            )
                        }
                        is AiStreamEvent.SearchProgress -> {
                            _liveProgress.value = _liveProgress.value.copy(
                                searchStatusMessage = event.message
                            )
                        }
                        is AiStreamEvent.PartialText -> {
                            _liveProgress.value = _liveProgress.value.copy(
                                stageTitle = "يتدفق الرد الآن (أول جزء خلال ${event.ttftMs}ms)...",
                                streamingText = event.accumulatedText,
                                liveTtftMs = event.ttftMs
                            )
                        }
                        is AiStreamEvent.Completed -> {
                            val ans = event.structuredAnswer
                            dao.insertMessage(
                                MessageEntity(
                                    conversationId = convId,
                                    isUser = false,
                                    content = ans.directAnswer,
                                    intentName = ans.detectedIntent.name,
                                    factsJson = JsonModelSerializer.encodeStringList(ans.facts),
                                    inferencesJson = JsonModelSerializer.encodeStringList(ans.inferences),
                                    uncertaintiesJson = JsonModelSerializer.encodeStringList(ans.uncertainties),
                                    stepsJson = JsonModelSerializer.encodeSteps(ans.actionSteps),
                                    citationsJson = JsonModelSerializer.encodeCitations(ans.citations),
                                    conflictSummary = ans.searchConflictSummary,
                                    modelUsed = ans.telemetry.modelUsed,
                                    ttftMs = ans.telemetry.ttftMs,
                                    totalDurationMs = ans.telemetry.totalDurationMs,
                                    searchDurationMs = ans.telemetry.searchDurationMs,
                                    isError = false
                                )
                            )
                            val updatedMessagesCount = dao.getMessagesOnce(convId).size
                            dao.updateConversationMeta(
                                id = convId,
                                title = prompt.take(48),
                                intent = ans.detectedIntent.arabicTitle,
                                count = updatedMessagesCount
                            )
                            settingsStore.recordRequestMetrics(
                                ttftMs = ans.telemetry.ttftMs,
                                totalMs = ans.telemetry.totalDurationMs
                            )
                            _liveProgress.value = LiveGenerationProgress()
                        }
                        is AiStreamEvent.Failure -> {
                            dao.insertMessage(
                                MessageEntity(
                                    conversationId = convId,
                                    isUser = false,
                                    content = event.errorMessage,
                                    intentName = initialIntent.name,
                                    citationsJson = JsonModelSerializer.encodeCitations(citations),
                                    searchDurationMs = searchDurationMs,
                                    isError = true
                                )
                            )
                            _liveProgress.value = LiveGenerationProgress()
                        }
                    }
                }
            } catch (e: CancellationException) {
                _liveProgress.value = LiveGenerationProgress()
                throw e
            } catch (e: Exception) {
                _liveProgress.value = LiveGenerationProgress()
                _snackbarMessage.value = "حدث خطأ غير متوقع: ${e.localizedMessage ?: "يرجى المحاولة مجددًا"}"
            } finally {
                inFlightPromptSignature = null
            }
        }
    }

    fun stopCurrentGeneration() {
        val partial = _liveProgress.value.streamingText
        val activeConv = _activeConversationId.value
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        inFlightPromptSignature = null

        if (partial.isNotBlank() && activeConv != null) {
            viewModelScope.launch {
                dao.insertMessage(
                    MessageEntity(
                        conversationId = activeConv,
                        isUser = false,
                        content = "$partial\n\n[تم إيقاف التوليد بطلب المستخدم]",
                        intentName = (_liveProgress.value.activeIntent ?: UserIntent.QUICK_ANSWER).name,
                        citationsJson = JsonModelSerializer.encodeCitations(_liveProgress.value.discoveredCitations),
                        modelUsed = _liveProgress.value.activeModelId,
                        ttftMs = _liveProgress.value.liveTtftMs,
                        totalDurationMs = _liveProgress.value.liveTtftMs
                    )
                )
            }
        }
        _liveProgress.value = LiveGenerationProgress()
    }

    fun retryLastUserMessage(forceSearch: Boolean = false) {
        val lastPrompt = _lastUserPrompt.value.ifBlank {
            activeMessages.value.lastOrNull { it.isUser }?.rawText.orEmpty()
        }
        if (lastPrompt.isNotBlank()) {
            sendUserPrompt(lastPrompt, explicitForceSearch = forceSearch)
        }
    }

    fun toggleStepCompletion(message: ChatMessageUi, stepNumber: Int) {
        viewModelScope.launch {
            val updatedSteps = message.steps.map { step ->
                if (step.stepNumber == stepNumber) {
                    step.copy(isCompleted = !step.isCompleted)
                } else {
                    step
                }
            }
            dao.updateMessageSteps(
                messageId = message.id,
                stepsJson = JsonModelSerializer.encodeSteps(updatedSteps)
            )
        }
    }

    // --- In-App Guidance Flows ---

    fun startInAppGuidanceSession(goal: String) {
        val cleanGoal = goal.trim()
        if (cleanGoal.isBlank()) {
            _snackbarMessage.value = "يرجى كتابة الهدف الذي تريد من خُطوة AI إرشادك لتنفيذه."
            return
        }

        val currentSettings = settingsState.value
        if (!currentSettings.screenInspectionConsentGranted) {
            _snackbarMessage.value = "يرجى الموافقة أولًا على قراءة عناصر الشاشة لأغراض الإرشاد."
            return
        }

        activeGuidanceJob?.cancel()
        GuidanceSessionCoordinator.startSession(
            goal = cleanGoal,
            showFloatingHud = currentSettings.floatingOverlayEnabled
        )
        _currentScreen.value = KhotwaScreen.GUIDANCE
        computeCurrentGuidanceStep()
    }

    fun advanceGuidanceToNextStep() {
        val state = GuidanceSessionCoordinator.sessionState.value
        if (!state.isActive) return
        GuidanceSessionCoordinator.markCurrentStepDoneAndAdvance()
        computeCurrentGuidanceStep()
    }

    fun refreshGuidanceForCurrentScreen() {
        val state = GuidanceSessionCoordinator.sessionState.value
        if (!state.isActive) return
        GuidanceSessionCoordinator.setAnalyzing(true, "جاري إعادة فحص عناصر الشاشة الحالية...")
        computeCurrentGuidanceStep()
    }

    private fun computeCurrentGuidanceStep() {
        activeGuidanceJob?.cancel()
        activeGuidanceJob = viewModelScope.launch {
            val state = GuidanceSessionCoordinator.sessionState.value
            if (!state.isActive) return@launch

            val snapshot = state.latestSnapshot ?: GuidanceSessionCoordinator.createPracticeSnapshotForGoal(
                goal = state.userGoal,
                stepNumber = state.currentStepNumber
            )
            GuidanceSessionCoordinator.updateScreenSnapshot(snapshot)

            val instruction = aiEngine.generateNextGuidanceStep(
                userGoal = state.userGoal,
                currentStepNumber = state.currentStepNumber,
                snapshot = snapshot,
                previousStepsHistory = state.completedStepsHistory
            )
            GuidanceSessionCoordinator.updateStepInstruction(instruction)
        }
    }

    fun stopGuidanceImmediately() {
        activeGuidanceJob?.cancel()
        activeGuidanceJob = null
        GuidanceSessionCoordinator.stopSessionImmediately()
        _snackbarMessage.value = "تم إيقاف جلسة الإرشاد داخل التطبيقات فورًا."
    }

    // --- Settings & Diagnostics ---

    fun setAiProvider(provider: AiProviderType) {
        viewModelScope.launch { settingsStore.updateAiProvider(provider) }
    }

    fun setAiModelTier(tier: AiModelTier) {
        viewModelScope.launch { settingsStore.updateAiModelTier(tier) }
    }

    fun setCustomEndpoint(url: String) {
        viewModelScope.launch { settingsStore.updateCustomEndpoint(url) }
    }

    fun setSearchProvider(provider: SearchProviderType) {
        viewModelScope.launch { settingsStore.updateSearchProvider(provider) }
    }

    fun setAutoWebSearch(enabled: Boolean) {
        viewModelScope.launch { settingsStore.updateAutoWebSearch(enabled) }
    }

    fun setParallelSearch(enabled: Boolean) {
        viewModelScope.launch { settingsStore.updateParallelSearch(enabled) }
    }

    fun setSafeCache(enabled: Boolean) {
        viewModelScope.launch { settingsStore.updateSafeCache(enabled) }
    }

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateFloatingOverlay(enabled)
            GuidanceSessionCoordinator.setFloatingHudVisibility(enabled)
        }
    }

    fun setExternalAiConsent(granted: Boolean) {
        viewModelScope.launch { settingsStore.updateExternalAiConsent(granted) }
    }

    fun setScreenInspectionConsent(granted: Boolean) {
        viewModelScope.launch { settingsStore.updateScreenInspectionConsent(granted) }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { settingsStore.updateThemePreference(theme) }
    }

    fun setDailySoftLimit(limit: Int) {
        viewModelScope.launch { settingsStore.updateDailySoftLimit(limit) }
    }

    fun runConnectionDiagnostic() {
        if (_isTestingConnection.value) return
        viewModelScope.launch {
            _isTestingConnection.value = true
            try {
                val result = aiEngine.testConnection(settingsState.value)
                _diagnosticState.value = result
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    private suspend fun ensureActiveConversation(prompt: String, intent: UserIntent): Long {
        val currentId = _activeConversationId.value
        if (currentId != null) return currentId

        val newId = dao.insertConversation(
            ConversationEntity(
                title = prompt.take(48),
                primaryIntent = intent.arabicTitle,
                messageCount = 1
            )
        )
        _activeConversationId.value = newId
        return newId
    }

    private fun mapEntityToUi(entity: MessageEntity): ChatMessageUi {
        val intent = runCatching { UserIntent.valueOf(entity.intentName) }
            .getOrDefault(UserIntent.QUICK_ANSWER)
        return ChatMessageUi(
            id = entity.id,
            conversationId = entity.conversationId,
            isUser = entity.isUser,
            rawText = entity.content,
            intent = intent,
            facts = JsonModelSerializer.decodeStringList(entity.factsJson),
            inferences = JsonModelSerializer.decodeStringList(entity.inferencesJson),
            uncertainties = JsonModelSerializer.decodeStringList(entity.uncertaintiesJson),
            steps = JsonModelSerializer.decodeSteps(entity.stepsJson),
            citations = JsonModelSerializer.decodeCitations(entity.citationsJson),
            conflictSummary = entity.conflictSummary,
            modelUsed = entity.modelUsed,
            ttftMs = entity.ttftMs,
            totalDurationMs = entity.totalDurationMs,
            searchDurationMs = entity.searchDurationMs,
            isStreaming = false,
            isError = entity.isError,
            timestamp = entity.timestamp
        )
    }
}
