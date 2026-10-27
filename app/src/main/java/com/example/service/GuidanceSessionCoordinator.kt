package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import com.example.domain.model.GuidanceStepInstruction
import com.example.domain.model.ScreenInspectionSnapshot
import com.example.domain.model.ScreenUiElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActiveGuidanceSession(
    val isActive: Boolean = false,
    val userGoal: String = "",
    val isOverlayMinimized: Boolean = false,
    val isFloatingHudVisible: Boolean = true,
    val currentStepNumber: Int = 1,
    val latestSnapshot: ScreenInspectionSnapshot? = null,
    val currentInstruction: GuidanceStepInstruction? = null,
    val completedStepsHistory: List<String> = emptyList(),
    val isAnalyzingScreen: Boolean = false,
    val statusBanner: String = "جاهز لبدء جلسة الإرشاد التفاعلي"
)

/**
 * Coordinates communication between KhotwaAccessibilityService, the Floating Overlay HUD,
 * and the Compose UI / GuidanceViewModel.
 */
object GuidanceSessionCoordinator {

    private val _isAccessibilityServiceConnected = MutableStateFlow(false)
    val isAccessibilityServiceConnected: StateFlow<Boolean> = _isAccessibilityServiceConnected.asStateFlow()

    private val _sessionState = MutableStateFlow(ActiveGuidanceSession())
    val sessionState: StateFlow<ActiveGuidanceSession> = _sessionState.asStateFlow()

    private var onRequestNextStepCallback: (() -> Unit)? = null

    fun setServiceConnectionState(connected: Boolean) {
        _isAccessibilityServiceConnected.value = connected
        if (!connected && _sessionState.value.isActive) {
            _sessionState.update {
                it.copy(
                    statusBanner = "تم إيقاف خدمة إمكانية الوصول من النظام؛ يعمل الإرشاد الآن في الوضع التفاعلي الآمن."
                )
            }
        }
    }

    fun registerNextStepHandler(handler: () -> Unit) {
        onRequestNextStepCallback = handler
    }

    fun requestNextStepFromOverlay() {
        onRequestNextStepCallback?.invoke()
    }

    fun startSession(goal: String, showFloatingHud: Boolean) {
        _sessionState.value = ActiveGuidanceSession(
            isActive = true,
            userGoal = goal.trim(),
            isOverlayMinimized = false,
            isFloatingHudVisible = showFloatingHud,
            currentStepNumber = 1,
            completedStepsHistory = emptyList(),
            isAnalyzingScreen = true,
            statusBanner = "جاري فحص عناصر الشاشة المتاحة وتحديد الخطوة الأولى..."
        )
    }

    fun updateScreenSnapshot(snapshot: ScreenInspectionSnapshot) {
        _sessionState.update { state ->
            state.copy(latestSnapshot = snapshot)
        }
    }

    fun setAnalyzing(analyzing: Boolean, status: String? = null) {
        _sessionState.update { state ->
            state.copy(
                isAnalyzingScreen = analyzing,
                statusBanner = status ?: state.statusBanner
            )
        }
    }

    fun updateStepInstruction(instruction: GuidanceStepInstruction) {
        _sessionState.update { state ->
            state.copy(
                currentStepNumber = instruction.stepIndex,
                currentInstruction = instruction,
                isAnalyzingScreen = false,
                statusBanner = if (instruction.isGoalCompleted) {
                    "اكتمل الهدف بنجاح!"
                } else {
                    "الخطوة ${instruction.stepIndex} جاهزة — بانتظار تنفيذك للمتابعة"
                }
            )
        }
    }

    fun markCurrentStepDoneAndAdvance() {
        _sessionState.update { state ->
            val currentTitle = state.currentInstruction?.instructionTitle ?: "الخطوة ${state.currentStepNumber}"
            state.copy(
                currentStepNumber = state.currentStepNumber + 1,
                completedStepsHistory = state.completedStepsHistory + currentTitle,
                isAnalyzingScreen = true,
                statusBanner = "جاري التحقق من الشاشة الجديدة لتحديد الخطوة ${state.currentStepNumber + 1}..."
            )
        }
    }

    fun toggleMinimizeOverlay() {
        _sessionState.update { state ->
            state.copy(isOverlayMinimized = !state.isOverlayMinimized)
        }
    }

