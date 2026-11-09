package com.shrivatsav.cardamom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Universal document ingestion, format detection, pagination, splitting, and rendering engine.
 * Handles PDFs (including Google Drive virtual files and encrypted fallbacks), DOCX, DOC,
 * plain text, and image documents.
 */
object DocumentEngine {
    private const val MAX_ZIP_ENTRY_BYTES = 20_000_000L
    private const val MAX_ZIP_TOTAL_BYTES = 100_000_000L
    private const val MAX_ZIP_ENTRIES = 200
    private const val MAX_EMBEDDED_IMAGES = 20
    private const val MAX_IMPORT_BYTES = 50_000_000L

    /** Bounded read: throws if more than [max] bytes are available. */
    fun InputStream.readBounded(max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            require(total <= max) { "entry exceeds size limit" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    enum class DocumentFormat {
        PDF,
        DOCX,
        DOC,
        TEXT,
        IMAGE,
        UNKNOWN
    }

    data class ProcessedDocument(
        val id: String,
        val title: String,
        val docType: String,
        val uriPath: String,
        val mimeType: String,
        val pageCount: Int,
        val pagePaths: List<String>,
        val pdfPath: String?,
        val fileSize: Long,
        val bitmaps: List<Bitmap>,
        val extractedText: String = "",
    ) {
        fun toWalletDocument(): WalletDocument = WalletDocument(
            id = id,
            title = title,
            docType = docType,
            uriPath = uriPath,
            mimeType = mimeType,
            pageCount = pageCount.coerceAtLeast(1),
            pagePaths = pagePaths,
            pdfPath = pdfPath,
            fileSize = fileSize,
        )
    }

    /**
     * Reliably opens an InputStream for ANY URI: local files, cloud providers (Google Drive),
     * download providers, or virtual documents (e.g. Google Docs/Sheets on Drive).
     */
    fun openUriStream(ctx: Context, uri: Uri): InputStream? {
        // 1. Try standard openInputStream
        try {
            val stream = ctx.contentResolver.openInputStream(uri)
            if (stream != null) return stream
        } catch (_: Exception) {}

        // 2. If it is a virtual document (Google Docs, Sheets, Slides on Drive), request PDF export stream
        try {
            val pfd = ctx.contentResolver.openTypedAssetFileDescriptor(uri, "application/pdf", null)
                ?: ctx.contentResolver.openTypedAssetFileDescriptor(uri, "*/*", null)
            if (pfd != null) {
                return pfd.createInputStream()
            }
        } catch (_: Exception) {}

        // 3. Query stream types supported by the provider
        try {
            val streamTypes = ctx.contentResolver.getStreamTypes(uri, "*/*")
            if (!streamTypes.isNullOrEmpty()) {
                val preferred = streamTypes.firstOrNull { it.contains("pdf", ignoreCase = true) }
                    ?: streamTypes.firstOrNull { it.contains("officedocument", ignoreCase = true) || it.contains("word", ignoreCase = true) }
                    ?: streamTypes.firstOrNull { it.contains("text", ignoreCase = true) }
                    ?: streamTypes.first()
                val pfd = ctx.contentResolver.openTypedAssetFileDescriptor(uri, preferred, null)
                if (pfd != null) return pfd.createInputStream()
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Determines true file format from magic bytes, filename extension, and MIME type.
     */
    fun detectFormat(file: File, mimeType: String? = null, fileName: String? = null): DocumentFormat {
        val name = fileName ?: file.name
        val ext = name.substringAfterLast('.', "").lowercase()

        // 1. Magic bytes inspection
        if (file.exists() && file.length() >= 4) {
            val header = ByteArray(8)
            val readCount = try {
                file.inputStream().use { it.read(header) }
            } catch (_: Exception) { 0 }

            if (readCount >= 4) {
                // %PDF
                if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
                    return DocumentFormat.PDF
                }
                // PK\x03\x04 (ZIP / DOCX / Office Open XML)
                if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                    if (isDocxZip(file)) return DocumentFormat.DOCX
                }
                // OLE2 Compound Document Header: \xD0\xCF\x11\xE0 (legacy Word .doc)
                if (header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() && header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()) {
                    return DocumentFormat.DOC
                }
                // JPEG
                if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                    return DocumentFormat.IMAGE
                }
                // PNG
                if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
                    return DocumentFormat.IMAGE
                }
                // WEBP: RIFF....WEBP
                if (readCount >= 8 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()) {
                    return DocumentFormat.IMAGE
                }
            }
        }

        // 2. Extension matching
        when (ext) {
            "pdf" -> return DocumentFormat.PDF
            "docx" -> return DocumentFormat.DOCX
            "doc" -> return DocumentFormat.DOC
            "txt", "rtf", "csv", "log", "md" -> return DocumentFormat.TEXT
            "jpg", "jpeg", "png", "webp", "bmp", "heic" -> return DocumentFormat.IMAGE
        }

        // 3. MIME type matching
        val mime = (mimeType ?: "").lowercase()
        return when {
            mime.contains("pdf") -> DocumentFormat.PDF
            mime.contains("wordprocessingml") || mime.contains("officedocument") -> DocumentFormat.DOCX
            mime.contains("msword") -> DocumentFormat.DOC
            mime.contains("text") -> DocumentFormat.TEXT
            mime.contains("image") -> DocumentFormat.IMAGE
            else -> DocumentFormat.UNKNOWN
        }
    }

    private fun isDocxZip(file: File): Boolean {
        return try {
            ZipInputStream(java.io.ByteArrayInputStream(VaultCrypto.readDecryptedBytes(file))).use { zip ->
                var entry = zip.nextEntry
                var found = false
                while (entry != null) {
                    if (entry.name.startsWith("word/") || entry.name == "[Content_Types].xml") {
                        found = true
                        break
                    }
                    entry = zip.nextEntry
                }
                found
            }
        } catch (_: Exception) { false }
    }

    /**
     * Cleans up display filenames into pleasant document titles.
     */
    fun cleanTitle(rawName: String): String {
        val base = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
        return base.replace('_', ' ').replace('-', ' ').trim().ifBlank { "Document" }
    }

    /**
     * Imports ANY document URI, copies it into storage, splits it into pages,
     * renders high-resolution page JPEG images, and compiles a clean PDF.
     */
    suspend fun importDocument(ctx: Context, uri: Uri, customId: String? = null): ProcessedDocument? {
        val id = customId ?: UUID.randomUUID().toString()
        val displayName = Files.displayName(ctx, uri)
        val title = cleanTitle(displayName)
        val docDir = Files.dir(ctx, "documents")

        // 1. Read input stream into temp file
        val tempFile = File(docDir, "temp_import_$id")
        try {
            val stream = openUriStream(ctx, uri) ?: return null
            stream.use { input ->
                tempFile.outputStream().use { output -> Files.copyBounded(input, output) }
            }
        } catch (e: Exception) {
            tempFile.delete()
            return null
        }

        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            return null
        }

        val mime = ctx.contentResolver.getType(uri) ?: ""
        val format = detectFormat(tempFile, mime, displayName)

        // 2. Process based on format
        val finalPdfFile = File(docDir, "doc_$id.pdf")
        val pageBitmaps = mutableListOf<Bitmap>()
        var extractedText = ""

        when (format) {
            DocumentFormat.PDF -> {
                // Move/copy temp to final PDF destination
                VaultCrypto.writeEncryptedBytes(finalPdfFile, tempFile.readBytes())
                tempFile.delete()
                val pages = renderPdfPages(finalPdfFile)
                pageBitmaps.addAll(pages)
            }
            DocumentFormat.DOCX -> {
                val (pages, text) = renderDocxPages(tempFile, title)
                tempFile.delete()
                pageBitmaps.addAll(pages)
                extractedText = text
                compilePagesToPdf(pageBitmaps, finalPdfFile)
                try { VaultCrypto.writeEncryptedBytes(finalPdfFile, finalPdfFile.readBytes()) } catch (_: Exception) {}
            }
            DocumentFormat.DOC -> {
                val (pages, text) = renderLegacyDocPages(tempFile, title)
                tempFile.delete()
                pageBitmaps.addAll(pages)
                extractedText = text
                compilePagesToPdf(pageBitmaps, finalPdfFile)
                try { VaultCrypto.writeEncryptedBytes(finalPdfFile, finalPdfFile.readBytes()) } catch (_: Exception) {}
            }
            DocumentFormat.TEXT -> {
                val (pages, text) = renderTextPages(tempFile, title)
                tempFile.delete()
                pageBitmaps.addAll(pages)
                extractedText = text
                compilePagesToPdf(pageBitmaps, finalPdfFile)
                try { VaultCrypto.writeEncryptedBytes(finalPdfFile, finalPdfFile.readBytes()) } catch (_: Exception) {}
            }
            DocumentFormat.IMAGE -> {
                val bmp = VaultCrypto.decodeBitmapFile(tempFile)
                tempFile.delete()
                if (bmp != null) {
                    pageBitmaps.add(bmp)
                    compilePagesToPdf(listOf(bmp), finalPdfFile)
                    try { VaultCrypto.writeEncryptedBytes(finalPdfFile, finalPdfFile.readBytes()) } catch (_: Exception) {}
                }
            }
            DocumentFormat.UNKNOWN -> {
                // Attempt PDF first, then fallback to text
                var pages = renderPdfPages(tempFile)
                if (pages.isNotEmpty()) {
                    VaultCrypto.writeEncryptedBytes(finalPdfFile, tempFile.readBytes())
                    tempFile.delete()
                    pageBitmaps.addAll(pages)
                } else {
                    val (textPages, text) = renderTextPages(tempFile, title)
                    tempFile.delete()
                    pageBitmaps.addAll(textPages)
                    extractedText = text
                    compilePagesToPdf(pageBitmaps, finalPdfFile)
                try { VaultCrypto.writeEncryptedBytes(finalPdfFile, finalPdfFile.readBytes()) } catch (_: Exception) {}
                }
            }
        }

        // If no bitmaps could be rendered, generate a guaranteed fallback cover page
        if (pageBitmaps.isEmpty()) {
            val fallback = renderProtectedPdfPlaceholder(title, finalPdfFile.length().coerceAtLeast(1024L))
            if (fallback != null) {
                pageBitmaps.add(fallback)
                if (!finalPdfFile.exists() || finalPdfFile.length() == 0L) {
                    compilePagesToPdf(pageBitmaps, finalPdfFile)
                try { VaultCrypto.writeEncryptedBytes(finalPdfFile, finalPdfFile.readBytes()) } catch (_: Exception) {}
                }
            }
        }

        // 3. Save each page bitmap as JPEG
        val pagePaths = mutableListOf<String>()
        pageBitmaps.forEachIndexed { i, bmp ->
            val pageFile = File(docDir, "doc_${id}_page_$i.jpg")
            try {
                VaultCrypto.compressEncryptedBitmap(pageFile, bmp, Bitmap.CompressFormat.JPEG, 90)
                pagePaths.add(pageFile.absolutePath)
            } catch (_: Exception) {}
        }

        val primaryPath = pagePaths.firstOrNull() ?: finalPdfFile.absolutePath
        val finalMime = when (format) {
            DocumentFormat.PDF -> "application/pdf"
            DocumentFormat.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            DocumentFormat.DOC -> "application/msword"
            DocumentFormat.TEXT -> "text/plain"
            DocumentFormat.IMAGE -> "image/jpeg"
            DocumentFormat.UNKNOWN -> "application/pdf"
        }

        return ProcessedDocument(
            id = id,
            title = title,
            docType = "Document",
            uriPath = primaryPath,
            mimeType = finalMime,
            pageCount = pagePaths.size.coerceAtLeast(1),
            pagePaths = pagePaths,
            pdfPath = finalPdfFile.absolutePath,
            fileSize = if (finalPdfFile.exists()) finalPdfFile.length() else pagePaths.sumOf { File(it).length() },
            bitmaps = pageBitmaps,
            extractedText = extractedText,
        )
    }

