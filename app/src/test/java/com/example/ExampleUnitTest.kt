package com.example

import com.example.data.ai.KhotwaAiEngine
import com.example.data.local.JsonModelSerializer
import com.example.data.search.MultiSourceWebSearchEngine
import com.example.domain.model.ActionStep
import com.example.domain.model.AiModelTier
import com.example.domain.model.ResponseTelemetry
import com.example.domain.model.SearchCitation
import com.example.domain.model.UserIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    private val aiEngine = KhotwaAiEngine()
    private val searchEngine = MultiSourceWebSearchEngine()

    @Test
    fun `intent analyzer classifies Arabic and dialect prompts accurately`() {
        assertEquals(
            UserIntent.WEB_SEARCH,
            aiEngine.analyzeUserIntent("ابحث لي عن أحدث تطورات الطاقة المتجددة مع المصادر")
        )
        assertEquals(
            UserIntent.APP_GUIDANCE,
            aiEngine.analyzeUserIntent("ساعدني على استخدام تطبيق الإعدادات لتفعيل البلوتوث")
        )
        assertEquals(
            UserIntent.DEEP_ANALYSIS,
            aiEngine.analyzeUserIntent("حلل هذه المشكلة وقارن الخيارات المتاحة واقترح أفضل حل")
        )
        assertEquals(
            UserIntent.TASK_PLANNING,
            aiEngine.analyzeUserIntent("ابغى خطة عملية لتعلم تطوير تطبيقات أندرويد في شهرين")
        )
        assertEquals(
            UserIntent.QUICK_ANSWER,
            aiEngine.analyzeUserIntent("ما معنى كلمة خوارزمية؟")
        )
    }

    @Test
    fun `adaptive model router selects fast model for simple queries and balanced for complex`() {
        val fastModel = aiEngine.selectOptimalModelId(
            tier = AiModelTier.AUTO,
            intent = UserIntent.QUICK_ANSWER,
            promptLength = 24
        )
        assertEquals("gemini-3.5-flash-lite", fastModel)

        val analysisModel = aiEngine.selectOptimalModelId(
            tier = AiModelTier.AUTO,
            intent = UserIntent.DEEP_ANALYSIS,
            promptLength = 120
        )
        assertEquals("gemini-3.8-flash", analysisModel)

        val explicitReasoning = aiEngine.selectOptimalModelId(
            tier = AiModelTier.REASONING,
            intent = UserIntent.DEEP_ANALYSIS,
            promptLength = 80
        )
        assertEquals("gemini-4-argon", explicitReasoning)
    }

    @Test
    fun `structured parser separates facts, inferences, uncertainties, and steps`() {
        val sampleResponse = """
            ### الخلاصة المباشرة
            يُعد التحقق بخطوتين وسيلة أساسية لحماية الحسابات الرقمية [1].
            ### الحقائق المؤكدة
            - يقلل التحقق بخطوتين من خطر اختراق الحسابات بنسبة عالية.
            - يدعم نظام أندرويد مفاتيح المرور (Passkeys) رسميًا.
            ### الاستنتاجات التحليلية
            - استخدام تطبيق مصادقة أكثر أمانًا من الرسائل النصية القصيرة.
            ### الشكوك والحدود المعرفية
            - قد تختلف خطوات الإعداد قليلًا حسب واجهة الشركة المصنعة للهاتف.
            ### الخطوات العملية القابلة للتنفيذ
            1. فتح إعدادات الحساب: انتقل إلى قسم الأمان في إعدادات الهاتف.
            2. تفعيل التحقق بخطوتين: اختر تطبيق المصادقة واحفظ الرموز الاحتياطية.
        """.trimIndent()

        val parsed = aiEngine.parseStructuredSections(
            rawText = sampleResponse,
            intent = UserIntent.TASK_PLANNING,
            citations = listOf(
                SearchCitation(
                    id = "1",
                    title = "أمن الحسابات",
                    url = "https://ar.wikipedia.org/wiki/استيثاق_متعدد_العوامل",
                    sourceName = "ويكيبيديا العربية",
                    snippet = "الاستيثاق متعدد العوامل يحمي الحسابات.",
                    publishedDate = "آخر تحديث: 2026-01-10"
                )
            ),
            conflictSummary = null,
            telemetry = ResponseTelemetry(
                modelUsed = "gemini-3.8-flash",
                intentAnalysisMs = 2L,
                searchDurationMs = 150L,
                ttftMs = 420L,
                totalDurationMs = 980L
            )
        )

        assertTrue(parsed.directAnswer.contains("التحقق بخطوتين"))
        assertEquals(2, parsed.facts.size)
        assertEquals(1, parsed.inferences.size)
        assertEquals(1, parsed.uncertainties.size)
        assertEquals(2, parsed.actionSteps.size)
        assertEquals("فتح إعدادات الحساب", parsed.actionSteps[0].title)
    }

    @Test
    fun `search layer detects search need and compares sources for conflicts`() {
        assertTrue(
            searchEngine.shouldTriggerSearch(
                query = "اشرح لي هذه المعلومة مع المصادر",
                intent = UserIntent.QUICK_ANSWER,
                forceSearch = false
            )
        )
        assertFalse(
            searchEngine.shouldTriggerSearch(
                query = "مرحبا",
                intent = UserIntent.QUICK_ANSWER,
                forceSearch = false
            )
        )

        val citations = listOf(
            SearchCitation("1", "مصدر 1", "https://example.com/1", "SourceA", "صدر في 2022 و2023", "2023"),
            SearchCitation("2", "مصدر 2", "https://example.com/2", "SourceB", "تحديث 2025 و2026", "2026")
        )
        val (analyzed, summary) = searchEngine.compareSourcesAndDetectConflicts(citations)
        assertEquals(2, analyzed.size)
        assertNotNull(summary)
        assertTrue(summary!!.contains("تباين في التواريخ"))
    }

    @Test
    fun `json serializer round-trips steps and citations accurately`() {
        val steps = listOf(
            ActionStep(1, "الخطوة الأولى", "افتح التطبيق", "زر البدء", true),
            ActionStep(2, "الخطوة الثانية", "اختر الإعدادات", null, false)
        )
        val encodedSteps = JsonModelSerializer.encodeSteps(steps)
        val decodedSteps = JsonModelSerializer.decodeSteps(encodedSteps)
        assertEquals(2, decodedSteps.size)
        assertTrue(decodedSteps[0].isCompleted)
        assertEquals("الخطوة الثانية", decodedSteps[1].title)
    }
}
