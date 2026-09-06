package com.silvertongue.paraphraser.paraphrase

import com.silvertongue.paraphraser.data.SettingsRepository
import kotlinx.coroutines.flow.first

class ParaphraseRepository(private val settings: SettingsRepository) {

    private val providers: Map<ProviderId, ParaphraseProvider> = mapOf(
        ProviderId.GROQ to GroqProvider(ApiKeySource { settings.apiKey(ProviderId.GROQ) }),
        ProviderId.GEMINI to GeminiProvider(ApiKeySource { settings.apiKey(ProviderId.GEMINI) }),
        ProviderId.ANTHROPIC to AnthropicProvider(ApiKeySource { settings.apiKey(ProviderId.ANTHROPIC) })
    )

    suspend fun paraphrase(rawText: String): Result<List<String>> {
        val provider = providers.getValue(settings.activeProvider.first())
        return paraphraseWith(provider, rawText)
    }

    suspend fun paraphraseWith(providerId: ProviderId, rawText: String): Result<List<String>> =
        paraphraseWith(providers.getValue(providerId), rawText)

    private suspend fun paraphraseWith(
        provider: ParaphraseProvider,
        rawText: String
    ): Result<List<String>> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(ParaphraseException("Nothing to rewrite"))
        }
        return runCatching { provider.paraphrase(trimmed) }
    }
}
