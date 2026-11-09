package com.shrivatsav.cardamom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentEngineTest {

    @Test
    fun testCleanTitle() {
        assertEquals("My Passport Scan", DocumentEngine.cleanTitle("My_Passport_Scan.pdf"))
        assertEquals("Invoice March 2026", DocumentEngine.cleanTitle("Invoice-March-2026.docx"))
        assertEquals("report", DocumentEngine.cleanTitle("report.doc"))
        assertEquals("notes", DocumentEngine.cleanTitle("notes.txt"))
        assertEquals("Document", DocumentEngine.cleanTitle(""))
        assertEquals("Document", DocumentEngine.cleanTitle(".pdf"))
    }

    @Test
    fun testDetectFormatByMagicBytes() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cardamom_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // 1. PDF
            val pdfFile = File(tempDir, "sample_no_ext")
            pdfFile.writeBytes("%PDF-1.7\n%some pdf data".toByteArray())
            assertEquals(DocumentEngine.DocumentFormat.PDF, DocumentEngine.detectFormat(pdfFile))

            // 2. JPEG
            val jpgFile = File(tempDir, "photo_no_ext")
            jpgFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0))
            assertEquals(DocumentEngine.DocumentFormat.IMAGE, DocumentEngine.detectFormat(jpgFile))

            // 3. PNG
            val pngFile = File(tempDir, "image_no_ext")
            pngFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0, 0, 0, 0))
            assertEquals(DocumentEngine.DocumentFormat.IMAGE, DocumentEngine.detectFormat(pngFile))

            // 4. DOC (OLE2 header)
            val docFile = File(tempDir, "word_no_ext")
            docFile.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0, 0, 0, 0))
            assertEquals(DocumentEngine.DocumentFormat.DOC, DocumentEngine.detectFormat(docFile))

            // 5. DOCX (Zip with word/document.xml)
            val docxFile = File(tempDir, "word_doc.docx")
            createSampleDocx(docxFile, "Test Title", "This is a test paragraph inside word document.")
            assertEquals(DocumentEngine.DocumentFormat.DOCX, DocumentEngine.detectFormat(docxFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDetectFormatByExtensionAndMime() {
        val dummyFile = File("non_existent_file.xyz")

        // Extensions
        assertEquals(DocumentEngine.DocumentFormat.PDF, DocumentEngine.detectFormat(dummyFile, fileName = "doc.pdf"))
        assertEquals(DocumentEngine.DocumentFormat.DOCX, DocumentEngine.detectFormat(dummyFile, fileName = "doc.docx"))
        assertEquals(DocumentEngine.DocumentFormat.DOC, DocumentEngine.detectFormat(dummyFile, fileName = "doc.doc"))
        assertEquals(DocumentEngine.DocumentFormat.TEXT, DocumentEngine.detectFormat(dummyFile, fileName = "doc.txt"))
        assertEquals(DocumentEngine.DocumentFormat.TEXT, DocumentEngine.detectFormat(dummyFile, fileName = "doc.rtf"))
        assertEquals(DocumentEngine.DocumentFormat.TEXT, DocumentEngine.detectFormat(dummyFile, fileName = "doc.csv"))
        assertEquals(DocumentEngine.DocumentFormat.IMAGE, DocumentEngine.detectFormat(dummyFile, fileName = "doc.png"))
        assertEquals(DocumentEngine.DocumentFormat.IMAGE, DocumentEngine.detectFormat(dummyFile, fileName = "doc.jpg"))

        // MIME types
        assertEquals(DocumentEngine.DocumentFormat.PDF, DocumentEngine.detectFormat(dummyFile, mimeType = "application/pdf"))
        assertEquals(DocumentEngine.DocumentFormat.DOCX, DocumentEngine.detectFormat(dummyFile, mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(DocumentEngine.DocumentFormat.DOC, DocumentEngine.detectFormat(dummyFile, mimeType = "application/msword"))
        assertEquals(DocumentEngine.DocumentFormat.TEXT, DocumentEngine.detectFormat(dummyFile, mimeType = "text/plain"))
        assertEquals(DocumentEngine.DocumentFormat.IMAGE, DocumentEngine.detectFormat(dummyFile, mimeType = "image/jpeg"))
    }

    @Test
    fun testProcessedDocumentToWalletDocument() {
        val processed = DocumentEngine.ProcessedDocument(
            id = "test-doc-123",
            title = "Boarding Pass",
            docType = "Ticket",
            uriPath = "/data/user/0/com.shrivatsav.cardamom/files/documents/doc_test-doc-123_page_0.jpg",
            mimeType = "application/pdf",
            pageCount = 3,
            pagePaths = listOf(
                "/data/user/0/com.shrivatsav.cardamom/files/documents/doc_test-doc-123_page_0.jpg",
                "/data/user/0/com.shrivatsav.cardamom/files/documents/doc_test-doc-123_page_1.jpg",
                "/data/user/0/com.shrivatsav.cardamom/files/documents/doc_test-doc-123_page_2.jpg"
            ),
            pdfPath = "/data/user/0/com.shrivatsav.cardamom/files/documents/doc_test-doc-123.pdf",
            fileSize = 45000L,
            bitmaps = emptyList(),
            extractedText = "Flight AI 101 Gate 4B"
        )

        val doc = processed.toWalletDocument()
        assertEquals("test-doc-123", doc.id)
        assertEquals("Boarding Pass", doc.title)
        assertEquals("Ticket", doc.docType)
        assertEquals("application/pdf", doc.mimeType)
        assertEquals(3, doc.pageCount)
        assertEquals(3, doc.pagePaths.size)
        assertEquals("/data/user/0/com.shrivatsav.cardamom/files/documents/doc_test-doc-123.pdf", doc.pdfPath)
        assertEquals(45000L, doc.fileSize)
    }

    @Test
    fun testDocxZipInspection() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cardamom_docx_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val docxFile = File(tempDir, "contract.docx")
            createSampleDocx(docxFile, "Employment Agreement", "This agreement is entered into between Employer and Employee.")
            assertTrue(docxFile.exists())
            assertTrue(docxFile.length() > 0)

            val format = DocumentEngine.detectFormat(docxFile)
            assertEquals(DocumentEngine.DocumentFormat.DOCX, format)
        } finally {
            tempDir.deleteRecursively()
        }
    }
    @Test
    fun testEmptyAndShortFilesDetectGracefully() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cardamom_empty_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val emptyFile = File(tempDir, "empty.bin")
            emptyFile.writeBytes(byteArrayOf())
            assertEquals(DocumentEngine.DocumentFormat.UNKNOWN, DocumentEngine.detectFormat(emptyFile))

            val shortFile = File(tempDir, "short.bin")
            shortFile.writeBytes(byteArrayOf(1, 2))
            assertEquals(DocumentEngine.DocumentFormat.UNKNOWN, DocumentEngine.detectFormat(shortFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTextDocumentFormattingAndReading() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cardamom_txt_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val textFile = File(tempDir, "meeting_notes.txt")
            val content = "# Meeting Notes\n\nDiscussion about project rollout.\n\n- Milestone 1\n- Milestone 2\n\nNext steps."
            textFile.writeText(content, Charsets.UTF_8)

            val format = DocumentEngine.detectFormat(textFile)
            assertEquals(DocumentEngine.DocumentFormat.TEXT, format)

            val (bitmaps, extractedText) = DocumentEngine.renderTextPages(textFile, "Meeting Notes")
            assertTrue(extractedText.contains("Meeting Notes"))
            assertTrue(extractedText.contains("Milestone 1"))
            if (bitmaps.isNotEmpty()) {
                assertEquals(1200, bitmaps[0].width)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDocxRenderingAndTextExtraction() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cardamom_render_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val docxFile = File(tempDir, "agreement.docx")
            createSampleDocx(docxFile, "Service Contract", "Parties agree to the terms and conditions outlined herein.")

            val (bitmaps, extractedText) = DocumentEngine.renderDocxPages(docxFile, "Service Contract")
            assertTrue("Extracted text should contain title", extractedText.contains("Service Contract"))
            assertTrue("Extracted text should contain body", extractedText.contains("Parties agree to the terms"))
            if (bitmaps.isNotEmpty()) {
                assertEquals(1200, bitmaps[0].width)
                assertEquals(1697, bitmaps[0].height)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProtectedPdfPlaceholderBitmap() {
        val bmp = DocumentEngine.renderProtectedPdfPlaceholder("Secure Document", 204800L)
        if (bmp != null) {
            assertEquals(1200, bmp.width)
            assertEquals(1697, bmp.height)
        }
    }
    private fun createSampleDocx(file: File, title: String, body: String) {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
        <w:p>
            <w:pPr>
                <w:pStyle w:val="Heading1"/>
            </w:pPr>
            <w:r>
                <w:t>$title</w:t>
            </w:r>
        </w:p>
        <w:p>
            <w:r>
                <w:t>$body</w:t>
            </w:r>
        </w:p>
    </w:body>
</w:document>"""

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write("<Types></Types>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(xml.toByteArray())
            zos.closeEntry()
        }
    }
}
