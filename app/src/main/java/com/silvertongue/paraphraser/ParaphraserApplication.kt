package com.silvertongue.paraphraser

import android.app.Application

class ParaphraserApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.install(this)
    }
}
