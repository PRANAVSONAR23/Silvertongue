package com.silvertongue.paraphraser.paraphrase

import android.util.Log

data class ParaphraseOutcome(
    val suggestions: List<String>,
    val provider: ProviderId,
    val usedFallback: Boolean
)

class ParaphraseRepository(
    private val preferences: ProviderPreferences,
    private val providers: Map<ProviderId, ParaphraseProvider> = defaultProviders(preferences)
) {

    suspend fun paraphrase(rawText: String): Result<ParaphraseOutcome> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                ParaphraseException("Nothing to rewrite", ParaphraseFailure.EMPTY_INPUT)
            )
        }

        val active = preferences.currentProvider()
        val primary = attempt(active, trimmed)
        primary.getOrNull()?.let {
            return Result.success(ParaphraseOutcome(it, active, usedFallback = false))
        }

        val primaryError = primary.exceptionOrNull()
        if (!isWorthFallingBack(primaryError)) return Result.failure(primaryError!!)

        for (candidate in ProviderId.entries) {
            if (candidate == active) continue
            if (preferences.keyFor(candidate).isBlank()) continue

            Log.i(TAG, "${active.displayName} failed, trying ${candidate.displayName}")
            attempt(candidate, trimmed).getOrNull()?.let {
                return Result.success(ParaphraseOutcome(it, candidate, usedFallback = true))
            }
        }

        return Result.failure(primaryError!!)
    }

    private suspend fun attempt(providerId: ProviderId, text: String): Result<List<String>> =
        runCatching { providers.getValue(providerId).paraphrase(text) }

    private fun isWorthFallingBack(error: Throwable?): Boolean {
        val failure = (error as? ParaphraseException)?.failure ?: return false
        return failure.isWorthTryingAnotherProvider
    }

    companion object {
        private const val TAG = "SilvertongueRepo"

        fun defaultProviders(preferences: ProviderPreferences): Map<ProviderId, ParaphraseProvider> = mapOf(
            ProviderId.GROQ to GroqProvider(ApiKeySource { preferences.keyFor(ProviderId.GROQ) }),
            ProviderId.GEMINI to GeminiProvider(ApiKeySource { preferences.keyFor(ProviderId.GEMINI) }),
            ProviderId.ANTHROPIC to AnthropicProvider(ApiKeySource { preferences.keyFor(ProviderId.ANTHROPIC) })
        )
    }
}
