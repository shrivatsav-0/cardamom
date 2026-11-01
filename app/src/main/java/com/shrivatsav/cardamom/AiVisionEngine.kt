package com.shrivatsav.cardamom

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
sealed class AiVisionResult {
    object ModelNotDownloaded : AiVisionResult()
    data class Success(
        val fullText: String,
        val lines: List<String>,
        val issuer: String,
        val category: String,
        val dynamicFields: List<CardField>,
        val points: String,
        val status: String,
        val confidence: Float = 0.95f,
    ) : AiVisionResult()
}

/**
 * Local-first AI Vision Engine for on-device image recognition, text transcription,
 * and dynamic field extraction. Only executes if the AI model is downloaded and verified.
 */
object AiVisionEngine {
    suspend fun processImage(
        context: Context,
        frontBitmap: Bitmap,
        backBitmap: Bitmap? = null,
    ): AiVisionResult {
        val selectedType = OcrEngineProvider.getSelectedEngineType(context)
        if (selectedType == AppOcrEngine.PADDLE && !OcrEngineProvider.paddleEngine.isAvailable(context)) {
            return AiVisionResult.ModelNotDownloaded
        }

        val engine = OcrEngineProvider.getActiveEngine(context)
        val frontOcr = engine.recognize(frontBitmap, context)
        if (!frontOcr.isSuccess) {
            return AiVisionResult.ModelNotDownloaded
        }

        val backOcr = backBitmap?.let { engine.recognize(it, context) }

        return extractStructuredFields(
            frontText = frontOcr.fullText,
            backText = backOcr?.fullText ?: "",
            frontLines = frontOcr.lines,
            backLines = backOcr?.lines ?: emptyList(),
        )
    }

