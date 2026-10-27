package com.example.data.local

import com.example.domain.model.ActionStep
import com.example.domain.model.SearchCitation
import org.json.JSONArray
import org.json.JSONObject

object JsonModelSerializer {

    fun encodeStringList(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeStringList(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeSteps(steps: List<ActionStep>): String {
        val arr = JSONArray()
        steps.forEach { step ->
            val obj = JSONObject().apply {
                put("stepNumber", step.stepNumber)
                put("title", step.title)
                put("description", step.description)
                put("targetUiElementHint", step.targetUiElementHint ?: "")
                put("isCompleted", step.isCompleted)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeSteps(json: String): List<ActionStep> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                ActionStep(
                    stepNumber = obj.optInt("stepNumber", idx + 1),
                    title = obj.optString("title", "خطوة ${idx + 1}"),
                    description = obj.optString("description", ""),
                    targetUiElementHint = obj.optString("targetUiElementHint", "").takeIf { it.isNotBlank() },
                    isCompleted = obj.optBoolean("isCompleted", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeCitations(citations: List<SearchCitation>): String {
        val arr = JSONArray()
        citations.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("url", c.url)
                put("sourceName", c.sourceName)
                put("snippet", c.snippet)
                put("publishedDate", c.publishedDate ?: "")
                put("extractedContentPreview", c.extractedContentPreview ?: "")
                put("hasConflict", c.hasConflict)
                put("conflictExplanation", c.conflictExplanation ?: "")
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeCitations(json: String): List<SearchCitation> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val url = obj.optString("url", "")
                if (url.isBlank()) return@mapNotNull null
                SearchCitation(
                    id = obj.optString("id", "${idx + 1}"),
                    title = obj.optString("title", "مصدر ويب"),
                    url = url,
                    sourceName = obj.optString("sourceName", "ويب"),
                    snippet = obj.optString("snippet", ""),
                    publishedDate = obj.optString("publishedDate", "").takeIf { it.isNotBlank() },
                    extractedContentPreview = obj.optString("extractedContentPreview", "").takeIf { it.isNotBlank() },
                    hasConflict = obj.optBoolean("hasConflict", false),
                    conflictExplanation = obj.optString("conflictExplanation", "").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }
}
