package com.shrivatsav.cardamom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.UUID

data class Detected(
    val card: WalletCard,
    val details: String,
    val codes: List<Pair<String, CodeFormat>>,
    val thumb: Bitmap?,
    val frontThumb: Bitmap? = null,
    val backThumb: Bitmap? = null,
)

data class ExtractedCardData(
    val issuer: String,
    val name: String,
    val category: String,
    val colorIndex: Int,
    val fields: List<CardField>,
    val primaryCode: String,
    val primaryFormat: CodeFormat,
)

data class AnalysisOutcome(
    val issuer: String,
    val category: String,
    val fields: List<CardField>,
    val points: String,
    val status: String,
    val details: String,
)
/** Everything here runs on the phone: PdfRenderer + ML Kit barcode scanning + ML Kit text recognition + simple rules. */
object Importer {

    suspend fun analyzeTwoWay(ctx: Context, frontBmp: Bitmap, backBmp: Bitmap?): Detected {
        val id = UUID.randomUUID().toString()
        val frontPath = Files.saveCardSide(ctx, id, "front", frontBmp)
        val backPath = backBmp?.let { Files.saveCardSide(ctx, id, "back", it) }

        val scanner = BarcodeScanning.getClient()
        val codes = mutableListOf<Pair<String, CodeFormat>>()
        try {
            val frontImg = InputImage.fromBitmap(frontBmp, 0)
            for (bc in scanner.process(frontImg).await()) {
                val v = bc.rawValue
                if (!v.isNullOrBlank() && codes.none { it.first == v }) codes.add(v to CodeFormat.fromMl(bc.format))
            }
            if (backBmp != null) {
                val backImg = InputImage.fromBitmap(backBmp, 0)
                for (bc in scanner.process(backImg).await()) {
                    val v = bc.rawValue
                    if (!v.isNullOrBlank() && codes.none { it.first == v }) codes.add(v to CodeFormat.fromMl(bc.format))
                }
            }
        } finally {
            scanner.close()
        }

        val aiResult = AiVisionEngine.processImage(ctx, frontBmp, backBmp)

        val (issuer, category, fields, points, status, details) = when (aiResult) {
            is AiVisionResult.Success -> {
                val info = if (codes.isEmpty()) "${aiResult.dynamicFields.size} field(s) auto-filled by AI"
                           else "${codes.size} code(s), ${aiResult.dynamicFields.size} field(s) auto-filled"
                AnalysisOutcome(aiResult.issuer, aiResult.category, aiResult.dynamicFields, aiResult.points, aiResult.status, info)
            }
            is AiVisionResult.ModelNotDownloaded -> {
                val info = if (codes.isEmpty()) "OCR engine unavailable (check Settings)"
                           else "${codes.size} code(s) detected (OCR engine unavailable for text auto-fill)"
                AnalysisOutcome("", "Card", emptyList(), "", "", info)
            }
        }
        val firstCode = codes.firstOrNull()
        val primaryCode = firstCode?.first ?: ""
        val primaryFormat = firstCode?.second ?: CodeFormat.CODE128
        val detectedName = fields.firstOrNull { it.label.equals("Name", true) || it.label.equals("Student Name", true) }?.value ?: ""
        val card = WalletCard(
            id = id,
            name = detectedName,
            issuer = issuer,
            category = category,
            code = primaryCode,
            format = primaryFormat,
            colorIndex = (0 until C.CardColors.size).random(),
            frontImagePath = frontPath,
            backImagePath = backPath,
            fields = fields,
            points = points,
            status = status,
        )

        val frontThumb = frontBmp
        val backThumb = backBmp

        return Detected(card, details, codes, frontThumb, frontThumb, backThumb)
    }