    suspend fun processBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
    ): AiVisionResult {
        val selectedType = OcrEngineProvider.getSelectedEngineType(context)
        if (selectedType == AppOcrEngine.PADDLE && !OcrEngineProvider.paddleEngine.isAvailable(context)) {
            return AiVisionResult.ModelNotDownloaded
        }
        if (bitmaps.isEmpty()) {
            return AiVisionResult.Success(
                fullText = "",
                lines = emptyList(),
                issuer = "",
                category = "Document",
                dynamicFields = emptyList(),
                points = "",
                status = "",
            )
        }

        val engine = OcrEngineProvider.getActiveEngine(context)
        val allText = StringBuilder()
        val allLines = mutableListOf<String>()

        for (bmp in bitmaps) {
            val ocr = engine.recognize(bmp, context)
            if (ocr.isSuccess) {
                allText.append(ocr.fullText).append("\n")
                allLines.addAll(ocr.lines)
            }
        }

        return extractStructuredFields(
            frontText = allText.toString(),
            backText = "",
            frontLines = allLines,
            backLines = emptyList(),
        )
    }

    /**
     * Dynamically transcribes the image content and generates dynamic key-value fields
     * tailored specifically to the recognized card or document.
     */
    fun extractStructuredFields(
        frontText: String,
        backText: String,
        frontLines: List<String>,
        backLines: List<String>,
    ): AiVisionResult.Success {
        val allText = if (backText.isNotBlank()) "$frontText\n$backText" else frontText
        val allLines = frontLines + backLines
        val lower = allText.lowercase()

        // 1. Dynamic category classification
        val isFlight = listOf("boarding pass", "flight", "terminal", "gate", "pnr", "departure", "arrival", "airline", "airways").any { lower.contains(it) }
        val isTransit = !isFlight && listOf("metro", "transit", "subway", "railway", "train", "coach", "bus pass").any { lower.contains(it) }
        val isMovieOrTheatre = !isFlight && !isTransit && listOf("cinema", "theatre", "movie", "screen", "audi", "showtime", "show time", "admit", "box office", "popcorn", "multiplex", "imax").any { lower.contains(it) }
        val isEventTicket = !isMovieOrTheatre && !isFlight && !isTransit && listOf("ticket", "concert", "stadium", "arena", "festival", "admit one", "row", "venue", "vip pass").any { lower.contains(it) }
        val isLibrary = !isMovieOrTheatre && !isEventTicket && !isFlight && (listOf("library", "bibliotheque").any { lower.contains(it) } || (Regex("\\bbooks?\\b").containsMatchIn(lower) && !lower.contains("booking")))
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
            lower.contains("passport") || lower.contains("national id") || lower.contains("license") || lower.contains("certificate") -> "Document"
            else -> "Card"
        }

        // 2. High-accuracy Issuer / Institution detection across all lines
        val orgRegex = Regex(
            "\\b(University|College|Institute|Academy|School|Hospital|Clinic|Foundation|Govt|Government|Ministry|Corporation|Corp|Inc|LLC|Ltd|Cinemas?|Theatres?|Airlines?|Airways?|Bank|Club|Gym|Fitness|Studio|Library|Authority|Transit|Supermarket|Store|Mart|Pharmacy|Coffee|Bakery|Retail)\\b",
            RegexOption.IGNORE_CASE
        )
        val invalidIssuerWords = setOf(
            "cardholder", "card holder", "name", "student", "employee", "signature", "authorized",
            "identity card", "id card", "boarding pass", "ticket", "pass", "valid", "validity",
            "valid thru", "expires", "expiry", "date", "dob", "birth", "gender", "sex", "blood",
            "group", "phone", "mobile", "address", "usn", "reg", "roll", "course", "branch",
            "department", "sem", "semester", "principal", "bengaluru", "bangalore", "m.s. palya", "pincode", "pin"
        )
        var issuer = ""
        for (i in allLines.indices) {
            val l1 = allLines[i].trim()
            if (l1.length < 3 || invalidIssuerWords.any { l1.equals(it, ignoreCase = true) }) continue

            if (i + 1 < allLines.size) {
                val l2 = allLines[i + 1].trim()
                if (l2.length in 3..40 && !invalidIssuerWords.any { l2.contains(it, ignoreCase = true) }) {
                    if (orgRegex.containsMatchIn("$l1 $l2")) {
                        issuer = "$l1\n$l2"
                        break
                    }
                }
            }

            if (orgRegex.containsMatchIn(l1)) {
                if (i > 0 && allLines[i - 1].length in 3..35 && !invalidIssuerWords.any { allLines[i - 1].contains(it, true) }) {
                    issuer = "${allLines[i - 1].trim()}\n$l1"
                } else {
                    issuer = l1
                }
                break
            }
        }
        issuer = issuer.replace(Regex("(?i)^if\\s+found\\s+return\\s+to\\s*[:\\-]?\\s*"), "").trim()
        // 3. Dynamic Field Extraction (detects custom labeled rows, institutional attributes, tickets, and dynamic attributes)
        val fields = mutableListOf<CardField>()
        val seenLabels = mutableSetOf<String>()

        fun addField(label: String, value: String) {
            val cleanLabel = label.trim().trim(':', '-', '.')
            val cleanValue = value.trim().trim(':', '-')

            // A field label MUST contain at least one letter (strictly rejects "2025" -> "2029")
            if (!cleanLabel.any { it.isLetter() }) return

            // Reject echo where label == value (e.g. Address: Address)
            if (cleanLabel.equals(cleanValue, ignoreCase = true)) return

            // Reject pure year ranges matching as label-value
            if (cleanLabel.matches(Regex("^20[0-3][0-9]$")) && cleanValue.matches(Regex("^20[0-3][0-9]$"))) return

            val key = cleanLabel.lowercase()
            // Never add blacklisted attribute labels
            if (setOf("bengaluru", "bangalore", "m.s. palya", "pincode", "pin", "valid", "principal").contains(key)) return

            if (cleanLabel.length in 2..32 && cleanValue.isNotBlank() && cleanValue.length <= 140) {
                if (!seenLabels.contains(key)) {
                    seenLabels.add(key)
                    fields.add(CardField(cleanLabel, cleanValue))
                }
            }
        }

        // A. Dedicated Indian & Global ID Entity Extractors
        // Student / Cardholder Name
        val nameLine = allLines.firstOrNull { 
            val l = it.trim().lowercase()
            (l.startsWith("name") || l.startsWith("student name") || l.startsWith("cardholder")) && (l.contains(":") || l.contains("-"))
        }
        val candName = if (nameLine != null) {
            nameLine.substringAfter(':').substringAfter('-').trim()
        } else {
            Regex("(?:Name(?:\\s*of\\s*Student)?|Student\\s*Name|Cardholder(?:\\s*Name)?)\\s*[:\\-=]\\s*([\\p{L} .]{2,40})", RegexOption.IGNORE_CASE)
                .find(allText)?.groupValues?.get(1)?.trim()
        }
        if (!candName.isNullOrBlank()) {
            val cleanName = candName.replace(Regex("(?i)^(of\\s+student|student)\\s*"), "").trim()
            val invalidNames = setOf("valid", "invalid", "category", "dept", "department", "course", "branch", "student", "signature", "principal")
            if (!invalidNames.contains(cleanName.lowercase()) && !cleanName.contains(":") && cleanName.length in 3..40) {
                addField("Name", cleanName)
            }
        }

        // University Seat Number (USN) / Student ID / Roll Number
        val usnMatch = Regex("(?:USN|Roll\\s*(?:No|#)?|Reg(?:istration)?\\s*(?:No|#)?|Student\\s*ID|Admission\\s*(?:No|#)?|Matric(?:ula)?\\s*(?:No|#)?)\\s*[:\\-=]\\s*([A-Za-z0-9\\-_/]{3,20})", RegexOption.IGNORE_CASE).find(allText)
        if (usnMatch != null) {
            val candUsn = usnMatch.groupValues[1].trim()
            val invalidCodes = setOf("institute", "technology", "university", "college", "school", "sambhram", "principal", "address")
            if (!invalidCodes.contains(candUsn.lowercase()) && !candUsn.contains(" ") && candUsn.any { it.isDigit() }) {
                addField("USN", candUsn)
            }
        }

        // Course
        val courseMatch = Regex("(?:Course|Degree|Program(?:me)?)\\s*[:\\-=]\\s*([\\p{L}0-9 .]{2,30})", RegexOption.IGNORE_CASE).find(allText)
        if (courseMatch != null) {
            addField("Course", courseMatch.groupValues[1].trim())
        }

        // Branch
        val branchMatch = Regex("(?:Branch|Specialization|Stream)\\s*[:\\-=]\\s*([\\p{L}0-9 &'\\-]{2,40})", RegexOption.IGNORE_CASE).find(allText)
        if (branchMatch != null) {
            addField("Branch", branchMatch.groupValues[1].trim())
        }

        // Date of Birth (DOB) - supports DD.MM.YYYY, DD/MM/YYYY, DD-MM-YYYY
        val dobMatch = Regex("(?:DOB|Date\\s*of\\s*Birth|Birth\\s*Date)\\s*[:\\-=]\\s*([0-9]{1,2}[./\\-][0-9]{1,2}[./\\-]20[0-2][0-9]|[0-9]{1,2}[./\\-][0-9]{1,2}[./\\-][0-9]{2,4})", RegexOption.IGNORE_CASE).find(allText)
        if (dobMatch != null) {
            addField("Date of Birth", dobMatch.groupValues[1].trim())
        }

        // Mobile Number (10 digits Indian or international)
        val mobileMatch = Regex("(?:Mobile(?:\\s*No\\.?)?|Phone(?:\\s*No\\.?)?|Cell|Contact)\\s*[:\\-=]\\s*(\\+?[0-9\\s\\-]{10,15})", RegexOption.IGNORE_CASE).find(allText)
        if (mobileMatch != null) {
            addField("Mobile No", mobileMatch.groupValues[1].trim())
        }

        // Address
        val addressLine = allLines.firstOrNull { it.trim().startsWith("address", ignoreCase = true) }
        if (addressLine != null) {
            val rawAddr = addressLine.replace(Regex("(?i)^address\\s*[:\\-=]\\s*"), "").trim()
            if (rawAddr.isNotBlank() && !rawAddr.equals("address", ignoreCase = true) && rawAddr.length >= 6) {
                addField("Address", rawAddr)
            }
        }

        // Blood Group (validate non-empty, non-dash)
        val bloodMatch = Regex("(?:Blood\\s*(?:Grp|Group)?)\\s*[:\\-=]\\s*([ABO][+-]|AB[+-])", RegexOption.IGNORE_CASE).find(allText)
        if (bloodMatch != null) {
            addField("Blood Group", bloodMatch.groupValues[1].trim())
        }

        // B. Generic Key-Value parser with Pincode and City filtering
        val kvPattern = Regex("^([\\p{L}0-9 /&'\\-]{2,24})\\s*[:\\-=]\\s*(.+)$")
        val blacklistedLabels = setOf("bengaluru", "bangalore", "delhi", "mumbai", "chennai", "hyderabad", "pincode", "pin", "postal", "valid", "principal")
        for (line in allLines) {
            val match = kvPattern.find(line.trim())
            if (match != null) {
                val rawLabel = match.groupValues[1].trim()
                val rawVal = match.groupValues[2].trim()
                val lowerLabel = rawLabel.lowercase()
                val isPincode = rawVal.matches(Regex("^[0-9]{5,6}$")) || rawVal.matches(Regex("^[0-9]{3}\\s*[0-9]{3}$"))
                if (!lowerLabel.contains("http") && !lowerLabel.contains("www") && !blacklistedLabels.contains(lowerLabel) && !isPincode && rawVal.isNotBlank() && rawVal.length <= 50) {
                    // Prevent duplicate Branch vs Department
                    if (!(lowerLabel == "department" && fields.any { it.label.equals("Branch", true) && it.value.equals(rawVal, true) })) {
                        addField(rawLabel, rawVal)
                    }
                }
            }
        }

        // C. Dynamic 2-line Key-Value detection (Label on line i, Value on line i+1)
        val commonLabels = listOf(
            "name", "student name", "usn", "member id", "membership no", "card number", "account", "account no",
            "department", "branch", "course", "batch", "class", "designation",
            "validity", "valid thru", "expires", "expiry date", "exp date", "dob",
            "blood group", "seat", "seat no", "row", "screen", "showtime", "booking ref",
            "flight", "flight no", "gate", "terminal", "pnr", "points", "tier",
            "roll no", "scholar no", "student id", "employee id", "policy no"
        )
        for (i in 0 until allLines.size - 1) {
            val cur = allLines[i].trim()
            val nxt = allLines[i + 1].trim()
            val curLower = cur.lowercase().trim(':', '-')
            val isNextALabelOrPair = nxt.contains(":") || commonLabels.any { nxt.lowercase().startsWith(it) }
            if (commonLabels.any { curLower == it } && nxt.isNotBlank() && nxt.length <= 40 && !isNextALabelOrPair) {
                addField(cur.replaceFirstChar { it.uppercase() }, nxt)
            }
        }

        // D. Domain-specific dynamic entity extraction
        val fourDigitDate = Regex("(?:Valid(?:ity|\\s+thru|\\s+until|\\s+to)?|Exp(?:ires|iry)?)\\s*[:\\-=]?\\s*([0-9]{1,2}[./\\-][0-9]{1,2}[./\\-]20[2-3][0-9]|20[2-3][0-9]\\s*[-/]\\s*20[2-3][0-9]|[0-9]{1,2}[./\\-]20[2-3][0-9])", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.replace(Regex("\\s+"), "")
        if (!fourDigitDate.isNullOrBlank()) addField("Validity", fourDigitDate)

        if (isInstitution || isLibrary) {
            // Only add department if branch is not already added with same value
            val rawDept = Regex("(?:Dept\\.?|Department|Faculty|School of)\\s*[:\\-]?\\s*([A-Za-z0-9 &'-]+)", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
            if (!rawDept.isNullOrBlank() && !fields.any { it.label.equals("Branch", true) && it.value.equals(rawDept, true) }) {
                addField("Department", rawDept.take(40))
            }

            val lowerClean = lower.replace(Regex("[\\-_\\s]+"), " ")
            if (lowerClean.contains("non transferable") || lowerClean.contains("not transferable")) {
                addField("Transferability", "Non-Transferable")
            } else if (lowerClean.contains("transferable")) {
                addField("Transferability", "Transferable")
            }
        }

        if (isMovieOrTheatre || isEventTicket) {
            Regex("(?:Screen|Audi|Auditorium|Hall)\\s*[:\\-]?\\s*([A-Za-z0-9\\s]{1,15})", RegexOption.IGNORE_CASE)
                .find(allText)?.groupValues?.get(1)?.trim()?.let { addField("Screen", it) }

            val seat = Regex("(?:Seat(?:\\s*No)?)\\s*[:\\-]?\\s*([A-Za-z0-9\\-_]+)", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
                ?: Regex("\\b([A-Z]-[0-9]{1,3}|[A-Z][0-9]{1,2})\\b").find(allText)?.groupValues?.get(1)?.trim()
            if (!seat.isNullOrBlank()) addField("Seat", seat)

            Regex("(?:Row)\\s*[:\\-]?\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
                .find(allText)?.groupValues?.get(1)?.trim()?.let { addField("Row", it) }

            Regex("\\b([0-1]?[0-9]|2[0-3]):[0-5][0-9]\\s*(?:AM|PM|am|pm)?\\b")
                .find(allText)?.value?.let { addField("Showtime", it) }

            Regex("(?:Booking\\s*(?:ID|Ref|#)?|Order\\s*(?:ID|#)?|Ticket\\s*(?:No|#)?)\\s*[:\\-]?\\s*([A-Z0-9\\-_]+)", RegexOption.IGNORE_CASE)
                .find(allText)?.groupValues?.get(1)?.trim()?.let { addField("Booking Ref", it) }
        }

        var detectedPoints = ""
        var detectedTier = ""
        if (isLoyalty || isMembership) {
            val pts = Regex("(?:Available\\s+Points|Points|Pts|Balance)\\s*[:\\-]?\\s*([0-9,]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
                .find(allText)?.groupValues?.get(1)?.trim()
            if (!pts.isNullOrBlank()) {
                detectedPoints = pts
                addField("Points", pts)
            }

            val minPts = Regex("(?:Min(?:imum)?\\s*(?:Points|Pts)?)\\s*[:\\-]?\\s*([0-9,]+)", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
            if (!minPts.isNullOrBlank()) addField("Min Points", minPts)

            val maxPts = Regex("(?:Max(?:imum)?\\s*(?:Points|Pts)?)\\s*[:\\-]?\\s*([0-9,]+)", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
            if (!maxPts.isNullOrBlank()) addField("Max Points", maxPts)

            val tier = Regex("\\b(Gold|Silver|Platinum|Diamond|VIP|Bronze|Classic|Elite|Premier|Plus|Pro)\\b", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
            if (!tier.isNullOrBlank()) {
                detectedTier = tier
                addField("Tier", tier)
            }

            val memberId = Regex("(?:Member(?:\\s*ID|#)?|Card\\s*(?:No|#)?)\\s*[:\\-]?\\s*([A-Z0-9\\-_]{4,24})", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
            if (!memberId.isNullOrBlank()) addField("Member ID", memberId)
        }

        if (isFlight || isTransit) {
            val flightNo = Regex("\\b([A-Z]{2}\\s*[0-9]{3,4})\\b").find(allText)?.groupValues?.get(1)?.trim()
            if (!flightNo.isNullOrBlank()) addField("Flight No", flightNo)

            val gate = Regex("(?:Gate)\\s*[:\\-]?\\s*([A-Z]?[0-9]{1,3})", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
            if (!gate.isNullOrBlank()) addField("Gate", gate)

            val seat = Regex("(?:Seat)\\s*[:\\-]?\\s*([0-9]{1,2}[A-F])", RegexOption.IGNORE_CASE).find(allText)?.groupValues?.get(1)?.trim()
            if (!seat.isNullOrBlank()) addField("Seat", seat)

            val route = Regex("\\b([A-Z]{3})\\s*(?:-|TO|to|>|→)\\s*([A-Z]{3})\\b").find(allText)
            if (route != null) addField("Route", "${route.groupValues[1]} to ${route.groupValues[2]}")
        }

        return AiVisionResult.Success(
            fullText = allText,
            lines = allLines,
            issuer = issuer,
            category = category,
            dynamicFields = fields,
            points = detectedPoints,
            status = detectedTier,
            confidence = 0.95f,
        )
    }
}
