package com.silvertongue.paraphraser.paraphrase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicOutputConfig(val effort: String)

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
    @SerialName("output_config") val outputConfig: AnthropicOutputConfig
)

@Serializable
private data class AnthropicContentBlock(val type: String, val text: String? = null)

@Serializable
private data class AnthropicStopDetails(val category: String? = null, val explanation: String? = null)

@Serializable
private data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("stop_details") val stopDetails: AnthropicStopDetails? = null
)

class AnthropicProvider(
    private val keySource: ApiKeySource,
    private val client: OkHttpClient = HttpClients.shared,
    private val model: String = DEFAULT_MODEL
) : ParaphraseProvider {

    override val id = ProviderId.ANTHROPIC

    override suspend fun paraphrase(rawText: String): List<String> {
        val apiKey = keySource.currentKey()
        if (apiKey.isBlank()) throw ParaphraseException("No Anthropic API key configured", ParaphraseFailure.NO_KEY)

        val payload = AnthropicRequest(
            model = model,
            maxTokens = 1024,
            system = Prompts.SYSTEM,
            messages = listOf(AnthropicMessage("user", Prompts.userMessage(rawText))),
            outputConfig = AnthropicOutputConfig("low")
        )

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .post(
                HttpClients.json.encodeToString(AnthropicRequest.serializer(), payload)
                    .toRequestBody(HttpClients.jsonMediaType)
            )
            .build()

        val body = client.awaitResponseBody(request, id.displayName)
        val response = runCatching {
            HttpClients.json.decodeFromString(AnthropicResponse.serializer(), body)
        }.getOrElse { throw ParaphraseException("Could not read Anthropic response", ParaphraseFailure.BAD_RESPONSE, it) }

        if (response.stopReason == "refusal") {
            val category = response.stopDetails?.category ?: "unspecified"
            throw ParaphraseException("Anthropic declined this message ($category)", ParaphraseFailure.REFUSED)
        }

        val content = response.content
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("")

        if (content.isBlank()) throw ParaphraseException("Anthropic response contained no text", ParaphraseFailure.BAD_RESPONSE)

        return SuggestionParser.parse(content)
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MODEL = "claude-opus-5"
    }
}
