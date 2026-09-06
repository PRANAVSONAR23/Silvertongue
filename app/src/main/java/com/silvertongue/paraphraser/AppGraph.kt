package com.silvertongue.paraphraser

import android.content.Context
import com.silvertongue.paraphraser.data.SettingsRepository
import com.silvertongue.paraphraser.paraphrase.ParaphraseRepository

object AppGraph {

    private lateinit var applicationContext: Context

    fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    val settings: SettingsRepository by lazy { SettingsRepository(applicationContext) }

    val paraphraseRepository: ParaphraseRepository by lazy { ParaphraseRepository(settings) }
}
