package com.silvertongue.paraphraser.paraphrase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SuggestionsEnvelope(val suggestions: List<String> = emptyList())

object SuggestionParser {

    private const val MAX_SUGGESTIONS = 3

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val fencePattern = Regex("```[a-zA-Z]*\\s*|```")

    fun parse(modelOutput: String): List<String> {
        val cleaned = modelOutput.replace(fencePattern, "").trim()
        val suggestions = decodeEnvelope(cleaned) ?: decodeBareArray(cleaned)
        ?: throw ParaphraseException("Model did not return usable JSON", ParaphraseFailure.BAD_RESPONSE)

        val usable = suggestions
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SUGGESTIONS)

        if (usable.isEmpty()) throw ParaphraseException("Model returned no suggestions", ParaphraseFailure.BAD_RESPONSE)
        return usable
    }

    private fun decodeEnvelope(text: String): List<String>? {
        val body = substringBetween(text, '{', '}') ?: return null
        return runCatching { json.decodeFromString<SuggestionsEnvelope>(body).suggestions }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun decodeBareArray(text: String): List<String>? {
        val body = substringBetween(text, '[', ']') ?: return null
        return runCatching { json.decodeFromString<List<String>>(body) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun substringBetween(text: String, open: Char, close: Char): String? {
        val start = text.indexOf(open)
        val end = text.lastIndexOf(close)
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }
}
