package com.voiceprompter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxExtractorTest {

    /** Builds a minimal .docx in memory around the given document.xml body. */
    private fun docx(documentXml: String): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    private fun body(inner: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>$inner</w:body>
        </w:document>
    """.trimIndent()

    @Test
    fun `extracts paragraph text`() {
        val xml = body(
            """
            <w:p><w:r><w:t>First paragraph.</w:t></w:r></w:p>
            <w:p><w:r><w:t>Second paragraph.</w:t></w:r></w:p>
            """
        )
        assertEquals("First paragraph.\nSecond paragraph.", DocxExtractor.extractText(docx(xml)))
    }

    @Test
    fun `joins multiple runs within one paragraph`() {
        // Word splits a sentence across runs whenever formatting changes mid-line.
        val xml = body(
            """<w:p><w:r><w:t>Grace </w:t></w:r><w:r><w:t>and </w:t></w:r><w:r><w:t>peace.</w:t></w:r></w:p>"""
        )
        assertEquals("Grace and peace.", DocxExtractor.extractText(docx(xml)))
    }

    @Test
    fun `line breaks inside a paragraph are preserved`() {
        val xml = body(
            """<w:p><w:r><w:t>Line one</w:t><w:br/><w:t>Line two</w:t></w:r></w:p>"""
        )
        assertEquals("Line one\nLine two", DocxExtractor.extractText(docx(xml)))
    }

    @Test
    fun `runs of empty paragraphs collapse to a single blank line`() {
        val xml = body(
            """
            <w:p><w:r><w:t>Heading</w:t></w:r></w:p>
            <w:p/>
            <w:p/>
            <w:p/>
            <w:p><w:r><w:t>Body</w:t></w:r></w:p>
            """
        )
        assertEquals("Heading\n\nBody", DocxExtractor.extractText(docx(xml)))
    }

    @Test
    fun `non-run markup such as formatting is ignored`() {
        val xml = body(
            """<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Bold text</w:t></w:r></w:p>"""
        )
        assertEquals("Bold text", DocxExtractor.extractText(docx(xml)))
    }

    @Test
    fun `a zip without a document entry is rejected`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("notes.txt"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }
        assertThrows(DocxExtractor.NotADocxException::class.java) {
            DocxExtractor.extractText(ByteArrayInputStream(out.toByteArray()))
        }
    }

    @Test
    fun `tab characters survive`() {
        val xml = body("""<w:p><w:r><w:t>A</w:t><w:tab/><w:t>B</w:t></w:r></w:p>""")
        assertTrue(DocxExtractor.extractText(docx(xml)).contains('\t'))
    }
}
