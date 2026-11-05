package com.voiceprompter.data

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Minimal plain-text extraction from a .docx, with no third-party dependency.
 *
 * A .docx is a ZIP; the body text lives in `word/document.xml`. We stream the
 * entry out and SAX-parse it, keeping only run text (`w:t`), tabs (`w:tab`),
 * line breaks (`w:br`) and paragraph boundaries (`w:p`).
 *
 * Formatting, images, tables-as-tables, headers, footnotes and comments are all
 * discarded. For a teleprompter script that is exactly what we want.
 *
 * `javax.xml.parsers` is chosen over Android's `XmlPullParser` so this class is
 * plain JVM code and can be unit-tested without an emulator.
 */
object DocxExtractor {

    private const val DOCUMENT_ENTRY = "word/document.xml"
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024

    class NotADocxException(message: String) : Exception(message)

    fun extractText(input: InputStream): String {
        val xml = readDocumentEntry(input)
            ?: throw NotADocxException("No $DOCUMENT_ENTRY entry - not a Word document")
        return parse(xml)
    }

    private fun readDocumentEntry(input: InputStream): ByteArray? {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name != DOCUMENT_ENTRY) {
                    zip.closeEntry()
                    continue
                }
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read <= 0) break
                    total += read
                    // Guard against a zip bomb in an untrusted file.
                    if (total > MAX_ENTRY_BYTES) {
                        throw NotADocxException("Document body is unexpectedly large")
                    }
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        }
    }

    private fun parse(xml: ByteArray): String {
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var inTextRun = false

        val handler = object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String,
                attributes: Attributes?
            ) {
                when (stripPrefix(qName)) {
                    "t" -> inTextRun = true
                    "tab" -> current.append('\t')
                    "br" -> current.append('\n')
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (stripPrefix(qName)) {
                    "t" -> inTextRun = false
                    "p" -> {
                        paragraphs += current.toString().trim()
                        current.setLength(0)
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inTextRun) current.appendRange(ch, start, start + length)
            }
        }

        val factory = SAXParserFactory.newInstance().apply {
            // word/document.xml is untrusted input: no external entities, no DTDs.
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
        }
        factory.newSAXParser().parse(InputSource(ByteArrayInputStream(xml)), handler)

        if (current.isNotEmpty()) paragraphs += current.toString().trim()

        // Collapse runs of empty paragraphs into a single blank line.
        return buildString {
            var lastWasBlank = true
            for (p in paragraphs) {
                if (p.isEmpty()) {
                    if (!lastWasBlank) append('\n')
                    lastWasBlank = true
                } else {
                    append(p).append('\n')
                    lastWasBlank = false
                }
            }
        }.trim()
    }

    private fun stripPrefix(qName: String): String =
        qName.substringAfterLast(':')
}
