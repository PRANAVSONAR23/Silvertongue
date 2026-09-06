package com.silvertongue.paraphraser.paraphrase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class GroqMessage(val role: String, val content: String)

@Serializable
private data class GroqResponseFormat(val type: String)

@Serializable
private data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
    @SerialName("response_format") val responseFormat: GroqResponseFormat
)

@Serializable
private data class GroqChoice(val message: GroqMessage? = null)

@Serializable
private data class GroqCompletion(val choices: List<GroqChoice> = emptyList())

class GroqProvider(
    private val keySource: ApiKeySource,
    private val client: OkHttpClient = HttpClients.shared,
    private val model: String = DEFAULT_MODEL
) : ParaphraseProvider {

    override val id = ProviderId.GROQ

    override suspend fun paraphrase(rawText: String): List<String> {
        val apiKey = keySource.currentKey()
        if (apiKey.isBlank()) throw ParaphraseException("No Groq API key configured", ParaphraseFailure.NO_KEY)

        val payload = GroqRequest(
            model = model,
            messages = listOf(
                GroqMessage("system", Prompts.SYSTEM),
                GroqMessage("user", Prompts.userMessage(rawText))
            ),
            temperature = 0.4,
            maxTokens = 400,
            responseFormat = GroqResponseFormat("json_object")
        )

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(
                HttpClients.json.encodeToString(GroqRequest.serializer(), payload)
                    .toRequestBody(HttpClients.jsonMediaType)
            )
            .build()

        val body = client.awaitResponseBody(request, id.displayName)
        val completion = runCatching {
            HttpClients.json.decodeFromString(GroqCompletion.serializer(), body)
        }.getOrElse { throw ParaphraseException("Could not read Groq response", ParaphraseFailure.BAD_RESPONSE, it) }

        val content = completion.choices.firstOrNull()?.message?.content
            ?: throw ParaphraseException("Groq response contained no message", ParaphraseFailure.BAD_RESPONSE)

        return SuggestionParser.parse(content)
    }

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "qwen/qwen3.8-27b"
    }
}