    /**
     * Renders up to maxPages from a PDF file using PdfRenderer with safe memory bounds
     * and a pure white background.
     */
    fun renderPdfPages(pdfFile: File, maxPages: Int = 25): List<Bitmap> {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return emptyList()
        val pages = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        // PdfRenderer needs a plain file: decrypt encrypted vault PDFs to a
        // private temp file, render, then delete it.
        var tempPlain: File? = null
        val readableFile: File = try {
            if (VaultCrypto.isEncryptedFile(pdfFile)) {
                val tmp = File(pdfFile.parentFile, "render_tmp_${System.nanoTime()}.pdf")
                tmp.writeBytes(VaultCrypto.readDecryptedBytes(pdfFile))
                tempPlain = tmp
                tmp
            } else pdfFile
        } catch (_: Exception) { pdfFile }
        try {
            pfd = ParcelFileDescriptor.open(readableFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                val count = minOf(renderer.pageCount, maxPages)
                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    try {
                        val pageW = page.width.coerceAtLeast(1)
                        val pageH = page.height.coerceAtLeast(1)
                        val targetWidth = 1200
                        val targetHeight = ((targetWidth.toFloat() / pageW) * pageH).toInt().coerceIn(600, 2400)

                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bmp)
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        } catch (_: Exception) {
            if (pages.isEmpty()) {
                val fallback = renderProtectedPdfPlaceholder(pdfFile.name, pdfFile.length())
                if (fallback != null) pages.add(fallback)
            }
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
            try { tempPlain?.delete() } catch (_: Exception) {}
        }
        return pages
    }

    /**
     * Parses a Word .docx file (XML structure) and paginates it onto crisp A4 page Bitmaps.
     */
    private fun decodeBoundedBitmap(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        require(opts.outWidth.toLong() * opts.outHeight <= 40_000_000L) { "image too large" }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun renderDocxPages(docxFile: File, title: String, maxPages: Int = 25): Pair<List<Bitmap>, String> {
        val elements = mutableListOf<DocElement>()
        val textBuilder = StringBuilder()
        val embeddedImages = mutableListOf<Bitmap>()

        try {
            ZipInputStream(java.io.ByteArrayInputStream(VaultCrypto.readDecryptedBytes(docxFile))).use { zip ->
                var entry = zip.nextEntry
                var docXmlBytes: ByteArray? = null
                var totalBytes = 0L
                var entryCount = 0
                while (entry != null) {
                    entryCount++
                    require(entryCount <= MAX_ZIP_ENTRIES) { "too many zip entries" }
                    var entryBytes = 0L
                    val counting = object : java.io.FilterInputStream(zip) {
                        override fun read(): Int {
                            val r = super.read()
                            if (r >= 0) { entryBytes++; totalBytes++ }
                            return r
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val n = super.read(b, off, len)
                            if (n > 0) { entryBytes += n; totalBytes += n }
                            return n
                        }
                    }
                    if (entry.name == "word/document.xml") {
                        docXmlBytes = counting.readBounded(MAX_ZIP_ENTRY_BYTES)
                    } else if (entry.name.startsWith("word/media/") &&
                        (entry.name.endsWith(".png", true) || entry.name.endsWith(".jpg", true) || entry.name.endsWith(".jpeg", true))) {
                        if (embeddedImages.size < MAX_EMBEDDED_IMAGES) {
                            val bytes = counting.readBounded(MAX_ZIP_ENTRY_BYTES)
                            decodeBoundedBitmap(bytes)?.let { embeddedImages.add(it) }
                        }
                    }
                    require(totalBytes <= MAX_ZIP_TOTAL_BYTES) { "zip expands beyond size limit" }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }

                if (docXmlBytes != null) {
                    val factory = DocumentBuilderFactory.newInstance()
                    // XXE hardening: never resolve external entities/DTDs from
                    // attacker-controlled DOCX content.
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                    factory.isXIncludeAware = false
                    factory.isExpandEntityReferences = false
                    factory.isNamespaceAware = true
                    val builder = factory.newDocumentBuilder()
                    val doc = builder.parse(ByteArrayInputStream(docXmlBytes))
                    doc.documentElement.normalize()

                    val bodyNodes = doc.getElementsByTagName("w:body").item(0)?.childNodes
                    if (bodyNodes != null) {
                        for (i in 0 until bodyNodes.length) {
                            val node = bodyNodes.item(i)
                            if (node.nodeType == Node.ELEMENT_NODE) {
                                val elem = node as Element
                                when (elem.tagName) {
                                    "w:p" -> parseParagraph(elem, elements, textBuilder)
                                    "w:tbl" -> parseTable(elem, elements, textBuilder)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If XML parsing fails, extract any raw strings from the file
            val rawText = extractRawTextFromStream(docxFile)
            if (rawText.isNotBlank()) {
                elements.add(DocElement.Paragraph(rawText))
                textBuilder.append(rawText)
            }
        }

        // Append embedded images to the document
        embeddedImages.forEach { bmp ->
            elements.add(DocElement.ImageElement(bmp))
        }

        if (elements.isEmpty()) {
            elements.add(DocElement.Heading(title, 1))
            elements.add(DocElement.Paragraph("Empty or protected Word document."))
        }

        val bitmaps = paginateElements(elements, title, maxPages)
        return Pair(bitmaps, textBuilder.toString())
    }

    private fun parseParagraph(elem: Element, out: MutableList<DocElement>, textOut: StringBuilder) {
        val pPr = elem.getElementsByTagName("w:pPr").item(0) as? Element
        val pStyle = pPr?.getElementsByTagName("w:pStyle")?.item(0) as? Element
        val styleVal = pStyle?.getAttribute("w:val") ?: ""
        val isBullet = (pPr?.getElementsByTagName("w:numPr")?.length ?: 0) > 0

        val tNodes = elem.getElementsByTagName("w:t")
        val sb = StringBuilder()
        for (i in 0 until tNodes.length) {
            sb.append(tNodes.item(i).textContent)
        }
        val text = sb.toString().trim()
        if (text.isBlank()) return

        textOut.append(text).append("\n")

        val lowerStyle = styleVal.lowercase()
        when {
            lowerStyle.contains("title") -> out.add(DocElement.Heading(text, 1))
            lowerStyle.contains("heading 1") || lowerStyle.contains("heading1") -> out.add(DocElement.Heading(text, 1))
            lowerStyle.contains("heading 2") || lowerStyle.contains("heading2") -> out.add(DocElement.Heading(text, 2))
            lowerStyle.contains("heading") -> out.add(DocElement.Heading(text, 3))
            isBullet -> out.add(DocElement.Paragraph(text, isBullet = true))
            else -> out.add(DocElement.Paragraph(text))
        }
    }

    private fun parseTable(elem: Element, out: MutableList<DocElement>, textOut: StringBuilder) {
        val rows = elem.getElementsByTagName("w:tr")
        for (i in 0 until rows.length) {
            val row = rows.item(i) as? Element ?: continue
            val cells = row.getElementsByTagName("w:tc")
            val cellTexts = mutableListOf<String>()
            for (j in 0 until cells.length) {
                val cell = cells.item(j) as? Element ?: continue
                val text = cell.textContent.trim()
                cellTexts.add(text)
                textOut.append(text).append("  ")
            }
            textOut.append("\n")
            if (cellTexts.any { it.isNotBlank() }) {
                out.add(DocElement.TableRow(cellTexts))
            }
        }
    }

    /**
     * Renders plain text files onto paginated A4 page Bitmaps.
     */
    fun renderTextPages(file: File, title: String, maxPages: Int = 25): Pair<List<Bitmap>, String> {
        val lines = try {
            String(VaultCrypto.readDecryptedBytes(file), Charsets.UTF_8).lines()
        } catch (_: Exception) {
            try { String(VaultCrypto.readDecryptedBytes(file), Charsets.ISO_8859_1).lines() } catch (_: Exception) { emptyList() }
        }

        val elements = mutableListOf<DocElement>()
        elements.add(DocElement.Heading(title, 1))

        val currentParagraph = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentParagraph.isNotEmpty()) {
                    elements.add(DocElement.Paragraph(currentParagraph.toString()))
                    currentParagraph.setLength(0)
                }
            } else if (trimmed.startsWith("# ")) {
                if (currentParagraph.isNotEmpty()) {
                    elements.add(DocElement.Paragraph(currentParagraph.toString()))
                    currentParagraph.setLength(0)
                }
                elements.add(DocElement.Heading(trimmed.removePrefix("# ").trim(), 1))
            } else if (trimmed.startsWith("## ")) {
                if (currentParagraph.isNotEmpty()) {
                    elements.add(DocElement.Paragraph(currentParagraph.toString()))
                    currentParagraph.setLength(0)
                }
                elements.add(DocElement.Heading(trimmed.removePrefix("## ").trim(), 2))
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (currentParagraph.isNotEmpty()) {
                    elements.add(DocElement.Paragraph(currentParagraph.toString()))
                    currentParagraph.setLength(0)
                }
                elements.add(DocElement.Paragraph(trimmed.substring(2).trim(), isBullet = true))
            } else {
                if (currentParagraph.isNotEmpty()) currentParagraph.append(" ")
                currentParagraph.append(trimmed)
            }
        }
        if (currentParagraph.isNotEmpty()) {
            elements.add(DocElement.Paragraph(currentParagraph.toString()))
        }

        val allText = lines.joinToString("\n")
        val bitmaps = paginateElements(elements, title, maxPages)
        return Pair(bitmaps, allText)
    }

    /**
     * Extracts readable strings from legacy binary .doc files and paginates them.
     */
    fun renderLegacyDocPages(file: File, title: String, maxPages: Int = 25): Pair<List<Bitmap>, String> {
        val extracted = extractRawTextFromStream(file)
        val elements = mutableListOf<DocElement>()
        elements.add(DocElement.Heading(title, 1))

        val paragraphs = extracted.split("\n\n").map { it.trim() }.filter { it.length > 3 }
        for (p in paragraphs) {
            elements.add(DocElement.Paragraph(p))
        }

        if (elements.size <= 1) {
            elements.add(DocElement.Paragraph("Word 97-2004 Document. To view full original layout, export or share file."))
        }

        val bitmaps = paginateElements(elements, title, maxPages)
        return Pair(bitmaps, extracted)
    }

    private fun extractRawTextFromStream(file: File): String {
        return try {
            val bytes = VaultCrypto.readDecryptedBytes(file)
            val sb = StringBuilder()
            var i = 0
            while (i < bytes.size - 1) {
                // Check for UTF-16LE characters (common in MS Office binary format)
                val c = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                if (c in 32..126 || c == 10 || c == 13) {
                    sb.append(c.toChar())
                    i += 2
                } else if (bytes[i] in 32..126 || bytes[i] == 10.toByte() || bytes[i] == 13.toByte()) {
                    sb.append(bytes[i].toInt().toChar())
                    i++
                } else {
                    if (sb.isNotEmpty() && sb.last() != '\n') sb.append(' ')
                    i++
                }
            }
            sb.toString().replace(Regex("[ \t]+"), " ").trim()
        } catch (_: Exception) { "" }
    }

    sealed class DocElement {
        data class Heading(val text: String, val level: Int) : DocElement()
        data class Paragraph(val text: String, val isBullet: Boolean = false) : DocElement()
        data class TableRow(val cells: List<String>) : DocElement()
        data class ImageElement(val bitmap: Bitmap) : DocElement()
    }

    /**
     * Lays out elements onto A4 pages (1200 x 1697) using StaticLayout and Canvas.
     */
    private fun paginateElements(elements: List<DocElement>, title: String, maxPages: Int): List<Bitmap> {
        val pageWidth = 1200
        val pageHeight = 1697
        val marginH = 90
        val contentWidth = pageWidth - (marginH * 2)
        val startY = 140f
        val maxY = 1530f

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 44f
            color = Color.parseColor("#161616")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val heading1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 36f
            color = Color.parseColor("#161616")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val heading2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            color = Color.parseColor("#333333")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            color = Color.parseColor("#222222")
            typeface = Typeface.DEFAULT
        }
        val tablePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            color = Color.parseColor("#1E1E1E")
            typeface = Typeface.DEFAULT
        }
        val tableBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D0D0D0")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val tableBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F7F7")
            style = Paint.Style.FILL
        }
        val headerFooterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = Color.parseColor("#888888")
            typeface = Typeface.DEFAULT
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 2f
        }

        val pages = mutableListOf<Bitmap>()
        var currentBmp = try {
            Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) { null }
        if (currentBmp == null) return emptyList()

        try { currentBmp.eraseColor(Color.WHITE) } catch (_: Throwable) {}
        var canvas: Canvas
        try {
            canvas = Canvas(currentBmp)
        } catch (_: Throwable) {
            return emptyList()
        }
        var currentY = startY

        fun finishPageAndStartNew() {
            pages.add(currentBmp!!)
            if (pages.size >= maxPages) return
            val nextBmp = try {
                Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            } catch (_: Throwable) { null } ?: return
            try { nextBmp.eraseColor(Color.WHITE) } catch (_: Throwable) {}
            currentBmp = nextBmp
            canvas = Canvas(nextBmp)
            currentY = startY
        }
        for (el in elements) {
            if (pages.size >= maxPages) break

            when (el) {
                is DocElement.Heading -> {
                    val paint = when (el.level) {
                        1 -> if (currentY == startY) titlePaint else heading1Paint
                        else -> heading2Paint
                    }
                    val layout = makeLayout(el.text, paint, contentWidth)
                    val required = layout.height + 24f

                    if (currentY + required > maxY && currentY > startY) {
                        finishPageAndStartNew()
                        if (pages.size >= maxPages) break
                    }

                    canvas.save()
                    canvas.translate(marginH.toFloat(), currentY)
                    layout.draw(canvas)
                    canvas.restore()
                    currentY += required
                }
                is DocElement.Paragraph -> {
                    val indent = if (el.isBullet) 36 else 0
                    val width = contentWidth - indent
                    val layout = makeLayout(el.text, bodyPaint, width)
                    val required = layout.height + 16f

                    if (currentY + required > maxY && currentY > startY) {
                        finishPageAndStartNew()
                        if (pages.size >= maxPages) break
                    }

                    if (el.isBullet) {
                        canvas.drawText("•", marginH.toFloat() + 8f, currentY + 22f, bodyPaint)
                    }

                    canvas.save()
                    canvas.translate((marginH + indent).toFloat(), currentY)
                    layout.draw(canvas)
                    canvas.restore()
                    currentY += required
                }
                is DocElement.TableRow -> {
                    if (el.cells.isEmpty()) continue
                    val cellWidth = contentWidth / el.cells.size
                    val cellLayouts = el.cells.map { makeLayout(it, tablePaint, cellWidth - 16) }
                    val maxCellHeight = (cellLayouts.maxOfOrNull { it.height } ?: 30) + 16f

                    if (currentY + maxCellHeight > maxY && currentY > startY) {
                        finishPageAndStartNew()
                        if (pages.size >= maxPages) break
                    }

                    val rowRect = RectF(marginH.toFloat(), currentY, (marginH + contentWidth).toFloat(), currentY + maxCellHeight)
                    canvas.drawRect(rowRect, tableBgPaint)
                    canvas.drawRect(rowRect, tableBorderPaint)

                    for (cIdx in el.cells.indices) {
                        val cx = marginH + (cIdx * cellWidth)
                        if (cIdx > 0) {
                            canvas.drawLine(cx.toFloat(), currentY, cx.toFloat(), currentY + maxCellHeight, tableBorderPaint)
                        }
                        canvas.save()
                        canvas.translate(cx + 8f, currentY + 8f)
                        cellLayouts[cIdx].draw(canvas)
                        canvas.restore()
                    }
                    currentY += maxCellHeight + 4f
                }
                is DocElement.ImageElement -> {
                    val img = el.bitmap
                    val maxImgWidth = contentWidth.toFloat()
                    val maxImgHeight = 600f
                    val scale = minOf(maxImgWidth / img.width, maxImgHeight / img.height, 1f)
                    val destW = (img.width * scale).toInt()
                    val destH = (img.height * scale).toInt()
                    val required = destH + 20f

                    if (currentY + required > maxY && currentY > startY) {
                        finishPageAndStartNew()
                        if (pages.size >= maxPages) break
                    }

                    val destX = marginH + ((contentWidth - destW) / 2f)
                    canvas.drawBitmap(img, null, RectF(destX, currentY, destX + destW, currentY + destH), null)
                    currentY += required
                }
            }
        }

        if (!pages.contains(currentBmp)) {
            pages.add(currentBmp)
        }

        // Add headers and footers to all pages
        val total = pages.size
        for (idx in pages.indices) {
            val pCanvas = Canvas(pages[idx])
            // Header
            val headerText = if (title.length > 50) title.take(47) + "..." else title
            pCanvas.drawText(headerText, marginH.toFloat(), 70f, headerFooterPaint)
            pCanvas.drawLine(marginH.toFloat(), 90f, (pageWidth - marginH).toFloat(), 90f, dividerPaint)

            // Footer
            val pageText = "Page ${idx + 1} of $total"
            val textWidth = headerFooterPaint.measureText(pageText)
            pCanvas.drawText(pageText, (pageWidth - textWidth) / 2f, (pageHeight - 60).toFloat(), headerFooterPaint)
            val brand = "Cardamom Vault"
            pCanvas.drawText(brand, (pageWidth - marginH - headerFooterPaint.measureText(brand)), (pageHeight - 60).toFloat(), headerFooterPaint)
        }

        return pages
    }