    suspend fun analyze(ctx: Context, uri: Uri): Detected {
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val id = UUID.randomUUID().toString()
        val bitmaps = if (mime.contains("pdf")) renderPdf(ctx, uri) else listOfNotNull(decodeImage(ctx, uri))

        val codes = mutableListOf<Pair<String, CodeFormat>>()
        val scanner = BarcodeScanning.getClient()
        try {
            for (b in bitmaps) {
                val img = InputImage.fromBitmap(b, 0)
                for (bc in scanner.process(img).await()) {
                    val v = bc.rawValue
                    if (!v.isNullOrBlank() && codes.none { it.first == v }) codes.add(v to CodeFormat.fromMl(bc.format))
                }
            }
        } finally {
            scanner.close()
        }

        val aiResult = AiVisionEngine.processBitmaps(ctx, bitmaps)

        val (issuer, category, fields, points, status, details) = when (aiResult) {
            is AiVisionResult.Success -> {
                val info = if (codes.isEmpty()) "${aiResult.dynamicFields.size} field(s) auto-filled by AI"
                           else "${codes.size} code(s), ${aiResult.dynamicFields.size} field(s) auto-filled"
                AnalysisOutcome(aiResult.issuer, aiResult.category, aiResult.dynamicFields, aiResult.points, aiResult.status, info)
            }
            is AiVisionResult.ModelNotDownloaded -> {
                val info = if (codes.isEmpty()) "OCR engine unavailable (check Settings)"
                           else "${codes.size} code(s) detected (OCR engine unavailable for text auto-fill)"
                AnalysisOutcome("", "Card", emptyList(), "", "", info)
            }
        }

        val copy = Files.copyOriginal(ctx, id, uri)
        val firstCode = codes.firstOrNull()
        val primaryCode = firstCode?.first ?: ""
        val primaryFormat = firstCode?.second ?: CodeFormat.CODE128
        val detectedName = fields.firstOrNull { it.label.equals("Name", true) || it.label.equals("Student Name", true) }?.value ?: ""
        val card = WalletCard(
            id = id,
            name = detectedName,
            issuer = issuer,
            category = category,
            code = primaryCode,
            format = primaryFormat,
            colorIndex = (0 until C.CardColors.size).random(),
            fields = fields,
            points = points,
            status = status,
            originalPath = copy?.first,
            originalName = copy?.second,
            originalMime = copy?.third,
        )

        val thumb = bitmaps.firstOrNull()
        return Detected(card, details, codes, thumb, thumb, null)
    }

