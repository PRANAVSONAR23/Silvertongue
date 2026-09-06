package com.silvertongue.paraphraser.paraphrase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePreferences(
    private val active: ProviderId,
    private val keys: Map<ProviderId, String>
) : ProviderPreferences {
    override suspend fun currentProvider() = active
    override suspend fun keyFor(provider: ProviderId) = keys[provider].orEmpty()
}

private class FakeProvider(
    override val id: ProviderId,
    private val result: Result<List<String>>
) : ParaphraseProvider {
    var callCount = 0
        private set

    override suspend fun paraphrase(rawText: String): List<String> {
        callCount++
        return result.getOrThrow()
    }
}

private fun failing(id: ProviderId, failure: ParaphraseFailure) =
    FakeProvider(id, Result.failure(ParaphraseException("boom", failure)))

private fun succeeding(id: ProviderId, vararg suggestions: String) =
    FakeProvider(id, Result.success(suggestions.toList()))

class ParaphraseRepositoryTest {

    private val bothKeys = mapOf(ProviderId.GROQ to "gk", ProviderId.GEMINI to "mk")

    @Test
    fun `uses the active provider and does not touch the others`() = runTest {
        val groq = succeeding(ProviderId.GROQ, "fixed")
        val gemini = succeeding(ProviderId.GEMINI, "other")
        val repository = ParaphraseRepository(
            FakePreferences(ProviderId.GROQ, bothKeys),
            mapOf(ProviderId.GROQ to groq, ProviderId.GEMINI to gemini)
        )

        val outcome = repository.paraphrase("raw text").getOrThrow()

        assertEquals(listOf("fixed"), outcome.suggestions)
        assertEquals(ProviderId.GROQ, outcome.provider)
        assertFalse(outcome.usedFallback)
        assertEquals(1, groq.callCount)
        assertEquals(0, gemini.callCount)
    }

    @Test
    fun `falls back when the active provider is rate limited`() = runTest {
        val groq = failing(ProviderId.GROQ, ParaphraseFailure.RATE_LIMITED)
        val gemini = succeeding(ProviderId.GEMINI, "rescued")
        val repository = ParaphraseRepository(
            FakePreferences(ProviderId.GROQ, bothKeys),
            mapOf(ProviderId.GROQ to groq, ProviderId.GEMINI to gemini)
        )

        val outcome = repository.paraphrase("raw text").getOrThrow()

        assertEquals(listOf("rescued"), outcome.suggestions)
        assertEquals(ProviderId.GEMINI, outcome.provider)
        assertTrue(outcome.usedFallback)
        assertEquals(1, gemini.callCount)
    }

    @Test
    fun `falls back on transport, server and model errors`() = runTest {
        val retryable = listOf(
            ParaphraseFailure.TRANSPORT,
            ParaphraseFailure.SERVER_ERROR,
            ParaphraseFailure.MODEL_UNAVAILABLE
        )
        retryable.forEach { failure ->
            val gemini = succeeding(ProviderId.GEMINI, "rescued")
            val repository = ParaphraseRepository(
                FakePreferences(ProviderId.GROQ, bothKeys),
                mapOf(ProviderId.GROQ to failing(ProviderId.GROQ, failure), ProviderId.GEMINI to gemini)
            )

            assertTrue("$failure should fall back", repository.paraphrase("raw").getOrThrow().usedFallback)
        }
    }

    @Test
    fun `does not fall back when the key is missing or the reply was malformed`() = runTest {
        listOf(ParaphraseFailure.NO_KEY, ParaphraseFailure.BAD_RESPONSE, ParaphraseFailure.REFUSED)
            .forEach { failure ->
                val gemini = succeeding(ProviderId.GEMINI, "should not be used")
                val repository = ParaphraseRepository(
                    FakePreferences(ProviderId.GROQ, bothKeys),
                    mapOf(
                        ProviderId.GROQ to failing(ProviderId.GROQ, failure),
                        ProviderId.GEMINI to gemini
                    )
                )

                assertTrue("$failure should surface", repository.paraphrase("raw").isFailure)
                assertEquals("$failure must not call the fallback", 0, gemini.callCount)
            }
    }

    @Test
    fun `skips a fallback provider that has no key`() = runTest {
        val gemini = succeeding(ProviderId.GEMINI, "unreachable")
        val repository = ParaphraseRepository(
            FakePreferences(ProviderId.GROQ, mapOf(ProviderId.GROQ to "gk")),
            mapOf(
                ProviderId.GROQ to failing(ProviderId.GROQ, ParaphraseFailure.RATE_LIMITED),
                ProviderId.GEMINI to gemini
            )
        )

        val result = repository.paraphrase("raw")

        assertTrue(result.isFailure)
        assertEquals(0, gemini.callCount)
    }

    @Test
    fun `reports the original error when every provider fails`() = runTest {
        val repository = ParaphraseRepository(
            FakePreferences(ProviderId.GROQ, bothKeys),
            mapOf(
                ProviderId.GROQ to failing(ProviderId.GROQ, ParaphraseFailure.RATE_LIMITED),
                ProviderId.GEMINI to failing(ProviderId.GEMINI, ParaphraseFailure.SERVER_ERROR)
            )
        )

        val error = repository.paraphrase("raw").exceptionOrNull() as ParaphraseException

        assertEquals(ParaphraseFailure.RATE_LIMITED, error.failure)
    }

    @Test
    fun `rejects blank input without calling any provider`() = runTest {
        val groq = succeeding(ProviderId.GROQ, "never")
        val repository = ParaphraseRepository(
            FakePreferences(ProviderId.GROQ, bothKeys),
            mapOf(ProviderId.GROQ to groq)
        )

        val error = repository.paraphrase("   ").exceptionOrNull() as ParaphraseException

        assertEquals(ParaphraseFailure.EMPTY_INPUT, error.failure)
        assertEquals(0, groq.callCount)
    }
}
