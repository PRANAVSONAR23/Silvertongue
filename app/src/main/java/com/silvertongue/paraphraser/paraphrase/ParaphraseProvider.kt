package com.silvertongue.paraphraser.paraphrase

enum class ProviderId(val displayName: String) {
    GROQ("Groq"),
    GEMINI("Gemini"),
    ANTHROPIC("Anthropic")
}

enum class ParaphraseFailure {
    EMPTY_INPUT,
    NO_KEY,
    TRANSPORT,
    RATE_LIMITED,
    SERVER_ERROR,
    MODEL_UNAVAILABLE,
    BAD_RESPONSE,
    REFUSED
}

val ParaphraseFailure.isWorthTryingAnotherProvider: Boolean
    get() = this == ParaphraseFailure.TRANSPORT ||
        this == ParaphraseFailure.RATE_LIMITED ||
        this == ParaphraseFailure.SERVER_ERROR ||
        this == ParaphraseFailure.MODEL_UNAVAILABLE

fun interface ApiKeySource {
    suspend fun currentKey(): String
}

class ParaphraseException(
    message: String,
    val failure: ParaphraseFailure,
    cause: Throwable? = null
) : Exception(message, cause)

interface ParaphraseProvider {
    val id: ProviderId
    suspend fun paraphrase(rawText: String): List<String>
}
