package com.shrivatsav.cardamom

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class CodeFormat(val label: String, val zx: BarcodeFormat, val ml: Int) {
    QR("QR code", BarcodeFormat.QR_CODE, Barcode.FORMAT_QR_CODE),
    CODE128("Code 128", BarcodeFormat.CODE_128, Barcode.FORMAT_CODE_128),
    CODE39("Code 39", BarcodeFormat.CODE_39, Barcode.FORMAT_CODE_39),
    CODE93("Code 93", BarcodeFormat.CODE_93, Barcode.FORMAT_CODE_93),
    CODABAR("Codabar", BarcodeFormat.CODABAR, Barcode.FORMAT_CODABAR),
    EAN13("EAN-13", BarcodeFormat.EAN_13, Barcode.FORMAT_EAN_13),
    EAN8("EAN-8", BarcodeFormat.EAN_8, Barcode.FORMAT_EAN_8),
    UPCA("UPC-A", BarcodeFormat.UPC_A, Barcode.FORMAT_UPC_A),
    UPCE("UPC-E", BarcodeFormat.UPC_E, Barcode.FORMAT_UPC_E),
    ITF("ITF", BarcodeFormat.ITF, Barcode.FORMAT_ITF),
    PDF417("PDF417", BarcodeFormat.PDF_417, Barcode.FORMAT_PDF417),
    AZTEC("Aztec", BarcodeFormat.AZTEC, Barcode.FORMAT_AZTEC),
    DATAMATRIX("Data Matrix", BarcodeFormat.DATA_MATRIX, Barcode.FORMAT_DATA_MATRIX);

    val is2D: Boolean get() = this == QR || this == PDF417 || this == AZTEC || this == DATAMATRIX

    companion object {
        fun fromMl(f: Int): CodeFormat = values().firstOrNull { it.ml == f } ?: CODE128
    }
}

enum class LogoMode { AUTO, EMOJI, IMAGE }

data class CardField(
    val label: String,
    val value: String,
)

data class WalletDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val docType: String = "National ID",
    val issuer: String = "",
    val uriPath: String = "",
    val mimeType: String = "image/jpeg",
    val pageCount: Int = 1,
    val pagePaths: List<String> = emptyList(),
    val pdfPath: String? = null,
    val fileSize: Long = 0L,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val fields: List<CardField> = emptyList(),
)

data class WalletCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val issuer: String = "",
    val website: String = "",
    val category: String = "Card",
    val code: String = "",
    val format: CodeFormat = CodeFormat.CODE128,
    val colorIndex: Int = (0..99).random() % 5,
    val seed: Int = (0..99999).random(),
    val logoMode: LogoMode = LogoMode.AUTO,
    val emoji: String = "",
    val logoPath: String? = null,
    val customPath: String? = null,
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val fields: List<CardField> = emptyList(),
    val points: String = "",
    val status: String = "",
    val originalPath: String? = null,
    val originalName: String? = null,
    val originalMime: String? = null,
    val isPinned: Boolean = false,
    val isDocument: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val initials: String
        get() = (if (issuer.isNotBlank()) issuer else name).trim().split(" ")
            .filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

fun WalletCard.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("issuer", issuer); put("website", website); put("category", category)
    put("code", code); put("format", format.name); put("colorIndex", colorIndex); put("seed", seed)
    put("logoMode", logoMode.name); put("emoji", emoji)
    put("logoPath", logoPath ?: JSONObject.NULL)
    put("customPath", customPath ?: JSONObject.NULL)
    put("frontImagePath", frontImagePath ?: JSONObject.NULL)
    put("backImagePath", backImagePath ?: JSONObject.NULL)
    put("isPinned", isPinned)
    put("isDocument", isDocument)
    put("points", points); put("status", status)
    put("originalPath", originalPath ?: JSONObject.NULL)
    put("originalName", originalName ?: JSONObject.NULL)
    put("originalMime", originalMime ?: JSONObject.NULL)
    put("createdAt", createdAt)
    val arr = JSONArray()
    fields.forEach { f ->
        arr.put(JSONObject().apply {
            put("label", f.label)
            put("value", f.value)
        })
    }
    put("fields", arr)
}

fun JSONObject.toCard(): WalletCard {
    val fieldsList = mutableListOf<CardField>()
    val arr = optJSONArray("fields")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val l = obj.optString("label", "")
            val v = obj.optString("value", "")
            if (l.isNotBlank() && v.isNotBlank()) {
                fieldsList.add(CardField(l, v))
            }
        }
    }
    val pts = str("points") ?: ""
    val st = str("status") ?: ""
    if (fieldsList.isEmpty()) {
        if (pts.isNotBlank()) fieldsList.add(CardField("Points", pts))
        if (st.isNotBlank()) fieldsList.add(CardField("Status", st))
    }
    return WalletCard(
        id = str("id") ?: UUID.randomUUID().toString(),
        name = str("name") ?: "",
        issuer = str("issuer") ?: "",
        website = str("website") ?: "",
        category = str("category") ?: "Card",
        code = str("code") ?: "",
        format = runCatching { CodeFormat.valueOf(str("format") ?: "") }.getOrDefault(CodeFormat.CODE128),
        colorIndex = optInt("colorIndex", 0),
        seed = optInt("seed", 1),
        logoMode = runCatching { LogoMode.valueOf(str("logoMode") ?: "") }.getOrDefault(LogoMode.AUTO),
        emoji = str("emoji") ?: "",
        logoPath = str("logoPath"),
        customPath = str("customPath"),
        frontImagePath = str("frontImagePath"),
        backImagePath = str("backImagePath"),
        fields = fieldsList,
        points = pts,
        status = st,
        originalPath = str("originalPath"),
        originalName = str("originalName"),
        originalMime = str("originalMime"),
        isPinned = optBoolean("isPinned", false),
        isDocument = optBoolean("isDocument", false),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
    )
}

fun WalletDocument.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("title", title); put("docType", docType); put("issuer", issuer)
    put("uriPath", uriPath); put("mimeType", mimeType); put("pageCount", pageCount)
    put("pdfPath", pdfPath ?: JSONObject.NULL)
    put("fileSize", fileSize); put("isPinned", isPinned); put("createdAt", createdAt)
    val pArr = JSONArray()
    pagePaths.forEach { pArr.put(it) }
    put("pagePaths", pArr)
    val fArr = JSONArray()
    fields.forEach { f ->
        fArr.put(JSONObject().apply {
            put("label", f.label)
            put("value", f.value)
        })
    }
    put("fields", fArr)
}

fun JSONObject.toDocument(): WalletDocument {
    val pList = mutableListOf<String>()
    optJSONArray("pagePaths")?.let { arr ->
        for (i in 0 until arr.length()) pList.add(arr.optString(i))
    }
    val fList = mutableListOf<CardField>()
    optJSONArray("fields")?.let { arr ->
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val l = obj.optString("label", "")
            val v = obj.optString("value", "")
            if (l.isNotBlank()) fList.add(CardField(l, v))
        }
    }
    return WalletDocument(
        id = str("id") ?: UUID.randomUUID().toString(),
        title = str("title") ?: "",
        docType = str("docType") ?: "Document",
        issuer = str("issuer") ?: "",
        uriPath = str("uriPath") ?: "",
        mimeType = str("mimeType") ?: "image/jpeg",
        pageCount = optInt("pageCount", 1),
        pagePaths = pList,
        pdfPath = str("pdfPath"),
        fileSize = optLong("fileSize", 0L),
        isPinned = optBoolean("isPinned", false),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        fields = fList,
    )
}
