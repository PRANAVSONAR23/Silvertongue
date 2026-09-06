package com.silvertongue.paraphraser.paraphrase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SuggestionParserTest {

    @Test
    fun `parses a clean envelope`() {
        val output = """{"suggestions": ["Can you send that file?", "Could you send me the file?"]}"""
        assertEquals(
            listOf("Can you send that file?", "Could you send me the file?"),
            SuggestionParser.parse(output)
        )
    }

    @Test
    fun `strips markdown fences`() {
        val output = "```json\n{\"suggestions\": [\"Are you free later?\"]}\n```"
        assertEquals(listOf("Are you free later?"), SuggestionParser.parse(output))
    }

    @Test
    fun `ignores preamble and trailing prose`() {
        val output = "Sure! Here you go:\n{\"suggestions\": [\"On my way.\"]}\nHope that helps."
        assertEquals(listOf("On my way."), SuggestionParser.parse(output))
    }

    @Test
    fun `falls back to a bare array`() {
        val output = """["I'll call you tonight.", "Let me call you tonight."]"""
        assertEquals(
            listOf("I'll call you tonight.", "Let me call you tonight."),
            SuggestionParser.parse(output)
        )
    }

    @Test
    fun `drops blanks and duplicates and caps at three`() {
        val output = """{"suggestions": ["one", "one", "  ", "two", "three", "four"]}"""
        assertEquals(listOf("one", "two", "three"), SuggestionParser.parse(output))
    }

    @Test
    fun `throws on malformed json`() {
        assertThrows(ParaphraseException::class.java) {
            SuggestionParser.parse("the model just rambled without any json")
        }
    }

    @Test
    fun `throws when every suggestion is blank`() {
        assertThrows(ParaphraseException::class.java) {
            SuggestionParser.parse("""{"suggestions": ["", "   "]}""")
        }
    }
}
