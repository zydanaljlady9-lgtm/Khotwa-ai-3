package com.example.data.ai

import android.os.SystemClock
import android.util.Log
import com.example.data.local.KhotwaSettingsState
import com.example.domain.model.ActionStep
import com.example.domain.model.AiModelTier
import com.example.domain.model.AiProviderType
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.GuidanceStepInstruction
import com.example.domain.model.ResponseTelemetry
import com.example.domain.model.ScreenInspectionSnapshot
import com.example.domain.model.SearchCitation
import com.example.domain.model.StructuredAnswer
import com.example.domain.model.UserIntent
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "KhotwaAiEngine"

sealed interface AiStreamEvent {
    data class IntentDetected(
        val intent: UserIntent,
        val selectedModel: String,
        val analysisMs: Long
    ) : AiStreamEvent

    data class SearchProgress(val message: String) : AiStreamEvent

    data class PartialText(
        val accumulatedText: String,
        val ttftMs: Long
    ) : AiStreamEvent

    data class Completed(val structuredAnswer: StructuredAnswer) : AiStreamEvent

    data class Failure(val errorMessage: String) : AiStreamEvent
}

data class ConnectionDiagnosticResult(
    val isSuccess: Boolean,
    val providerName: String,
    val modelTested: String,
    val latencyMs: Long,
    val details: String
)

