package com.silvertongue.paraphraser.overlay

interface FocusedTextBridge {
    fun readFocusedText(): String?
    fun writeFocusedText(text: String): Boolean
}
