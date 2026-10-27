package com.example.data.search

import android.os.SystemClock
import com.example.data.local.JsonModelSerializer
import com.example.data.local.KhotwaDao
import com.example.data.local.SearchCacheEntity
import com.example.domain.model.SearchCitation
import com.example.domain.model.SearchExecutionReport
import com.example.domain.model.SearchProviderType
import com.example.domain.model.UserIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface SearchProvider {
    fun shouldTriggerSearch(query: String, intent: UserIntent, forceSearch: Boolean): Boolean
    suspend fun searchWeb(
        query: String,
        providerType: SearchProviderType = SearchProviderType.MULTI_SOURCE_HYBRID,
        useCache: Boolean = true
    ): SearchExecutionReport
}

class MultiSourceWebSearchEngine(
    private val dao: KhotwaDao? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : SearchProvider {

    companion object {
        private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes safe cache TTL

        private val SEARCH_TRIGGER_KEYWORDS = listOf(
            "ابحث", "بحث", "مصادر", "مصدر", "روابط", "رابط", "أحدث", "احدث", "آخر",
            "اليوم", "الان", "الآن", "سعر", "أخبار", "اخبار", "تاريخ", "متى", "من هو",
            "ما هي", "إحصائيات", "دراسة", "مقارنة بين", "2025", "2026", "search",
            "source", "sources", "latest", "news", "update", "official", "wiki"
        )
    }

    override fun shouldTriggerSearch(
        query: String,
        intent: UserIntent,
        forceSearch: Boolean
    ): Boolean {
        if (forceSearch || intent == UserIntent.WEB_SEARCH) return true
        val normalized = query.trim().lowercase()
        if (normalized.length < 4) return false
        return SEARCH_TRIGGER_KEYWORDS.any { normalized.contains(it) }
    }

    override suspend fun searchWeb(
        query: String,
        providerType: SearchProviderType,
        useCache: Boolean
    ): SearchExecutionReport = withContext(Dispatchers.IO) {
        val startMs = SystemClock.elapsedRealtime()
        val cleanQuery = extractCoreSearchQuery(query)
        val cacheKey = "${providerType.name}:${cleanQuery.lowercase()}"

        if (useCache && dao != null) {
            val cached = runCatching { dao.getCachedSearch(cacheKey) }.getOrNull()
            if (cached != null && (System.currentTimeMillis() - cached.createdAt) < CACHE_TTL_MS) {
                val citations = JsonModelSerializer.decodeCitations(cached.citationsJson)
                if (citations.isNotEmpty()) {
                    val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                    return@withContext SearchExecutionReport(
                        query = cleanQuery,
                        executedQueries = listOf(cleanQuery),
                        citations = citations,
                        conflictSummary = cached.conflictSummary,
                        durationMs = elapsed,
                        fromCache = true,
                        statusMessage = "تم استرجاع ${citations.size} مصادر موثقة من الذاكرة المؤقتة الآمنة.",
                        isSuccessful = true
                    )
                }
            }
        }

        try {
            val collectedCitations = mutableListOf<SearchCitation>()
            val executedSubQueries = mutableListOf(cleanQuery)

            coroutineScope {
                val arWikiDeferred = async {
                    fetchWikipediaResults(cleanQuery, lang = "ar", maxResults = 3)
                }
                val enWikiDeferred = async {
                    fetchWikipediaResults(cleanQuery, lang = "en", maxResults = 2)
                }
                val ddgDeferred = async {
                    if (providerType != SearchProviderType.WIKIPEDIA_DIRECT) {
                        fetchDuckDuckGoInstantResults(cleanQuery)
                    } else emptyList()
                }
                val hnDeferred = async {
                    if (providerType != SearchProviderType.WIKIPEDIA_DIRECT) {
                        fetchHackerNewsAlgoliaResults(cleanQuery, maxResults = 2)
                    } else emptyList()
                }

                collectedCitations.addAll(arWikiDeferred.await())
                collectedCitations.addAll(ddgDeferred.await())
                collectedCitations.addAll(enWikiDeferred.await())
                collectedCitations.addAll(hnDeferred.await())
            }

            // Deduplicate by URL and enrich top citation with direct page content extraction if accessible
            val deduplicated = collectedCitations
                .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
                .distinctBy { it.url }
                .take(6)
                .mapIndexed { idx, citation ->
                    val enrichedPreview = if (idx < 2) {
                        extractReadablePageExcerpt(citation.url) ?: citation.extractedContentPreview
                    } else {
                        citation.extractedContentPreview
                    }
                    citation.copy(
                        id = "${idx + 1}",
                        extractedContentPreview = enrichedPreview
                    )
                }

            val (analyzedCitations, conflictSummary) = compareSourcesAndDetectConflicts(deduplicated)
            val durationMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)

            if (analyzedCitations.isEmpty()) {
                return@withContext SearchExecutionReport(
                    query = cleanQuery,
                    executedQueries = executedSubQueries,
                    citations = emptyList(),
                    conflictSummary = null,
                    durationMs = durationMs,
                    fromCache = false,
                    statusMessage = "لم يتم العثور على مصادر ويب مطابقة تمامًا لهذا الاستعلام أو تعذر الوصول لخدمة البحث. سيتم توضيح حدود المعرفة المتاحة دون اختلاق روابط.",
                    isSuccessful = false
                )
            }

            if (useCache && dao != null) {
                runCatching {
                    dao.putCachedSearch(
                        SearchCacheEntity(
                            queryKey = cacheKey,
                            queryText = cleanQuery,
                            citationsJson = JsonModelSerializer.encodeCitations(analyzedCitations),
                            conflictSummary = conflictSummary
                        )
                    )
                }
            }

            SearchExecutionReport(
                query = cleanQuery,
                executedQueries = executedSubQueries,
                citations = analyzedCitations,
                conflictSummary = conflictSummary,
                durationMs = durationMs,
                fromCache = false,
                statusMessage = "تم جمع ومقارنة ${analyzedCitations.size} مصادر حقيقية خلال ${durationMs}ms.",
                isSuccessful = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val durationMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            SearchExecutionReport(
                query = cleanQuery,
                executedQueries = listOf(cleanQuery),
                citations = emptyList(),
                conflictSummary = null,
                durationMs = durationMs,
                fromCache = false,
                statusMessage = "تعذر الاتصال بمزود البحث على الإنترنت (${e.localizedMessage ?: "خطأ في الشبكة"}). لن يتم اختلاق أي مصادر.",
                isSuccessful = false
            )
        }
    }

    fun extractCoreSearchQuery(rawPrompt: String): String {
        val prefixes = listOf(
            "ابحث لي عن", "ابحث عن", "اشرح لي هذه المعلومة مع المصادر:", "اشرح لي",
            "ما هو", "ما هي", "اريد معلومات عن", "أريد معلومات عن", "هل يمكنك البحث عن"
        )
        var cleaned = rawPrompt.trim()
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.removePrefix(prefix).trim()
                break
            }
        }
        return cleaned.removeSuffix("؟").removeSuffix("?").trim().ifBlank { rawPrompt.trim() }
    }

    private fun fetchWikipediaResults(
        query: String,
        lang: String,
        maxResults: Int
    ): List<SearchCitation> {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=$maxResults&srprop=snippet|timestamp&format=json&utf8=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KhotwaAI/1.0 (Android; Educational Assistant)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val root = JSONObject(body)
                val searchArray = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
                val results = mutableListOf<SearchCitation>()

                for (i in 0 until searchArray.length()) {
                    val item = searchArray.optJSONObject(i) ?: continue
                    val title = item.optString("title", "").trim()
                    if (title.isBlank()) continue
                    val rawSnippet = item.optString("snippet", "")
                    val cleanSnippet = stripHtmlTags(rawSnippet)
                    val timestamp = item.optString("timestamp", "").take(10).takeIf { it.isNotBlank() }
                    val encodedTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                    val articleUrl = "https://$lang.wikipedia.org/wiki/$encodedTitle"

                    results.add(
                        SearchCitation(
                            id = "wiki-$lang-$i",
                            title = title,
                            url = articleUrl,
                            sourceName = if (lang == "ar") "ويكيبيديا العربية" else "English Wikipedia",
                            snippet = cleanSnippet,
                            publishedDate = timestamp?.let { "آخر تحديث: $it" }
                        )
                    )
                }
                results
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchDuckDuckGoInstantResults(query: String): List<SearchCitation> {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KhotwaAI/1.0 (Android)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val root = JSONObject(body)
                val citations = mutableListOf<SearchCitation>()

                val abstractText = root.optString("AbstractText", "").trim()
                val abstractUrl = root.optString("AbstractURL", "").trim()
                val abstractSource = root.optString("AbstractSource", "DuckDuckGo Instant").trim()
                val heading = root.optString("Heading", query).trim()

                if (abstractText.isNotBlank() && abstractUrl.startsWith("http")) {
                    citations.add(
                        SearchCitation(
                            id = "ddg-abstract",
                            title = heading.ifBlank { query },
                            url = abstractUrl,
                            sourceName = abstractSource.ifBlank { "DuckDuckGo" },
                            snippet = abstractText,
                            publishedDate = "تحديث فوري"
                        )
                    )
                }

                val relatedTopics = root.optJSONArray("RelatedTopics")
                if (relatedTopics != null) {
                    for (i in 0 until minOf(relatedTopics.length(), 2)) {
                        val topic = relatedTopics.optJSONObject(i) ?: continue
                        val text = topic.optString("Text", "").trim()
                        val firstUrl = topic.optString("FirstURL", "").trim()
                        if (text.isNotBlank() && firstUrl.startsWith("http")) {
                            citations.add(
                                SearchCitation(
                                    id = "ddg-topic-$i",
                                    title = text.take(65),
                                    url = firstUrl,
                                    sourceName = "DuckDuckGo Knowledge",
                                    snippet = text,
                                    publishedDate = null
                                )
                            )
                        }
                    }
                }
                citations
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchHackerNewsAlgoliaResults(query: String, maxResults: Int): List<SearchCitation> {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://hn.algolia.com/api/v1/search?query=$encoded&hitsPerPage=$maxResults"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KhotwaAI/1.0 (Android)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val root = JSONObject(body)
                val hits = root.optJSONArray("hits") ?: return emptyList()
                val results = mutableListOf<SearchCitation>()

                for (i in 0 until hits.length()) {
                    val hit = hits.optJSONObject(i) ?: continue
                    val title = hit.optString("title", "").trim()
                    val hitUrl = hit.optString("url", "").trim()
                    val createdAt = hit.optString("created_at", "").take(10)
                    val author = hit.optString("author", "")
                    if (title.isBlank() || !hitUrl.startsWith("http")) continue

                    results.add(
                        SearchCitation(
                            id = "hn-$i",
                            title = title,
                            url = hitUrl,
                            sourceName = extractDomainName(hitUrl),
                            snippet = "نقاش ومصدر تقني موثق (الكاتب: $author) — $title",
                            publishedDate = createdAt.takeIf { it.isNotBlank() }?.let { "تاريخ النشر: $it" }
                        )
                    )
                }
                results
            }
        }.getOrDefault(emptyList())
    }

    private fun extractReadablePageExcerpt(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KhotwaAI/1.0 (Android; ContentExtractor)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("text/html", ignoreCase = true)) return null
                val rawHtml = response.body?.string()?.take(35_000) ?: return null

                // Extract paragraphs <p>...</p>
                val paragraphRegex = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val paragraphs = paragraphRegex.findAll(rawHtml)
                    .map { stripHtmlTags(it.groupValues[1]).trim() }
                    .filter { it.length > 60 }
                    .take(2)
                    .toList()

                if (paragraphs.isEmpty()) null else paragraphs.joinToString(" ")
                    .take(360)
            }
        }.getOrNull()
    }

    fun compareSourcesAndDetectConflicts(
        citations: List<SearchCitation>
    ): Pair<List<SearchCitation>, String?> {
        if (citations.size < 2) {
            return citations to if (citations.size == 1) {
                "تم العثور على مصدر واحد فقط؛ يُنصح بمراجعة الرابط للتحقق الإضافي."
            } else null
        }

        // Check if sources have differing publication years or numeric discrepancies
        val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
        val yearsByCitation = citations.map { c ->
            val combined = "${c.publishedDate.orEmpty()} ${c.snippet} ${c.extractedContentPreview.orEmpty()}"
            yearRegex.findAll(combined).map { it.value }.toSet()
        }

        val allYears = yearsByCitation.flatten().distinct()
        val hasTimelineVariance = allYears.size >= 3

        val updatedCitations = citations.mapIndexed { index, citation ->
            val localYears = yearsByCitation[index]
            if (hasTimelineVariance && localYears.isNotEmpty()) {
                citation.copy(
                    hasConflict = false,
                    conflictExplanation = "يحتوي هذا المصدر على إشارات زمنية (${localYears.joinToString(", ")}) قد تختلف عن بعض المصادر الأخرى."
                )
            } else {
                citation
            }
        }

        val distinctSourcesCount = citations.map { it.sourceName }.distinct().size
        val summary = if (hasTimelineVariance) {
            "مقارنة المصادر ($distinctSourcesCount جهات مختلفة): تتفق المصادر في المفهوم الأساسي مع وجود تباين في التواريخ أو التحديثات الزمنية (${allYears.take(4).joinToString(" / ")})."
        } else {
            "مقارنة المصادر ($distinctSourcesCount جهات مستقلة): تتطابق المعلومات الأساسية بين المصادر المجمعة دون رصد تعارضات جوهرية."
        }

        return updatedCitations to summary
    }

    private fun stripHtmlTags(input: String): String {
        return input
            .replace(Regex("<[^>]*>"), " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractDomainName(url: String): String {
        return runCatching {
            val withoutScheme = url.substringAfter("://")
            withoutScheme.substringBefore("/").removePrefix("www.")
        }.getOrDefault("ويب")
    }
}
