package com.voiceprompter.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SentencesTest {

    @Test
    fun `splits a paragraph into sentences`() {
        val result = Sentences.split(
            "Good morning. Today we look at Romans 8. Let us begin.",
            Locale.ENGLISH
        )
        assertEquals(
            listOf("Good morning.", "Today we look at Romans 8.", "Let us begin."),
            result
        )
    }

    @Test
    fun `blank input yields nothing`() {
        assertTrue(Sentences.split("   \n\n  ", Locale.ENGLISH).isEmpty())
    }

    @Test
    fun `paragraphs are kept separate even without terminal punctuation`() {
        val result = Sentences.split("First point\n\nSecond point", Locale.ENGLISH)
        assertEquals(listOf("First point", "Second point"), result)
    }

    @Test
    fun `single newlines do not split a sentence`() {
        val result = Sentences.split("A sentence that\nwraps across lines.", Locale.ENGLISH)
        assertEquals(1, result.size)
    }

    @Test
    fun `an over-long unpunctuated run is wrapped at word boundaries`() {
        val long = (1..400).joinToString(" ") { "word" }
        val result = Sentences.split(long, Locale.ENGLISH)
        assertTrue("expected more than one chunk", result.size > 1)
        assertTrue("no chunk may exceed the utterance limit", result.all { it.length <= 500 })
        // Nothing is lost in the wrapping.
        assertEquals(long, result.joinToString(" "))
    }

    // BreakIterator breaks after an abbreviation on its own. These cases cover the
    // merge pass that undoes that, since a stray "See Mr." would be spoken as its
    // own utterance with an audible pause after it.

    @Test
    fun `abbreviations do not split mid-sentence`() {
        assertEquals(
            listOf("See Mr. Smith tomorrow.", "Then go home."),
            Sentences.split("See Mr. Smith tomorrow. Then go home.", Locale.ENGLISH)
        )
    }

    @Test
    fun `initials stay with their name`() {
        assertEquals(
            listOf("Read C. S. Lewis on this.", "He is clear."),
            Sentences.split("Read C. S. Lewis on this. He is clear.", Locale.ENGLISH)
        )
    }

    @Test
    fun `scripture abbreviation stays joined`() {
        assertEquals(
            listOf("Turn to Rom. 8 now.", "Read it."),
            Sentences.split("Turn to Rom. 8 now. Read it.", Locale.ENGLISH)
        )
    }

    @Test
    fun `e g does not break the sentence`() {
        assertEquals(1, Sentences.split("Bring something, e.g. a notebook.", Locale.ENGLISH).size)
    }

    @Test
    fun `ordinary sentences still split`() {
        assertEquals(3, Sentences.split("One. Two. Three.", Locale.ENGLISH).size)
    }

    @Test
    fun `decimals are not sentence ends`() {
        assertEquals(1, Sentences.split("It rose by 3.5 percent.", Locale.ENGLISH).size)
    }
}
