package com.example.domain.model

import android.graphics.Rect

enum class UserIntent(
    val arabicTitle: String,
    val description: String,
    val defaultNeedsSearch: Boolean
) {
    QUICK_ANSWER(
        arabicTitle = "إجابة سريعة ومباشرة",
        description = "تفسير سريع أو رد مباشر على سؤال واضح",
        defaultNeedsSearch = false
    ),
    WEB_SEARCH(
        arabicTitle = "بحث موثق بالمصادر",
        description = "جمع معلومات حديثة من الإنترنت مع مقارنة المصادر والروابط",
        defaultNeedsSearch = true
    ),
    DEEP_ANALYSIS(
        arabicTitle = "تحليل ومقارنة واستدلال",
        description = "تحليل مشكلة معقدة ومقارنة الخيارات واقتراح الحل الأفضل",
        defaultNeedsSearch = false
    ),
    TASK_PLANNING(
        arabicTitle = "تخطيط خطوة بخطوة",
        description = "تقسيم المهمة إلى خطوات عملية متسلسلة قابلة للتنفيذ",
        defaultNeedsSearch = false
    ),
    APP_GUIDANCE(
        arabicTitle = "إرشاد داخل تطبيقات الهاتف",
        description = "توجيه تفاعلي لاستخدام تطبيق أو ضبط إعدادات النظام خطوة بخطوة",
        defaultNeedsSearch = false
    )
}

enum class AiProviderType(val arabicName: String, val subtitle: String) {
    FIREBASE_GEMINI(
        arabicName = "Google Gemini (Firebase AI Logic)",
        subtitle = "موثق عبر App Check بدون الحاجة لتضمين مفتاح سري في APK"
    ),
    CUSTOM_ENDPOINT(
        arabicName = "نقطة نهاية REST مخصصة (Proxy / متوافق مع OpenAI)",
        subtitle = "الاتصال بخادم وسيط آمن أو نقطة نهاية مخصصة"
    )
}

enum class AiModelTier(
    val modelId: String,
    val arabicName: String,
    val badge: String,
    val description: String
) {
    AUTO(
        modelId = "auto",
        arabicName = "توجيه ذكي تلقائي (موصى به)",
        badge = "ذكي",
        description = "يختار النموذج السريع للطلبات البسيطة والنموذج الأقوى للتحليل المعقد تلقائيًا"
    ),
    FAST(
        modelId = "gemini-3.5-flash-lite",
        arabicName = "Gemini 3.5 Flash-Lite (فائق السرعة)",
        badge = "سريع جدًا",
        description = "أقل زمن استجابة (TTFT) للأسئلة المباشرة والإرشاد السريع"
    ),
    BALANCED(
        modelId = "gemini-3.8-flash",
        arabicName = "Gemini 3.8 Flash (متوازن وشامل)",
        badge = "افتراضي",
        description = "أداء متقدم للمحادثة العربية، تلخيص نتائج البحث، وتخطيط المهام"
    ),
    REASONING(
        modelId = "gemini-4-argon",
        arabicName = "Gemini 4 Argon (استدلال عميق)",
        badge = "تحليل متقدم",
        description = "للمسائل المعقدة والمقارنات متعددة الأبعاد (يتطلب خطة Blaze أو يتحول تلقائيًا لـ 3.8 Flash)"
    )
}

enum class SearchProviderType(val arabicName: String, val description: String) {
    MULTI_SOURCE_HYBRID(
        arabicName = "محرك البحث الهجين متعدد المصادر",
        description = "يجمع ويقارن النتائج من ويكيبيديا (العربية والإنجليزية) وDuckDuckGo وAlgolia ومستخرج الصفحات"
    ),
    WIKIPEDIA_DIRECT(
        arabicName = "الموسوعة الحرة (عربي + إنجليزي)",
        description = "بحث موسوعي موثق مع تواريخ آخر تحديث والروابط المباشرة"
    ),
    TECH_AND_NEWS_SEARCH(
        arabicName = "البحث التقني والأخبار الحية",
        description = "يركز على المصادر التقنية والأخبار الحديثة مع استخراج محتوى الصفحات"
    )
}

enum class ThemePreference(val arabicLabel: String) {
    DARK("الوضع الداكن"),
    LIGHT("الوضع الفاتح"),
    SYSTEM("حسب النظام")
}

data class SearchCitation(
    val id: String,
    val title: String,
    val url: String,
    val sourceName: String,
    val snippet: String,
    val publishedDate: String?,
    val extractedContentPreview: String? = null,
    val hasConflict: Boolean = false,
    val conflictExplanation: String? = null
)

data class SearchExecutionReport(
    val query: String,
    val executedQueries: List<String>,
    val citations: List<SearchCitation>,
    val conflictSummary: String?,
    val durationMs: Long,
    val fromCache: Boolean = false,
    val statusMessage: String,
    val isSuccessful: Boolean = true
)

data class ActionStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val targetUiElementHint: String? = null,
    val isCompleted: Boolean = false
)

data class StructuredAnswer(
    val directAnswer: String,
    val detectedIntent: UserIntent,
    val facts: List<String>,
    val inferences: List<String>,
    val uncertainties: List<String>,
    val actionSteps: List<ActionStep>,
    val citations: List<SearchCitation>,
    val searchConflictSummary: String? = null,
    val telemetry: ResponseTelemetry
)

data class ResponseTelemetry(
    val modelUsed: String,
    val intentAnalysisMs: Long,
    val searchDurationMs: Long,
    val ttftMs: Long,
    val totalDurationMs: Long,
    val fromCache: Boolean = false
)

data class ChatMessageUi(
    val id: Long = 0L,
    val conversationId: Long,
    val isUser: Boolean,
    val rawText: String,
    val intent: UserIntent = UserIntent.QUICK_ANSWER,
    val facts: List<String> = emptyList(),
    val inferences: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList(),
    val steps: List<ActionStep> = emptyList(),
    val citations: List<SearchCitation> = emptyList(),
    val conflictSummary: String? = null,
    val modelUsed: String = "",
    val ttftMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val searchDurationMs: Long = 0L,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ScreenUiElement(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val viewIdResourceName: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val boundsInScreen: Rect
) {
    val displayLabel: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            viewIdResourceName.isNotBlank() -> viewIdResourceName.substringAfterLast("/")
            else -> className.substringAfterLast(".")
        }
}

data class ScreenInspectionSnapshot(
    val packageName: String,
    val appDisplayName: String,
    val isProtectedOrPasswordScreen: Boolean,
    val elements: List<ScreenUiElement>,
    val capturedAtMs: Long = System.currentTimeMillis()
)

data class GuidanceStepInstruction(
    val stepIndex: Int,
    val totalEstimatedSteps: Int,
    val instructionTitle: String,
    val simpleExplanation: String,
    val matchedElement: ScreenUiElement?,
    val safetyWarning: String? = null,
    val requiresUserConfirmation: Boolean = true,
    val isGoalCompleted: Boolean = false
)