class KhotwaAiEngine(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {

    // Lazy initialization of Firebase AI models per skill mandate
    private val fastModel by lazy {
        Firebase.ai.generativeModel(modelName = AiModelTier.FAST.modelId)
    }

    private val balancedModel by lazy {
        Firebase.ai.generativeModel(modelName = AiModelTier.BALANCED.modelId)
    }

    private val reasoningModel by lazy {
        Firebase.ai.generativeModel(modelName = AiModelTier.REASONING.modelId)
    }

    private val guidanceStepSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "instructionTitle" to Schema.string(description = "عنوان مختصر للخطوة الحالية بالعربية"),
                "simpleExplanation" to Schema.string(description = "شرح بسيط وواضح لما يجب على المستخدم فعله الآن"),
                "matchedElementIndex" to Schema.integer(description = "رقم العنصر المطابق من قائمة عناصر الشاشة أو -1 إذا لم يوجد"),
                "totalEstimatedSteps" to Schema.integer(description = "العدد التقديري لإجمالي الخطوات"),
                "safetyWarning" to Schema.string(description = "تنبيه أمان إذا كانت الخطوة تتضمن إجراء حساسًا أو فارغ"),
                "isGoalCompleted" to Schema.boolean(description = "هل اكتمل هدف المستخدم بالكامل")
            ),
            optionalProperties = listOf("safetyWarning")
        )
    }

    private val guidanceStructuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelTier.FAST.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = guidanceStepSchema
            }
        )
    }

    // Safe in-memory cache for identical deterministic queries within a session
    private val safeSessionCache = ConcurrentHashMap<String, StructuredAnswer>()

    /**
     * Analyzes user prompt in Modern Standard Arabic, Arabic dialects, or English
     * to determine intent before selecting response strategy and model.
     */
    fun analyzeUserIntent(prompt: String, forceSearch: Boolean = false): UserIntent {
        if (forceSearch) return UserIntent.WEB_SEARCH
        val text = prompt.trim().lowercase()

        val guidanceKeywords = listOf(
            "ساعدني على استخدام", "كيف أستخدم تطبيق", "داخل تطبيق", "خطوة بخطوة في تطبيق",
            "إعدادات الهاتف", "اعدادات الهاتف", "وين ألقى", "ازاي افتح", "كيف افعل",
            "أرشدني في", "ارشدني", "توجيه داخل", "بلوتوث", "واي فاي", "إشعارات", "اشعارات"
        )
        if (guidanceKeywords.any { text.contains(it) }) {
            return UserIntent.APP_GUIDANCE
        }

        val planningKeywords = listOf(
            "خطط", "خطة", "جدول", "مراحل", "كيف أنجز", "كيف انجز", "خطوات تنفيذ",
            "ابغى خطة", "عايز خطة", "رتب لي", "مشروع", "plan", "roadmap", "steps to"
        )
        if (planningKeywords.any { text.contains(it) }) {
            return UserIntent.TASK_PLANNING
        }

        val analysisKeywords = listOf(
            "حلل", "قارن", "مقارنة", "أفضل حل", "افضل حل", "مميزات وعيوب", "الفرق بين",
            "استنتج", "تقييم", "أيهما أفضل", "ايهما افضل", "compare", "analyze", "pros and cons"
        )
        if (analysisKeywords.any { text.contains(it) }) {
            return UserIntent.DEEP_ANALYSIS
        }

        val searchKeywords = listOf(
            "ابحث", "بحث", "مصادر", "مصدر", "روابط", "أحدث", "احدث", "أخبار",
            "تاريخ", "إحصائيات", "مع المصادر", "search", "sources", "latest"
        )
        if (searchKeywords.any { text.contains(it) }) {
            return UserIntent.WEB_SEARCH
        }

        return if (text.length > 140) UserIntent.DEEP_ANALYSIS else UserIntent.QUICK_ANSWER
    }

    /**
     * Selects the optimal model based on user settings and detected intent.
     */
    fun selectOptimalModelId(tier: AiModelTier, intent: UserIntent, promptLength: Int): String {
        return when (tier) {
            AiModelTier.FAST -> AiModelTier.FAST.modelId
            AiModelTier.BALANCED -> AiModelTier.BALANCED.modelId
            AiModelTier.REASONING -> AiModelTier.REASONING.modelId
            AiModelTier.AUTO -> when (intent) {
                UserIntent.QUICK_ANSWER -> if (promptLength < 90) {
                    AiModelTier.FAST.modelId
                } else {
                    AiModelTier.BALANCED.modelId
                }
                UserIntent.WEB_SEARCH,
                UserIntent.TASK_PLANNING,
                UserIntent.APP_GUIDANCE,
                UserIntent.DEEP_ANALYSIS -> AiModelTier.BALANCED.modelId
            }
        }
    }

    fun streamStructuredResponse(
        userPrompt: String,
        conversationHistory: List<ChatMessageUi>,
        citations: List<SearchCitation>,
        conflictSummary: String?,
        searchDurationMs: Long,
        settings: KhotwaSettingsState,
        forcedIntent: UserIntent? = null
    ): Flow<AiStreamEvent> = flow {
        val overallStartMs = SystemClock.elapsedRealtime()
        val intentStartMs = SystemClock.elapsedRealtime()
        val detectedIntent = forcedIntent ?: analyzeUserIntent(userPrompt, citations.isNotEmpty())
        val selectedModelId = selectOptimalModelId(
            tier = settings.aiModelTier,
            intent = detectedIntent,
            promptLength = userPrompt.length
        )
        val intentAnalysisMs = (SystemClock.elapsedRealtime() - intentStartMs).coerceAtLeast(1L)

        emit(AiStreamEvent.IntentDetected(detectedIntent, selectedModelId, intentAnalysisMs))

        val cacheKey = "${settings.aiProvider.name}:$selectedModelId:${detectedIntent.name}:${userPrompt.trim().lowercase()}:${citations.size}"
        if (settings.safeCacheEnabled && conversationHistory.isEmpty() && safeSessionCache.containsKey(cacheKey)) {
            val cached = safeSessionCache[cacheKey]!!
            emit(AiStreamEvent.PartialText(cached.directAnswer, 5L))
            emit(
                AiStreamEvent.Completed(
                    cached.copy(
                        telemetry = cached.telemetry.copy(fromCache = true, ttftMs = 5L)
                    )
                )
            )
            return@flow
        }

        val fullPrompt = buildSystemAndContextPrompt(
            userPrompt = userPrompt,
            intent = detectedIntent,
            history = conversationHistory.takeLast(6),
            citations = citations,
            conflictSummary = conflictSummary
        )

        var ttftMs = 0L
        val accumulated = StringBuilder()

        try {
            if (settings.aiProvider == AiProviderType.CUSTOM_ENDPOINT && settings.customEndpointUrl.isNotBlank()) {
                val customResult = callCustomEndpoint(
                    endpointUrl = settings.customEndpointUrl,
                    prompt = fullPrompt
                )
                ttftMs = (SystemClock.elapsedRealtime() - overallStartMs).coerceAtLeast(10L)
                accumulated.append(customResult)
                emit(AiStreamEvent.PartialText(accumulated.toString(), ttftMs))
            } else {
                val targetModel = when (selectedModelId) {
                    AiModelTier.FAST.modelId -> fastModel
                    AiModelTier.REASONING.modelId -> reasoningModel
                    else -> balancedModel
                }

                try {
                    withTimeout(60_000L) {
                        targetModel.generateContentStream(fullPrompt).collect { chunk ->
                            val piece = chunk.text.orEmpty()
                            if (piece.isNotEmpty()) {
                                if (ttftMs == 0L) {
                                    ttftMs = (SystemClock.elapsedRealtime() - overallStartMs).coerceAtLeast(1L)
                                }
                                accumulated.append(piece)
                                emit(AiStreamEvent.PartialText(accumulated.toString(), ttftMs))
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (primaryError: Exception) {
                    // If reasoning model fails (e.g. Blaze plan required), fallback seamlessly to gemini-3.8-flash
                    if (selectedModelId == AiModelTier.REASONING.modelId) {
                        Log.w(TAG, "Reasoning model fallback to gemini-3.8-flash", primaryError)
                        withTimeout(60_000L) {
                            balancedModel.generateContentStream(fullPrompt).collect { chunk ->
                                val piece = chunk.text.orEmpty()
                                if (piece.isNotEmpty()) {
                                    if (ttftMs == 0L) {
                                        ttftMs = (SystemClock.elapsedRealtime() - overallStartMs).coerceAtLeast(1L)
                                    }
                                    accumulated.append(piece)
                                    emit(AiStreamEvent.PartialText(accumulated.toString(), ttftMs))
                                }
                            }
                        }
                    } else {
                        throw primaryError
                    }
                }
            }

            val finalRawText = accumulated.toString().trim()
            if (finalRawText.isBlank()) {
                emit(AiStreamEvent.Failure("لم يُرجع النموذج نصًا. يرجى المحاولة مرة أخرى."))
                return@flow
            }

            val totalDurationMs = (SystemClock.elapsedRealtime() - overallStartMs).coerceAtLeast(ttftMs)
            val parsed = parseStructuredSections(
                rawText = finalRawText,
                intent = detectedIntent,
                citations = citations,
                conflictSummary = conflictSummary,
                telemetry = ResponseTelemetry(
                    modelUsed = selectedModelId,
                    intentAnalysisMs = intentAnalysisMs,
                    searchDurationMs = searchDurationMs,
                    ttftMs = if (ttftMs > 0L) ttftMs else totalDurationMs,
                    totalDurationMs = totalDurationMs,
                    fromCache = false
                )
            )

            if (settings.safeCacheEnabled && conversationHistory.isEmpty()) {
                safeSessionCache[cacheKey] = parsed
            }

            emit(AiStreamEvent.Completed(parsed))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            emit(AiStreamEvent.Failure("انتهت مهلة الاتصال بالنموذج بعد 60 ثانية. يرجى التحقق من الشبكة والمحاولة مجددًا."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            emit(
                AiStreamEvent.Failure(
                    "تعذر إتمام الطلب عبر محرك الذكاء الاصطناعي: ${e.localizedMessage ?: "خطأ غير متوقع"}"
                )
            )
        }
    }

    /**
     * Generates the next interactive in-app guidance step based on the user's goal
     * and the current screen's AccessibilityNodeInfo snapshot.
     */
    suspend fun generateNextGuidanceStep(
        userGoal: String,
        currentStepNumber: Int,
        snapshot: ScreenInspectionSnapshot,
        previousStepsHistory: List<String>
    ): GuidanceStepInstruction {
        if (snapshot.isProtectedOrPasswordScreen) {
            return GuidanceStepInstruction(
                stepIndex = currentStepNumber,
                totalEstimatedSteps = currentStepNumber + 1,
                instructionTitle = "شاشة محمية أو تحتوي على حقل سري",
                simpleExplanation = "توقف خُطوة AI تلقائيًا عن قراءة هذه الشاشة لحماية خصوصيتك (كلمة مرور أو شاشة محمية). أكمِل هذا الإجراء بنفسك ثم اضغط «تحليل الشاشة التالية».",
                matchedElement = null,
                safetyWarning = "حماية الخصوصية نشطة: لا يقرأ التطبيق حقول كلمات المرور أو الشاشات المحمية.",
                requiresUserConfirmation = true,
                isGoalCompleted = false
            )
        }

        val elementsSummary = snapshot.elements.take(30).joinToString("\n") { el ->
            "[${el.index}] النص=\"${el.displayLabel}\" | قابل للنقر=${el.isClickable} | حقل إدخال=${el.isEditable}"
        }

        val prompt = buildString {
            appendLine("أنت محرك الإرشاد التفاعلي في تطبيق خُطوة AI على نظام Android.")
            appendLine("هدف المستخدم: $userGoal")
            appendLine("التطبيق المفتوح حاليًا: ${snapshot.appDisplayName} (${snapshot.packageName})")
            appendLine("رقم الخطوة الحالية: $currentStepNumber")
            if (previousStepsHistory.isNotEmpty()) {
                appendLine("الخطوات السابقة التي تم إرشاد المستخدم إليها: ${previousStepsHistory.joinToString(" -> ")}")
            }
            appendLine("عناصر الشاشة المرئية حاليًا:")
            appendLine(elementsSummary.ifBlank { "لا توجد عناصر نصية واضحة على هذه الشاشة." })
            appendLine("حدد الخطوة التالية بلغة عربية بسيطة ومباشرة، واختر رقم العنصر (matchedElementIndex) الأنسب من القائمة أعلاه (أو -1 إذا لم يكن العنصر ظاهرًا بعد).")
        }

        return try {
            val response = withTimeout(45_000L) {
                guidanceStructuredModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("استجابة فارغة من نموذج الإرشاد")
            val json = JSONObject(jsonText)
            val matchedIndex = json.optInt("matchedElementIndex", -1)
            val matchedElement = snapshot.elements.find { it.index == matchedIndex }
            val safetyNote = json.optString("safetyWarning", "").takeIf { it.isNotBlank() }

            GuidanceStepInstruction(
                stepIndex = currentStepNumber,
                totalEstimatedSteps = json.optInt("totalEstimatedSteps", 4).coerceAtLeast(currentStepNumber),
                instructionTitle = json.optString("instructionTitle", "الخطوة $currentStepNumber"),
                simpleExplanation = json.optString("simpleExplanation", "اتبع العنصر الموضح على الشاشة للمتابعة."),
                matchedElement = matchedElement,
                safetyWarning = safetyNote,
                requiresUserConfirmation = true,
                isGoalCompleted = json.optBoolean("isGoalCompleted", false)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Guidance step inference failed, using heuristic matcher", e)
            // Deterministic local fallback matcher if offline or timeout so user is never stranded
            val goalTokens = userGoal.split(" ").filter { it.length >= 3 }
            val bestMatch = snapshot.elements.firstOrNull { el ->
                goalTokens.any { token -> el.displayLabel.contains(token, ignoreCase = true) }
            } ?: snapshot.elements.firstOrNull { it.isClickable }

            GuidanceStepInstruction(
                stepIndex = currentStepNumber,
                totalEstimatedSteps = 4,
                instructionTitle = bestMatch?.let { "انقر على «${it.displayLabel}»" } ?: "تحقق من عناصر الشاشة الحالية",
                simpleExplanation = if (bestMatch != null) {
                    "بناءً على عناصر الشاشة المتاحة في (${snapshot.appDisplayName})، العنصر الأقرب لهدفك هو «${bestMatch.displayLabel}»."
                } else {
                    "تعذر الاتصال بالنموذج السحابي (${e.localizedMessage ?: "خطأ شبكة"}). يمكنك التمرير في الشاشة ثم الضغط على تحديث الخطوة."
                },
                matchedElement = bestMatch,
                safetyWarning = "تنبيه: تعذر الوصول لخدمة الاستدلال السحابية؛ تم استخدام المطابقة المحلية لعناصر الشاشة.",
                requiresUserConfirmation = true,
                isGoalCompleted = false
            )
        }
    }

    suspend fun testConnection(settings: KhotwaSettingsState): ConnectionDiagnosticResult {
        val startMs = SystemClock.elapsedRealtime()
        val modelId = if (settings.aiModelTier == AiModelTier.AUTO) {
            AiModelTier.FAST.modelId
        } else {
            settings.aiModelTier.modelId
        }

        return try {
            if (settings.aiProvider == AiProviderType.CUSTOM_ENDPOINT) {
                if (settings.customEndpointUrl.isBlank()) {
                    return ConnectionDiagnosticResult(
                        isSuccess = false,
                        providerName = settings.aiProvider.arabicName,
                        modelTested = "Custom REST",
                        latencyMs = 0L,
                        details = "يرجى إدخال رابط نقطة النهاية (Endpoint URL) أولًا."
                    )
                }
                val reply = callCustomEndpoint(settings.customEndpointUrl, "أجب بكلمة: متصل")
                val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                ConnectionDiagnosticResult(
                    isSuccess = reply.isNotBlank(),
                    providerName = settings.aiProvider.arabicName,
                    modelTested = "Custom REST",
                    latencyMs = elapsed,
                    details = "تم الاتصال بنجاح (${elapsed}ms): ${reply.take(60)}"
                )
            } else {
                val model = when (modelId) {
                    AiModelTier.FAST.modelId -> fastModel
                    AiModelTier.REASONING.modelId -> reasoningModel
                    else -> balancedModel
                }
                val response = withTimeout(30_000L) {
                    model.generateContent("أجب بكلمة واحدة فقط بالعربية: جاهز")
                }
                val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                val text = response.text.orEmpty().trim()
                ConnectionDiagnosticResult(
                    isSuccess = text.isNotBlank(),
                    providerName = settings.aiProvider.arabicName,
                    modelTested = modelId,
                    latencyMs = elapsed,
                    details = "الاتصال سليم وموثق عبر App Check (${elapsed}ms) — استجابة النموذج: «${text.take(40)}»"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            ConnectionDiagnosticResult(
                isSuccess = false,
                providerName = settings.aiProvider.arabicName,
                modelTested = modelId,
                latencyMs = elapsed,
                details = "فشل اختبار الاتصال (${elapsed}ms): ${e.localizedMessage ?: "خطأ غير معروف"}"
            )
        }
    }

    private suspend fun callCustomEndpoint(endpointUrl: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                    )
                )
            }
            val request = Request.Builder()
                .url(endpointUrl)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", body)
                        ?: body
                } else {
                    json.optString("text", body)
                }
            }
        }

    private fun buildSystemAndContextPrompt(
        userPrompt: String,
        intent: UserIntent,
        history: List<ChatMessageUi>,
        citations: List<SearchCitation>,
        conflictSummary: String?
    ): String = buildString {
        appendLine("أنت «خُطوة AI» (Khotwa AI)، مساعد ذكي شخصي متقدم باللغة العربية والإنجليزية.")
        appendLine("هويتك: تحويل طلب المستخدم إلى معرفة دقيقة وخطوات عملية قابلة للتنفيذ بدون حشو أو ادعاء قدرات غير متوفرة.")
        appendLine("نوع النية المكتشفة لهذا الطلب: ${intent.arabicTitle} (${intent.description}).")
        appendLine()
        appendLine("قواعد صارمة للإجابة:")
        appendLine("1. افهم العربية الفصحى واللهجات العربية والإنجليزية بدقة، واستفد من السياق السابق دون تكرار أسئلة غير ضرورية.")
        appendLine("2. لا تختلق أي روابط أو مصادر أو تواريخ غير موجودة في بيانات البحث المرفقة أدناه.")
        appendLine("3. نظّم إجابتك بوضوح تام باستخدام العناوين القياسية التالية بالضبط لتسهيل قراءتها:")
        appendLine("### الخلاصة المباشرة")
        appendLine("(اشرح الإجابة بوضوح، وإذا وُجدت مصادر بحث أشر إليها بأرقامها مثل [1]، [2])")
        appendLine("### الحقائق المؤكدة")
        appendLine("- (اذكر الحقائق الثابتة والموثقة فقط في نقاط تبدأ بشرطة -)")
        appendLine("### الاستنتاجات التحليلية")
        appendLine("- (اذكر التحليل والمقارنة والاستنتاجات المنطقية في نقاط تبدأ بشرطة -)")
        appendLine("### الشكوك والحدود المعرفية")
        appendLine("- (اذكر أي نقاط تحتمل التغيير أو تتطلب تحققًا إضافيًا أو تعارضات بين المصادر)")
        appendLine("### الخطوات العملية القابلة للتنفيذ")
        appendLine("1. عنوان الخطوة: شرح موجز وعملي لكيفية التنفيذ.")
        appendLine("2. عنوان الخطوة الثانية: شرح موجز وعملي.")
        appendLine()

        if (history.isNotEmpty()) {
            appendLine("--- السياق السابق للمحادثة ---")
            history.forEach { msg ->
                val speaker = if (msg.isUser) "المستخدم" else "خُطوة AI"
                appendLine("$speaker: ${msg.rawText.take(350)}")
            }
            appendLine()
        }

        if (citations.isNotEmpty()) {
            appendLine("--- نتائج البحث الحقيقي على الإنترنت (اعتمد عليها ووثّق بها إجابتك) ---")
            citations.forEachIndexed { idx, c ->
                appendLine("[${idx + 1}] العنوان: ${c.title}")
                appendLine("    المصدر: ${c.sourceName} | التاريخ: ${c.publishedDate ?: "غير محدد"}")
                appendLine("    الرابط: ${c.url}")
                appendLine("    المقتطف: ${c.snippet}")
                if (!c.extractedContentPreview.isNullOrBlank()) {
                    appendLine("    محتوى مستخرج من الصفحة: ${c.extractedContentPreview}")
                }
            }
            if (!conflictSummary.isNullOrBlank()) {
                appendLine("ملاحظة مقارنة المصادر: $conflictSummary")
            }
            appendLine()
        }

        appendLine("--- طلب المستخدم الحالي ---")
        appendLine(userPrompt)
    }

    fun parseStructuredSections(
        rawText: String,
        intent: UserIntent,
        citations: List<SearchCitation>,
        conflictSummary: String?,
        telemetry: ResponseTelemetry
    ): StructuredAnswer {
        val facts = mutableListOf<String>()
        val inferences = mutableListOf<String>()
        val uncertainties = mutableListOf<String>()
        val steps = mutableListOf<ActionStep>()
        val directSummaryLines = mutableListOf<String>()

        var currentSection = "SUMMARY"

        rawText.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.contains("الخلاصة المباشرة") && trimmed.startsWith("#") -> {
                    currentSection = "SUMMARY"
                }
                trimmed.contains("الحقائق المؤكدة") && trimmed.startsWith("#") -> {
                    currentSection = "FACTS"
                }
                trimmed.contains("الاستنتاجات التحليلية") && trimmed.startsWith("#") -> {
                    currentSection = "INFERENCES"
                }
                (trimmed.contains("الشكوك") || trimmed.contains("الحدود المعرفية")) && trimmed.startsWith("#") -> {
                    currentSection = "UNCERTAINTIES"
                }
                trimmed.contains("الخطوات العملية") && trimmed.startsWith("#") -> {
                    currentSection = "STEPS"
                }
                else -> {
                    val bulletContent = trimmed
                        .removePrefix("-")
                        .removePrefix("•")
                        .removePrefix("*")
                        .trim()

                    when (currentSection) {
                        "SUMMARY" -> {
                            if (trimmed.isNotBlank()) directSummaryLines.add(trimmed)
                        }
                        "FACTS" -> {
                            if (bulletContent.length > 3) facts.add(bulletContent)
                        }
                        "INFERENCES" -> {
                            if (bulletContent.length > 3) inferences.add(bulletContent)
                        }
                        "UNCERTAINTIES" -> {
                            if (bulletContent.length > 3) uncertainties.add(bulletContent)
                        }
                        "STEPS" -> {
                            val numberedMatch = Regex("^(\\d+)[.)\\-]\\s*(.+)$").find(trimmed)
                            val rawStep = numberedMatch?.groupValues?.get(2) ?: bulletContent
                            if (rawStep.length > 3) {
                                val parts = rawStep.split(":", limit = 2)
                                val title = parts[0].replace("**", "").trim()
                                val desc = if (parts.size > 1) parts[1].trim() else title
                                steps.add(
                                    ActionStep(
                                        stepNumber = steps.size + 1,
                                        title = title.ifBlank { "خطوة ${steps.size + 1}" },
                                        description = desc
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        val cleanDirectAnswer = directSummaryLines.joinToString("\n").trim().ifBlank { rawText }

        return StructuredAnswer(
            directAnswer = cleanDirectAnswer,
            detectedIntent = intent,
            facts = facts,
            inferences = inferences,
            uncertainties = uncertainties,
            actionSteps = steps,
            citations = citations,
            searchConflictSummary = conflictSummary,
            telemetry = telemetry
        )
    }

    fun clearSessionCache() {
        safeSessionCache.clear()
    }
}