    private fun extractCardData(
        frontText: String,
        backText: String,
        frontLines: List<String>,
        allCodes: List<Pair<String, CodeFormat>>,
    ): ExtractedCardData {
        val fullText = "$frontText\n$backText"
        val lower = fullText.lowercase()

        val isLibrary = listOf("library", "lib", "bibliotheque", "book", "books").any { lower.contains(it) }
        val isMovieOrTheatre = listOf("cinema", "theatre", "movie", "screen", "audi", "seat", "showtime", "show time", "admit", "box office", "popcorn", "multiplex", "imax").any { lower.contains(it) }
        val isEventTicket = !isMovieOrTheatre && listOf("ticket", "concert", "stadium", "arena", "festival", "admit one", "row", "venue", "vip pass").any { lower.contains(it) }
        val isFlight = listOf("boarding pass", "flight", "terminal", "gate", "pnr", "departure", "arrival", "airline", "airways").any { lower.contains(it) }
        val isTransit = !isFlight && listOf("metro", "transit", "subway", "railway", "train", "coach", "bus pass").any { lower.contains(it) }
        val isInstitution = listOf("university", "college", "school", "institute", "hospital", "student", "employee", "faculty", "staff", "id card", "identity card", "campus", "matric", "roll no", "scholar", "blood group", "department").any { lower.contains(it) }
        val isLoyalty = listOf("loyalty", "rewards", "points", "club", "cashback", "miles", "pts", "frequent flyer").any { lower.contains(it) }
        val isMembership = listOf("gym", "fitness", "membership", "member").any { lower.contains(it) }

        val category = when {
            isLibrary -> "Library Card"
            isInstitution -> "Institutional ID"
            isMovieOrTheatre -> "Movie Ticket"
            isEventTicket -> "Event Ticket"
            isFlight -> "Flight Ticket"
            isTransit -> "Transit Pass"
            isLoyalty -> "Loyalty Card"
            isMembership -> "Membership"
            lower.contains("coupon") || lower.contains("voucher") -> "Coupon"
            else -> "Card"
        }

        val colorIndex = (0 until C.CardColors.size).random()

        val orgRegex = Regex("\\b(University|College|Institute|Academy|School|Hospital|Clinic|Foundation|Govt|Government|Ministry|Corporation|Corp|Inc|LLC|Ltd|Cinemas?|Theatres?|Airlines?|Airways?|Bank|Club|Gym|Fitness|Studio|Library|Authority|Transit)\\b", RegexOption.IGNORE_CASE)
        val invalidIssuerWords = setOf("cardholder", "card holder", "name", "student", "employee", "signature", "authorized", "identity card", "id card")

        var issuer = ""
        for (i in 0 until minOf(frontLines.size, 6)) {
            val l1 = frontLines[i].trim()
            if (l1.length < 3 || invalidIssuerWords.any { l1.contains(it, true) }) continue

            // 1. Two-line combined match (e.g. "STANFORD" + "UNIVERSITY" or "SAN JOSE STATE" + "UNIVERSITY")
            if (i + 1 < frontLines.size) {
                val l2 = frontLines[i + 1].trim()
                if (l2.length in 3..40 && !invalidIssuerWords.any { l2.contains(it, true) } && !listOf("id", "pass", "card").any { l2.equals(it, true) }) {
                    if (orgRegex.containsMatchIn("$l1 $l2")) {
                        issuer = "$l1\n$l2"
                        break
                    }
                }
            }

            // 2. Single-line match with multi-line sub-brand
            if (orgRegex.containsMatchIn(l1)) {
                if (i + 1 < frontLines.size) {
                    val l2 = frontLines[i + 1].trim()
                    if (l2.length in 3..35 && !invalidIssuerWords.any { l2.contains(it, true) } && !listOf("id", "pass", "card", "identity card").any { l2.equals(it, true) }) {
                        issuer = "$l1\n$l2"
                        break
                    }
                }
                issuer = l1
                break
            }
        }

        val fields = mutableListOf<CardField>()

        if (isInstitution || isLibrary) {
            val rawDept = Regex("(?:Dept\\.?|Department|Faculty|Branch|School of)\\s*[:\\-]?\\s*([A-Za-z0-9\\s&'-]+)", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()?.trim('.', ':', '-', ' ')
            if (!rawDept.isNullOrBlank() && rawDept.length >= 2 && rawDept.any(Char::isLetterOrDigit)) {
                fields.add(CardField("Department", rawDept.take(40)))
            }

            val fourDigitDate = Regex("(?:Valid(?:ity|\\s+thru|\\s+until|\\s+to)?|Exp(?:ires|iry)?)\\s*[:\\-]?\\s*([0-9]{1,2}\\s*[/\\-.]\\s*(?:[0-9]{1,2}\\s*[/\\-.])?\\s*20[2-3][0-9])", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.replace(Regex("\\s+"), "")
            val academicYear = Regex("\\b(20[2-3][0-9]\\s*[-/]\\s*20[2-3][0-9])\\b").find(fullText)?.value
            val fallbackDate = Regex("(?:Valid(?:ity|\\s+thru|\\s+until|\\s+to)?|Exp(?:ires|iry)?)\\s*[:\\-]?\\s*([0-9]{1,2}[/\\-.][0-9]{2,4})", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            val validity = fourDigitDate ?: academicYear ?: fallbackDate?.takeIf { it.length >= 4 && !it.endsWith(".") }
            if (!validity.isNullOrBlank() && validity.length >= 4) {
                fields.add(CardField("Validity", validity))
            }

            val blood = Regex("(?:Blood\\s*(?:Grp|Group)?)\\s*[:\\-]?\\s*([ABO][+-]|AB[+-])", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!blood.isNullOrBlank()) fields.add(CardField("Blood Group", blood))

            if (lower.contains("non-transferable") || lower.contains("not transferable")) {
                fields.add(CardField("Transferability", "Non-Transferable"))
            } else if (lower.contains("transferable")) {
                fields.add(CardField("Transferability", "Transferable"))
            }
        }

        if (isMovieOrTheatre || isEventTicket) {
            val screen = Regex("(?:Screen|Audi|Auditorium|Hall)\\s*[:\\-]?\\s*([A-Za-z0-9\\s]{1,15})", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!screen.isNullOrBlank()) fields.add(CardField("Screen", screen))

            val seat = Regex("(?:Seat(?:\\s*No)?)\\s*[:\\-]?\\s*([A-Za-z0-9\\-_]+)", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
                ?: Regex("\\b([A-Z]-[0-9]{1,3}|[A-Z][0-9]{1,2})\\b").find(fullText)?.groupValues?.get(1)?.trim()
            if (!seat.isNullOrBlank()) fields.add(CardField("Seat", seat))

            val row = Regex("(?:Row)\\s*[:\\-]?\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!row.isNullOrBlank()) fields.add(CardField("Row", row))

            val time = Regex("\\b([0-1]?[0-9]|2[0-3]):[0-5][0-9]\\s*(?:AM|PM|am|pm)?\\b").find(fullText)?.value
            if (!time.isNullOrBlank()) fields.add(CardField("Showtime", time))

            val booking = Regex("(?:Booking\\s*(?:ID|Ref|#)?|Order\\s*(?:ID|#)?|Ticket\\s*(?:No|#)?)\\s*[:\\-]?\\s*([A-Z0-9\\-_]+)", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!booking.isNullOrBlank()) fields.add(CardField("Booking Ref", booking))
        }

        if (isLoyalty || isMembership) {
            val pts = Regex("(?:Available\\s+Points|Points|Pts|Balance)\\s*[:\\-]?\\s*([0-9,]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!pts.isNullOrBlank()) fields.add(CardField("Points", pts))

            val minPts = Regex("(?:Min(?:imum)?\\s*(?:Points|Pts)?)\\s*[:\\-]?\\s*([0-9,]+)", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!minPts.isNullOrBlank()) fields.add(CardField("Min Points", minPts))

            val maxPts = Regex("(?:Max(?:imum)?\\s*(?:Points|Pts)?)\\s*[:\\-]?\\s*([0-9,]+)", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!maxPts.isNullOrBlank()) fields.add(CardField("Max Points", maxPts))

            val tier = Regex("\\b(Gold|Silver|Platinum|Diamond|VIP|Bronze|Classic|Elite|Premier|Plus|Pro)\\b", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!tier.isNullOrBlank()) fields.add(CardField("Tier", tier))

            val memberId = Regex("(?:Member(?:\\s*ID|#)?|Card\\s*(?:No|#)?)\\s*[:\\-]?\\s*([A-Z0-9\\-_]{4,24})", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!memberId.isNullOrBlank()) fields.add(CardField("Member ID", memberId))
        }

        if (isFlight || isTransit) {
            val flightNo = Regex("\\b([A-Z]{2}\\s*[0-9]{3,4})\\b").find(fullText)?.groupValues?.get(1)?.trim()
            if (!flightNo.isNullOrBlank()) fields.add(CardField("Flight No", flightNo))

            val gate = Regex("(?:Gate)\\s*[:\\-]?\\s*([A-Z]?[0-9]{1,3})", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!gate.isNullOrBlank()) fields.add(CardField("Gate", gate))

            val seat = Regex("(?:Seat)\\s*[:\\-]?\\s*([0-9]{1,2}[A-F])", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.get(1)?.trim()
            if (!seat.isNullOrBlank()) fields.add(CardField("Seat", seat))

            val route = Regex("\\b([A-Z]{3})\\s*(?:-|TO|to|>|→)\\s*([A-Z]{3})\\b").find(fullText)
            if (route != null) fields.add(CardField("Route", "${route.groupValues[1]} to ${route.groupValues[2]}"))
        }

        val firstCode = allCodes.firstOrNull()
        return ExtractedCardData(
            issuer = issuer,
            name = "",
            category = category,
            colorIndex = colorIndex,
            fields = fields,
            primaryCode = firstCode?.first ?: "",
            primaryFormat = firstCode?.second ?: CodeFormat.CODE128
        )
    }

    private fun decodeImage(ctx: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / s > 2400 || bounds.outHeight / s > 2400) s *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
        } ?: return null
        val deg = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        return if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }

    private fun renderPdf(ctx: Context, uri: Uri): List<Bitmap> {
        val tempFile = File(ctx.cacheDir, "temp_import_${UUID.randomUUID()}.pdf")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return emptyList()
            if (tempFile.length() == 0L) return emptyList()
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val out = mutableListOf<Bitmap>()
            val renderer = PdfRenderer(pfd)
            try {
                for (i in 0 until minOf(renderer.pageCount, 3)) {
                    val page = renderer.openPage(i)
                    try {
                        val w = 1400
                        val h = (w.toFloat() * page.height / page.width).toInt()
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(android.graphics.Color.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(b)
                    } finally { page.close() }
                }
            } finally {
                renderer.close()
                pfd.close()
            }
            return out
        } catch (e: Exception) {
            return emptyList()
        } finally {
            tempFile.delete()
        }
    }
}
