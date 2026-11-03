package com.shrivatsav.cardamom

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.util.ArrayList

object Share {
    fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "card" }

    fun safeDocFileName(doc: WalletDocument): String {
        val raw = doc.title.ifBlank { doc.docType }.ifBlank { "Document" }
        val clean = raw.trim().replace(Regex("[\\\\/:*?\"<>|]+"), "_").trim()
        return if (clean.isEmpty()) "Document" else clean
    }

    private fun isBackField(f: CardField): Boolean {
        val l = f.label.lowercase()
        return l.contains("dob") || l.contains("birth") || l.contains("blood") || l.contains("address") ||
                l.contains("mobile") || l.contains("phone") || l.contains("contact") || l.contains("emergency") ||
                l.contains("transfer") || l.contains("return") || l.contains("cvv") || l.contains("security") ||
                l.contains("pin")
    }

    fun cardDetailsText(card: WalletCard): String = buildString {
        val title = card.name.ifBlank { card.category.ifBlank { "Card" } }
        appendLine(title)
        if (card.issuer.isNotBlank() && !card.issuer.equals(title, ignoreCase = true)) {
            appendLine("Issuer: ${card.issuer}")
        }
        if (card.category.isNotBlank() && !card.category.equals(title, ignoreCase = true)) {
            appendLine("Category: ${card.category}")
        }
        if (card.code.isNotBlank()) {
            appendLine("Code (${card.format.label}): ${card.code}")
        }
        if (card.fields.isNotEmpty()) {
            appendLine("\nDetails:")
            card.fields.forEach { f ->
                if (f.label.isNotBlank() && f.value.isNotBlank()) {
                    appendLine("• ${f.label}: ${f.value}")
                }
            }
        }
        if (card.points.isNotBlank()) appendLine("Points: ${card.points}")
        if (card.status.isNotBlank()) appendLine("Status: ${card.status}")
        if (card.website.isNotBlank()) appendLine("Website: ${card.website}")
    }.trim()

    fun documentDetailsText(doc: WalletDocument): String = buildString {
        val title = doc.title.ifBlank { doc.docType }.ifBlank { "Document" }
        appendLine(title)
        if (doc.docType.isNotBlank() && !doc.docType.equals(title, ignoreCase = true)) {
            appendLine("Type: ${doc.docType}")
        }
        if (doc.issuer.isNotBlank()) {
            appendLine("Issuer: ${doc.issuer}")
        }
        if (doc.fields.isNotEmpty()) {
            appendLine("\nDetails:")
            doc.fields.forEach { f ->
                if (f.label.isNotBlank() && f.value.isNotBlank()) {
                    appendLine("• ${f.label}: ${f.value}")
                }
            }
        }
    }.trim()

    fun drawCardamomLogo(canvas: Canvas, rect: RectF) {
        val s = rect.width() / 120f
        val cx = rect.left
        val cy = rect.top
        val pivotX = cx + 60f * s
        val pivotY = cy + 62f * s

        // Mint card (-20 deg)
        canvas.save()
        canvas.rotate(-20f, pivotX, pivotY)
        val pMint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A8D8B4") }
        canvas.drawRoundRect(RectF(cx + 35f * s, cy + 26f * s, cx + 85f * s, cy + 94f * s), 10f * s, 10f * s, pMint)
        canvas.restore()

        // Orange card (-8 deg)
        canvas.save()
        canvas.rotate(-8f, pivotX, pivotY)
        val pOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F6B04B") }
        canvas.drawRoundRect(RectF(cx + 35f * s, cy + 26f * s, cx + 85f * s, cy + 94f * s), 10f * s, 10f * s, pOrange)
        canvas.restore()

        // Yellow card (+8 deg)
        canvas.save()
        canvas.rotate(8f, pivotX, pivotY)
        val pYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5C842") }
        canvas.drawRoundRect(RectF(cx + 35f * s, cy + 26f * s, cx + 85f * s, cy + 94f * s), 10f * s, 10f * s, pYellow)
        val pInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#161616") }
        canvas.drawCircle(cx + 47f * s, cy + 40f * s, 5f * s, pInk)
        listOf(43f to 3f, 49f to 2f, 53f to 4f, 60f to 2f, 65f to 3f, 71f to 2f).forEach { (x, w) ->
            canvas.drawRect(RectF(cx + x * s, cy + 68f * s, cx + (x + w) * s, cy + 86f * s), pInk)
        }
        canvas.restore()
    }

    fun drawCardamomOverlay(canvas: Canvas, width: Float, height: Float, ctx: Context) {
        val scale = (minOf(width, height) / 1000f).coerceIn(0.5f, 2.5f)
        val margin = 28f * scale
        val pillH = 46f * scale
        val textSize = 18f * scale
        val logoSize = 28f * scale
        val padX = 14f * scale
        val spacing = 10f * scale

        val sansFlex = try {
            ResourcesCompat.getFont(ctx, R.font.google_sans_flex)
        } catch (e: Exception) {
            null
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = sansFlex ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            fontVariationSettings = "'wght' 600, 'ROND' 100"
        }

        val text = "Shared from Cardamom"
        val textW = textPaint.measureText(text)
        val pillW = padX + logoSize + spacing + textW + padX

        val right = width - margin
        val bottom = height - margin
        val left = right - pillW
        val top = bottom - pillH
        val pillRect = RectF(left, top, right, bottom)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6161616")
        }
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
        }
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, borderPaint)

        val logoTop = top + (pillH - logoSize) / 2f
        val logoLeft = left + padX
        drawCardamomLogo(canvas, RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize))

        val textBaseline = top + (pillH / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        val textX = logoLeft + logoSize + spacing
        canvas.drawText(text, textX, textBaseline, textPaint)
    }

    fun render(ctx: Context, card: WalletCard, includeCode: Boolean, includeCodeText: Boolean = false): Bitmap {
        val w = 1080
        val code = if (includeCode && card.code.isNotBlank())
            Codes.bitmap(card.code, card.format, if (card.format.is2D) 700 else 900, if (card.format.is2D) 700 else 300) else null

        val sansFlex = try {
            ResourcesCompat.getFont(ctx, R.font.google_sans_flex)
        } catch (e: Exception) {
            null
        }

        val frontFields = card.fields.filterNot { isBackField(it) }
        val backFields = card.fields.filter { isBackField(it) }.ifEmpty {
            if (card.fields.size > 2) card.fields.drop(2) else emptyList()
        }

        // Calculate front height
        var frontContentH = 260f
        if (code != null) {
            frontContentH += if (card.format.is2D) 820f else 460f
        }
        if (frontFields.isNotEmpty()) {
            val rows = (frontFields.size + 1) / 2
            frontContentH += rows * 110f + 40f
        } else if (card.points.isNotBlank() || card.status.isNotBlank()) {
            frontContentH += 110f
        }
        val frontH = frontContentH.coerceAtLeast(420f)

        // Calculate back height
        var backContentH = 260f
        if (backFields.isNotEmpty()) {
            backContentH += backFields.size * 100f + 60f
        } else {
            backContentH += 220f
        }
        backContentH += 90f // space for overlay
        val backH = backContentH.coerceAtLeast(460f)

        val gap = 48f
        val totalH = (frontH + gap + backH).toInt()

        val bmp = Bitmap.createBitmap(w, totalH, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val cardColor = C.CardColors[card.colorIndex.mod(C.CardColors.size)].toArgb()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // ---------------- 1. FRONT CARD ----------------
        p.color = cardColor
        cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), frontH), 72f, 72f, p)

        // Front Title
        p.color = C.StaticInk.toArgb()
        p.textSize = 72f
        p.typeface = sansFlex ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.fontVariationSettings = "'wght' 700, 'ROND' 100"
        p.textAlign = Paint.Align.LEFT
        val primaryTitle = card.name.ifBlank { card.category.ifBlank { card.issuer.ifBlank { "Card" } } }
        cv.drawText(primaryTitle.take(28), 64f, 130f, p)

        // Front Subtitle
        p.textSize = 40f
        p.typeface = sansFlex ?: Typeface.DEFAULT
        p.fontVariationSettings = "'wght' 500, 'ROND' 100"
        val subList = mutableListOf<String>()
        if (card.issuer.isNotBlank() && !card.issuer.equals(primaryTitle, ignoreCase = true)) subList.add(card.issuer)
        if (card.category.isNotBlank() && !card.category.equals(primaryTitle, ignoreCase = true)) subList.add(card.category)
        val sub = subList.joinToString("  ·  ").ifBlank { card.category }
        cv.drawText(sub.take(40), 64f, 200f, p)

        var curY = 240f
        // Front Code box
        if (code != null) {
            val boxH = if (card.format.is2D) (if (includeCodeText) 820f else 760f) else (if (includeCodeText) 440f else 380f)
            val box = RectF(48f, curY, w - 48f, curY + boxH)
            p.color = Color.WHITE
            cv.drawRoundRect(box, 48f, 48f, p)

            val maxW = box.width() - 96f
            val maxH = if (includeCodeText) box.height() - 160f else box.height() - 96f
            val s = minOf(maxW / code.width, maxH / code.height)
            val dw = code.width * s
            val dh = code.height * s
            val left = box.centerX() - dw / 2
            val top = if (includeCodeText) box.top + 48f else box.centerY() - dh / 2
            cv.drawBitmap(code, null, RectF(left, top, left + dw, top + dh), null)

            if (includeCodeText) {
                p.color = C.StaticInk.toArgb()
                p.typeface = sansFlex ?: Typeface.DEFAULT
                p.fontVariationSettings = "'wght' 600, 'ROND' 100"
                p.textAlign = Paint.Align.CENTER
                val displayText = if (card.code.length > 55) card.code.take(52) + "..." else card.code
                var textSize = 38f
                p.textSize = textSize
                while (p.measureText(displayText) > (box.width() - 64f) && textSize > 18f) {
                    textSize -= 2f
                    p.textSize = textSize
                }
                cv.drawText(displayText, box.centerX(), box.bottom - 36f, p)
            }
            curY += boxH + 40f
        }

        // Front Fields / Attributes
        if (frontFields.isNotEmpty()) {
            val col1X = 64f
            val col2X = w / 2f + 20f
            frontFields.chunked(2).forEach { row ->
                val f1 = row[0]
                p.textAlign = Paint.Align.LEFT
                p.color = C.StaticInk.toArgb()
                p.alpha = 180
                p.textSize = 28f
                p.fontVariationSettings = "'wght' 600, 'ROND' 100"
                cv.drawText(f1.label.uppercase().take(22), col1X, curY, p)
                p.alpha = 255
                p.textSize = 38f
                p.fontVariationSettings = "'wght' 700, 'ROND' 100"
                cv.drawText(f1.value.take(24), col1X, curY + 46f, p)

                if (row.size > 1) {
                    val f2 = row[1]
                    p.alpha = 180
                    p.textSize = 28f
                    p.fontVariationSettings = "'wght' 600, 'ROND' 100"
                    cv.drawText(f2.label.uppercase().take(22), col2X, curY, p)
                    p.alpha = 255
                    p.textSize = 38f
                    p.fontVariationSettings = "'wght' 700, 'ROND' 100"
                    cv.drawText(f2.value.take(24), col2X, curY + 46f, p)
                }
                curY += 105f
            }
        } else if (card.points.isNotBlank() || card.status.isNotBlank()) {
            val col1X = 64f
            val col2X = w / 2f + 20f
            p.textAlign = Paint.Align.LEFT
            if (card.points.isNotBlank()) {
                p.color = C.StaticInk.toArgb()
                p.alpha = 180
                p.textSize = 28f
                cv.drawText("POINTS", col1X, curY, p)
                p.alpha = 255
                p.textSize = 42f
                p.fontVariationSettings = "'wght' 700, 'ROND' 100"
                cv.drawText(card.points, col1X, curY + 46f, p)
            }
            if (card.status.isNotBlank()) {
                p.color = C.StaticInk.toArgb()
                p.alpha = 180
                p.textSize = 28f
                cv.drawText("STATUS", col2X, curY, p)
                p.alpha = 255
                p.textSize = 42f
                p.fontVariationSettings = "'wght' 700, 'ROND' 100"
                cv.drawText(card.status, col2X, curY + 46f, p)
            }
        }

        // ---------------- 2. BACK CARD ----------------
        val backTop = frontH + gap
        p.color = cardColor
        p.alpha = 255
        cv.drawRoundRect(RectF(0f, backTop, w.toFloat(), backTop + backH), 72f, 72f, p)

        p.color = C.StaticInk.toArgb()
        p.alpha = 180
        p.textSize = 30f
        p.typeface = sansFlex ?: Typeface.DEFAULT
        p.fontVariationSettings = "'wght' 700, 'ROND' 100"
        p.textAlign = Paint.Align.LEFT
        cv.drawText("CARD DETAILS · BACK", 64f, backTop + 90f, p)

        p.alpha = 255
        p.textSize = 58f
        p.fontVariationSettings = "'wght' 700, 'ROND' 100"
        val backInst = card.issuer.ifBlank { card.name.ifBlank { "Institution" } }
        cv.drawText(backInst.take(30), 64f, backTop + 165f, p)

        var backCurY = backTop + 240f
        if (backFields.isNotEmpty()) {
            backFields.forEach { f ->
                p.alpha = 180
                p.textSize = 26f
                p.fontVariationSettings = "'wght' 600, 'ROND' 100"
                cv.drawText(f.label.uppercase().take(30), 64f, backCurY, p)
                p.alpha = 255
                p.textSize = 38f
                p.fontVariationSettings = "'wght' 700, 'ROND' 100"
                cv.drawText(f.value.take(45), 64f, backCurY + 44f, p)
                backCurY += 95f
            }
        } else {
            p.alpha = 180
            p.textSize = 26f
            p.fontVariationSettings = "'wght' 600, 'ROND' 100"
            cv.drawText("ISSUED BY", 64f, backCurY, p)
            p.alpha = 255
            p.textSize = 38f
            p.fontVariationSettings = "'wght' 700, 'ROND' 100"
            cv.drawText(card.issuer.ifBlank { "Cardamom Private Wallet" }.take(36), 64f, backCurY + 44f, p)

            backCurY += 100f
            p.alpha = 180
            p.textSize = 26f
            cv.drawText("SECURITY & VALIDITY", 64f, backCurY, p)
            p.alpha = 255
            p.textSize = 36f
            cv.drawText("Encrypted locally on device · Offline first", 64f, backCurY + 44f, p)
        }

        // Bottom right overlay inside the back card
        drawCardamomOverlay(cv, w.toFloat(), backTop + backH, ctx)

        return bmp
    }

    fun shareDir(ctx: Context) = File(ctx.cacheDir, "shared").also { it.mkdirs() }

    fun imageFile(ctx: Context, card: WalletCard, includeCode: Boolean, includeCodeText: Boolean = false): File {
        val f = File(shareDir(ctx), "cardamom_${safe(card.name)}.png")
        f.outputStream().use { render(ctx, card, includeCode, includeCodeText).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun cardFile(ctx: Context, card: WalletCard): File {
        val f = File(shareDir(ctx), "${safe(card.name)}.cardamom")
        f.writeBytes(Backup.build(listOf(card)))
        return f
    }

    fun send(
        ctx: Context,
        f: File,
        mime: String,
        subject: String? = null,
        text: String? = null,
        chooserTitle: String = "Share card",
    ) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, chooserTitle))
    }

    fun sendMultiple(
        ctx: Context,
        files: List<File>,
        mime: String,
        subject: String? = null,
        text: String? = null,
        chooserTitle: String = "Share card",
    ) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            send(ctx, files[0], mime, subject, text, chooserTitle)
            return
        }
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it) })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, chooserTitle))
    }

    fun documentPdfFile(ctx: Context, doc: WalletDocument): File {
        val f = File(shareDir(ctx), "${safeDocFileName(doc)}.pdf")
        val pdfDoc = PdfDocument()
        var pageNumber = 1

        val sansFlex = try {
            ResourcesCompat.getFont(ctx, R.font.google_sans_flex)
        } catch (e: Exception) {
            null
        }

        // 1. If document has fields, generate an elegant Document Details page
        if (doc.fields.isNotEmpty()) {
            val pw = 1200
            val ph = 1600
            val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, pageNumber++).create()
            val page = pdfDoc.startPage(pageInfo)
            val cv = page.canvas

            // Background
            cv.drawColor(Color.parseColor("#FDFCF9"))

            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // Top Banner: Title & Issuer
            p.color = Color.parseColor("#161616")
            p.textSize = 58f
            p.typeface = sansFlex ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.fontVariationSettings = "'wght' 700, 'ROND' 100"
            val docTitle = doc.title.ifBlank { doc.docType }.ifBlank { "Document" }
            cv.drawText(docTitle, 80f, 150f, p)

            p.textSize = 34f
            p.typeface = sansFlex ?: Typeface.DEFAULT
            p.fontVariationSettings = "'wght' 500, 'ROND' 100"
            p.color = Color.parseColor("#666666")
            val subLine = listOf(doc.docType, doc.issuer).filter { it.isNotBlank() }.joinToString("  ·  ")
            cv.drawText(subLine, 80f, 210f, p)

            // Divider line
            p.color = Color.parseColor("#E0DDD5")
            p.strokeWidth = 2f
            p.style = Paint.Style.STROKE
            cv.drawLine(80f, 250f, (pw - 80).toFloat(), 250f, p)
            p.style = Paint.Style.FILL

            // Details Card Container
            val cardTop = 300f
            val cardBottom = (cardTop + (doc.fields.size * 105f) + 60f).coerceAtMost(ph - 180f)
            val cardRect = RectF(80f, cardTop, (pw - 80).toFloat(), cardBottom)
            p.color = Color.parseColor("#F4F0E8")
            cv.drawRoundRect(cardRect, 32f, 32f, p)

            var fieldY = cardTop + 70f
            doc.fields.forEach { field ->
                if (fieldY + 70f < cardBottom) {
                    p.color = Color.parseColor("#706E6B")
                    p.textSize = 24f
                    p.fontVariationSettings = "'wght' 600, 'ROND' 100"
                    cv.drawText(field.label.uppercase().take(30), 120f, fieldY, p)

                    p.color = Color.parseColor("#161616")
                    p.textSize = 38f
                    p.fontVariationSettings = "'wght' 700, 'ROND' 100"
                    cv.drawText(field.value.take(45), 120f, fieldY + 44f, p)
                    fieldY += 100f
                }
            }

            // Bottom right overlay
            drawCardamomOverlay(cv, pw.toFloat(), ph.toFloat(), ctx)
            pdfDoc.finishPage(page)
        }

        // 2. Add each page image with the Cardamom overlay
        val images = getDocumentImages(ctx, doc)
        images.forEach { bmp ->
            val pw = bmp.width
            val ph = bmp.height
            val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, pageNumber++).create()
            val page = pdfDoc.startPage(pageInfo)
            val cv = page.canvas

            cv.drawBitmap(bmp, 0f, 0f, null)
            drawCardamomOverlay(cv, pw.toFloat(), ph.toFloat(), ctx)
            pdfDoc.finishPage(page)
        }

        // If document had no images and no fields, generate a placeholder page
        if (images.isEmpty() && doc.fields.isEmpty()) {
            val pw = 1200
            val ph = 1600
            val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, pageNumber++).create()
            val page = pdfDoc.startPage(pageInfo)
            val cv = page.canvas
            cv.drawColor(Color.parseColor("#FDFCF9"))

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.parseColor("#161616")
            p.textSize = 58f
            p.typeface = sansFlex ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val docTitle = doc.title.ifBlank { doc.docType }.ifBlank { "Document" }
            cv.drawText(docTitle, 80f, 150f, p)

            drawCardamomOverlay(cv, pw.toFloat(), ph.toFloat(), ctx)
            pdfDoc.finishPage(page)
        }

        f.outputStream().use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
        return f
    }

    fun sendDocument(ctx: Context, doc: WalletDocument) {
        try {
            val pdf = documentPdfFile(ctx, doc)
            if (!pdf.exists() || pdf.length() == 0L) {
                Toast.makeText(ctx, "Could not generate document PDF", Toast.LENGTH_SHORT).show()
                return
            }
            val title = doc.title.ifBlank { doc.docType }.ifBlank { "Document" }
            val details = documentDetailsText(doc)
            send(ctx, pdf, "application/pdf", subject = title, text = details, chooserTitle = "Share document")
        } catch (e: Exception) {
            Toast.makeText(ctx, "Error sharing document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveToGallery(ctx: Context, card: WalletCard, includeCode: Boolean, includeCodeText: Boolean = false) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cardamom_${safe(card.name)}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cardamom")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { Toast.makeText(ctx, "Couldn't save the image", Toast.LENGTH_SHORT).show(); return }
        ctx.contentResolver.openOutputStream(uri)?.use { render(ctx, card, includeCode, includeCodeText).compress(Bitmap.CompressFormat.PNG, 100, it) }
        Toast.makeText(ctx, "Saved to Pictures/Cardamom", Toast.LENGTH_SHORT).show()
    }

    fun openOriginal(ctx: Context, c: WalletCard) {
        val p = c.originalPath ?: return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(p))
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, c.originalMime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(i) } catch (e: Exception) { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }
}
