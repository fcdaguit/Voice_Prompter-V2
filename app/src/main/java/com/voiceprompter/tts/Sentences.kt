package com.voiceprompter.tts

import java.text.BreakIterator
import java.util.Locale

/**
 * Splits a script into the units the prompter speaks and highlights.
 *
 * Uses [BreakIterator] rather than a regex so decimals, quotation marks and
 * non-Latin punctuation behave sensibly, and because it is available on both
 * Android and the JVM, which keeps this unit-testable.
 *
 * [BreakIterator] does, however, break after an abbreviation: "See Mr. Smith
 * tomorrow." comes back as two sentences. In a teleprompter that is worse than
 * it sounds, because each unit is spoken as a separate utterance and the engine
 * inserts a pause between them. A merge pass re-joins those fragments.
 */
object Sentences {

    /** TTS engines truncate very long utterances; keep each chunk well under the limit. */
    private const val MAX_CHARS = 500

    /**
     * Abbreviations that end in a period without ending a sentence. Not
     * exhaustive, and deliberately weighted toward the words that show up in
     * sermons, lessons and business talks.
     */
    private val ABBREVIATIONS = setOf(
        // titles
        "mr.", "mrs.", "ms.", "dr.", "prof.", "rev.", "fr.", "sr.", "jr.", "st.",
        "hon.", "gov.", "sen.", "rep.", "capt.", "sgt.", "ptr.", "bro.", "sis.",
        // references and scripture
        "ch.", "chap.", "v.", "vv.", "cf.", "ff.", "vol.", "no.", "pp.", "p.",
        // general
        "e.g.", "i.e.", "etc.", "vs.", "approx.", "dept.", "est.", "fig.",
        "inc.", "ltd.", "min.", "max.", "a.m.", "p.m."
    )

    fun split(text: String, locale: Locale = Locale.getDefault()): List<String> {
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<String>()

        // Paragraphs first, so a blank line always starts a new prompt unit.
        for (paragraph in text.split(Regex("\n\\s*\n"))) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue

            for (sentence in mergeFragments(rawSegments(trimmed, locale))) {
                // `+=` with a collection delegates to addAll, so the list stays flat.
                result += hardWrap(sentence)
            }
        }

        return result
    }

    private fun rawSegments(text: String, locale: Locale): List<String> {
        val segments = mutableListOf<String>()
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)

        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val segment = text.substring(start, end).trim()
            if (segment.isNotEmpty()) segments += segment
            start = end
            end = iterator.next()
        }
        return segments
    }

    /** Re-joins a segment onto the previous one when the break was spurious. */
    private fun mergeFragments(segments: List<String>): List<String> {
        if (segments.size < 2) return segments

        val merged = mutableListOf<String>()
        for (segment in segments) {
            val previous = merged.lastOrNull()
            if (previous != null && endsWithAbbreviation(previous)) {
                merged[merged.lastIndex] = "$previous $segment"
            } else {
                merged += segment
            }
        }
        return merged
    }

    private fun endsWithAbbreviation(segment: String): Boolean {
        val lastToken = segment.trimEnd()
            .substringAfterLast(' ')
            .substringAfterLast('\n')
            .lowercase()

        if (lastToken in ABBREVIATIONS) return true

        // A lone initial, as in "C. S. Lewis" or "Rom. 8".
        return lastToken.length == 2 && lastToken[0].isLetter() && lastToken[1] == '.'
    }

    /**
     * Breaks an over-long "sentence" (an unpunctuated paragraph, say) at a word
     * boundary so no single utterance exceeds [MAX_CHARS].
     */
    private fun hardWrap(sentence: String): List<String> {
        if (sentence.length <= MAX_CHARS) return listOf(sentence)

        val chunks = mutableListOf<String>()
        val builder = StringBuilder()
        for (word in sentence.split(' ')) {
            if (builder.isNotEmpty() && builder.length + 1 + word.length > MAX_CHARS) {
                chunks += builder.toString()
                builder.setLength(0)
            }
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(word)
        }
        if (builder.isNotEmpty()) chunks += builder.toString()
        return chunks
    }
}
