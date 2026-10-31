package com.shrivatsav.cardamom

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File

object Share {
    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "card" }

    fun render(ctx: Context, card: WalletCard, includeCode: Boolean, includeCodeText: Boolean = false): Bitmap {
        val w = 1080
        val code = if (includeCode && card.code.isNotBlank())
            Codes.bitmap(card.code, card.format, if (card.format.is2D) 700 else 900, if (card.format.is2D) 700 else 300) else null
        val h = if (code != null) (if (card.format.is2D) 1200 else 820) else 380
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val sansFlex = try {
            ResourcesCompat.getFont(ctx, R.font.google_sans_flex)
        } catch (e: Exception) {
            null
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = C.CardColors[card.colorIndex.mod(C.CardColors.size)].toArgb()
        cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 80f, 80f, p)

        p.color = C.StaticInk.toArgb()
        p.textSize = 76f
        p.typeface = sansFlex ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.fontVariationSettings = "'wght' 700, 'ROND' 100"
        p.textAlign = Paint.Align.LEFT
        cv.drawText(card.name.take(24), 64f, 150f, p)

        p.textSize = 44f
        p.typeface = sansFlex ?: Typeface.DEFAULT
        p.fontVariationSettings = "'wght' 500, 'ROND' 100"
        val sub = listOf(card.category, card.points).filter { it.isNotBlank() }.joinToString("  \u00B7  ")
        cv.drawText(sub, 64f, 220f, p)

        if (code != null) {
            p.color = android.graphics.Color.WHITE
            val box = RectF(48f, 280f, w - 48f, h - 48f)
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
                var textSize = 40f
                p.textSize = textSize
                while (p.measureText(displayText) > (box.width() - 64f) && textSize > 20f) {
                    textSize -= 2f
                    p.textSize = textSize
                }
                cv.drawText(displayText, box.centerX(), box.bottom - 40f, p)
            }
        }
        return bmp
    }

    private fun shareDir(ctx: Context) = File(ctx.cacheDir, "shared").also { it.mkdirs() }

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

    fun send(ctx: Context, f: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share card"))
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
