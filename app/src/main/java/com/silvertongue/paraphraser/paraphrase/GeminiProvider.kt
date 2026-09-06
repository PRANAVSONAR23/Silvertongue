package com.silvertongue.paraphraser.paraphrase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    @SerialName("responseMimeType") val responseMimeType: String
)

@Serializable
private data class GeminiRequest(
    @SerialName("systemInstruction") val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig
)

@Serializable
private data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
private data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

class GeminiProvider(
    private val keySource: ApiKeySource,
    private val client: OkHttpClient = HttpClients.shared,
    private val model: String = DEFAULT_MODEL
) : ParaphraseProvider {

    override val id = ProviderId.GEMINI

    override suspend fun paraphrase(rawText: String): List<String> {
        val apiKey = keySource.currentKey()
        if (apiKey.isBlank()) throw ParaphraseException("No Gemini API key configured", ParaphraseFailure.NO_KEY)

        val payload = GeminiRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(Prompts.SYSTEM))),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(Prompts.userMessage(rawText)))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.4,
                maxOutputTokens = 1024,
                responseMimeType = "application/json"
            )
        )

        val request = Request.Builder()
            .url("$BASE_URL/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(
                HttpClients.json.encodeToString(GeminiRequest.serializer(), payload)
                    .toRequestBody(HttpClients.jsonMediaType)
            )
            .build()

        val body = client.awaitResponseBody(request, id.displayName)
        val response = runCatching {
            HttpClients.json.decodeFromString(GeminiResponse.serializer(), body)
        }.getOrElse { throw ParaphraseException("Could not read Gemini response", ParaphraseFailure.BAD_RESPONSE, it) }

        val content = response.candidates.firstOrNull()?.content?.parts?.joinToString("") { it.text }
        if (content.isNullOrBlank()) throw ParaphraseException("Gemini response contained no text", ParaphraseFailure.BAD_RESPONSE)

        return SuggestionParser.parse(content)
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
    }
}