    private fun makeLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        val safeWidth = width.coerceAtLeast(100)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.3f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, safeWidth, Layout.Alignment.ALIGN_NORMAL, 1.3f, 0f, true)
        }
    }

    /**
     * Compiles page bitmaps into a multi-page PDF document.
     */
    fun compilePagesToPdf(bitmaps: List<Bitmap>, destFile: File): Boolean {
        if (bitmaps.isEmpty()) return false
        val pdfDoc = PdfDocument()
        try {
            for (i in bitmaps.indices) {
                val bmp = bitmaps[i]
                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdfDoc.finishPage(page)
            }
            destFile.outputStream().use { pdfDoc.writeTo(it) }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            try { pdfDoc.close() } catch (_: Exception) {}
        }
    }

    /**
     * Generates a clean, beautifully styled document cover/placeholder
     * if a file cannot be visually decompiled (e.g. password protected or encrypted).
     */
    fun renderProtectedPdfPlaceholder(title: String, fileSize: Long): Bitmap? {
        val w = 1200
        val h = 1697
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) { null } ?: return null
        try { bmp.eraseColor(Color.parseColor("#FDFCF9")) } catch (_: Throwable) {}
        val canvas: Canvas
        try {
            canvas = Canvas(bmp)
        } catch (_: Throwable) { return null }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Document Outer Frame Border
        val frameRect = RectF(60f, 60f, (w - 60).toFloat(), (h - 60).toFloat())
        p.color = Color.parseColor("#E4DFD5")
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        canvas.drawRoundRect(frameRect, 28f, 28f, p)

        // Cardamom Brand Header
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#888888")
        p.textSize = 24f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CARDAMOM DOCUMENT VAULT", 100f, 130f, p)

        // Document Icon Card
        val cardRect = RectF(100f, 180f, (w - 100).toFloat(), 560f)
        p.color = Color.parseColor("#F2EFE9")
        canvas.drawRoundRect(cardRect, 32f, 32f, p)

        // Title
        p.color = Color.parseColor("#161616")
        p.textSize = 52f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val cleanName = cleanTitle(title)
        val titleText = if (cleanName.length > 32) cleanName.take(30) + "..." else cleanName
        canvas.drawText(titleText, 140f, 290f, p)

        // Subtitle / Format
        p.color = Color.parseColor("#666666")
        p.textSize = 28f
        p.typeface = Typeface.DEFAULT
        val sizeKb = (fileSize / 1024L).coerceAtLeast(1L)
        val sizeStr = if (sizeKb >= 1024) String.format("%.1f MB", sizeKb / 1024f) else "$sizeKb KB"
        canvas.drawText("Document  ·  $sizeStr", 140f, 350f, p)

        // Security / Status Badge
        val badgeRect = RectF(140f, 400f, 460f, 460f)
        p.color = Color.parseColor("#E6F4EA")
        canvas.drawRoundRect(badgeRect, 16f, 16f, p)
        p.color = Color.parseColor("#137333")
        p.textSize = 24f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("VERIFIED STORAGE", 165f, 440f, p)

        // Content description
        p.color = Color.parseColor("#444444")
        p.textSize = 28f
        p.typeface = Typeface.DEFAULT
        canvas.drawText("This file is safely encrypted and stored on your device.", 100f, 660f, p)
        canvas.drawText("Tap Share to export the original file or view in external apps.", 100f, 710f, p)

        // Document placeholder preview lines
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DED9CE")
            style = Paint.Style.FILL
        }
        var lineY = 820f
        val lineLengths = listOf(0.85f, 0.95f, 0.9f, 0.6f, 0.8f, 0.95f, 0.75f, 0.5f)
        for (fraction in lineLengths) {
            val r = RectF(100f, lineY, 100f + ((w - 200) * fraction), lineY + 14f)
            canvas.drawRoundRect(r, 6f, 6f, linePaint)
            lineY += 46f
        }

        return bmp
    }

    /**
     * Resolves all pages for a WalletDocument, recovering and rendering from disk if needed.
     */
    fun renderDocumentPages(ctx: Context, doc: WalletDocument): List<Bitmap> {
        // 1. Try from existing pagePaths
        val fromPagePaths = doc.pagePaths.mapNotNull { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0) VaultCrypto.decodeBitmapFile(f) else null
        }
        if (fromPagePaths.isNotEmpty()) return fromPagePaths

        // 2. Try from doc.pdfPath
        val pdfFile = doc.pdfPath?.let { File(it) }
        if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
            val pages = renderPdfPages(pdfFile)
            if (pages.isNotEmpty()) return pages
        }

        // 3. Try from doc.uriPath
        val uriFile = File(doc.uriPath)
        if (uriFile.exists() && uriFile.length() > 0) {
            val pages = renderFilePages(uriFile)
            if (pages.isNotEmpty()) return pages
        }

        // 4. Try scanning documents directory for doc_${id}_page_*.jpg
        val docDir = Files.dir(ctx, "documents")
        val jpegs: Array<File> = docDir.listFiles { f: File ->
            f.name.startsWith("doc_${doc.id}_page_") &&
            (f.name.endsWith(".jpg", ignoreCase = true) || f.name.endsWith(".png", ignoreCase = true)) &&
            f.length() > 0
        } ?: emptyArray()

        val fromDisk = jpegs.sortedBy { it.name }.mapNotNull { VaultCrypto.decodeBitmapFile(it) }
        if (fromDisk.isNotEmpty()) return fromDisk

        // 5. Guaranteed fallback: return clean styled document cover
        renderProtectedPdfPlaceholder(doc.title.ifBlank { doc.docType }, doc.fileSize)?.let {
            return listOf(it)
        }
        return emptyList()
    }
    /**
     * Renders any file (PDF, DOCX, DOC, Text, Image) into a list of page Bitmaps.
     */
    fun renderFilePages(file: File): List<Bitmap> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val format = detectFormat(file)
        return when (format) {
            DocumentFormat.PDF -> renderPdfPages(file)
            DocumentFormat.DOCX -> renderDocxPages(file, cleanTitle(file.name)).first
            DocumentFormat.DOC -> renderLegacyDocPages(file, cleanTitle(file.name)).first
            DocumentFormat.TEXT -> renderTextPages(file, cleanTitle(file.name)).first
            DocumentFormat.IMAGE -> {
                val b = VaultCrypto.decodeBitmapFile(file)
                if (b != null) listOf(b) else emptyList()
            }
            DocumentFormat.UNKNOWN -> {
                val p = renderPdfPages(file)
                if (p.isNotEmpty()) p else renderTextPages(file, cleanTitle(file.name)).first
            }
        }
    }

    /**
     * Renders a single page for a document, using pagePaths or dynamically from file.
     */
    fun renderSinglePage(ctx: Context, doc: WalletDocument, pageIndex: Int): Bitmap? {
        val path = doc.pagePaths.getOrNull(pageIndex)
        if (path != null) {
            val f = File(path)
            if (f.exists() && f.length() > 0) {
                val b = VaultCrypto.decodeBitmapFile(f)
                if (b != null) return b
            }
        }
        val all = renderDocumentPages(ctx, doc)
        return all.getOrNull(pageIndex) ?: all.firstOrNull()
    }
}