    fun setFloatingHudVisibility(visible: Boolean) {
        _sessionState.update { state ->
            state.copy(isFloatingHudVisible = visible)
        }
    }

    fun stopSessionImmediately() {
        _sessionState.value = ActiveGuidanceSession(
            isActive = false,
            userGoal = "",
            currentInstruction = null,
            completedStepsHistory = emptyList(),
            isAnalyzingScreen = false,
            statusBanner = "تم إنهاء جلسة الإرشاد وإيقاف قراءة الشاشة فورًا."
        )
    }

    fun isAccessibilityEnabledInSystem(context: Context): Boolean {
        if (_isAccessibilityServiceConnected.value) return true
        return runCatching {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabledServices.contains(context.packageName, ignoreCase = true)
        }.getOrDefault(false)
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Provides realistic interactive app screen scenarios so users can test or simulate
     * step-by-step guidance even before granting system AccessibilityService permissions
     * or while inside Khotwa AI.
     */
    fun createPracticeSnapshotForGoal(goal: String, stepNumber: Int): ScreenInspectionSnapshot {
        val lower = goal.lowercase()
        return when {
            lower.contains("مصادقة") || lower.contains("أمان") || lower.contains("حساب") || lower.contains("كلمة المرور") -> {
                if (stepNumber == 1) {
                    ScreenInspectionSnapshot(
                        packageName = "com.android.settings",
                        appDisplayName = "إعدادات الهاتف (Settings)",
                        isProtectedOrPasswordScreen = false,
                        elements = listOf(
                            ScreenUiElement(0, "الشبكة والإنترنت", "ضبط Wi-Fi وشبكة الجوال", "network_settings", "TextView", true, false, Rect(40, 220, 1000, 340)),
                            ScreenUiElement(1, "الأمان والخصوصية", "قفل الشاشة، المصادقة الثنائية، وأذونات التطبيقات", "security_privacy_row", "LinearLayout", true, false, Rect(40, 360, 1000, 490)),
                            ScreenUiElement(2, "التطبيقات والإشعارات", "إدارة التطبيقات المثبتة", "apps_row", "LinearLayout", true, false, Rect(40, 510, 1000, 640)),
                            ScreenUiElement(3, "إمكانية الوصول", "قارئ الشاشة ومُرشد خُطوة AI", "accessibility_row", "LinearLayout", true, false, Rect(40, 660, 1000, 790))
                        )
                    )
                } else {
                    ScreenInspectionSnapshot(
                        packageName = "com.android.settings",
                        appDisplayName = "الأمان والخصوصية (Security)",
                        isProtectedOrPasswordScreen = false,
                        elements = listOf(
                            ScreenUiElement(0, "قفل الجهاز والبصمة", "PIN أو البصمة", "device_lock", "LinearLayout", true, false, Rect(40, 220, 1000, 340)),
                            ScreenUiElement(1, "حساب Google والتحقق بخطوتين", "تفعيل المصادقة الثنائية لحماية الحساب", "two_factor_auth_btn", "Button", true, false, Rect(40, 360, 1000, 490)),
                            ScreenUiElement(2, "مراجعة أذونات الخصوصية", "الكاميرا والموقع والميكروفون", "privacy_dashboard", "LinearLayout", true, false, Rect(40, 510, 1000, 640))
                        )
                    )
                }
            }
            else -> {
                ScreenInspectionSnapshot(
                    packageName = "com.android.settings",
                    appDisplayName = "النظام والتطبيقات",
                    isProtectedOrPasswordScreen = false,
                    elements = listOf(
                        ScreenUiElement(0, "البحث في الإعدادات", "ابحث عن أي ميزة أو إعداد", "search_action_bar", "EditText", true, true, Rect(40, 140, 1000, 250)),
                        ScreenUiElement(1, "التطبيقات (Apps)", "إدارة الأذونات والإشعارات والتخزين", "apps_settings", "LinearLayout", true, false, Rect(40, 280, 1000, 410)),
                        ScreenUiElement(2, "البطارية والأداء", "تحسين استهلاك الطاقة", "battery_settings", "LinearLayout", true, false, Rect(40, 430, 1000, 560)),
                        ScreenUiElement(3, "إمكانية الوصول (Accessibility)", "الخدمات المساعدة ومُرشد خُطوة AI", "accessibility_settings", "LinearLayout", true, false, Rect(40, 580, 1000, 710))
                    )
                )
            }
        }
    }
}
