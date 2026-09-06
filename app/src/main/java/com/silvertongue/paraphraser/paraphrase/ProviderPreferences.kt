package com.silvertongue.paraphraser.paraphrase

interface ProviderPreferences {
    suspend fun currentProvider(): ProviderId
    suspend fun keyFor(provider: ProviderId): String
}
