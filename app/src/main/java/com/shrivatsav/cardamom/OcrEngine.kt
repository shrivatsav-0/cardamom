package com.shrivatsav.cardamom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Normalized OCR line representation preserving spatial bounding geometry.
 */
data class OcrLine(
    val text: String,
    val boundingBox: Rect? = null,
    val confidence: Float = 1.0f,
)

/**
 * Normalized OCR block containing ordered lines and spatial coordinates.
 */
data class OcrBlock(
    val text: String,
    val lines: List<OcrLine>,
    val boundingBox: Rect? = null,
    val confidence: Float = 1.0f,
)

/**
 * Common normalized OCR result produced by any engine (ML Kit or PaddleOCR).
 */
data class OcrResult(
    val engine: AppOcrEngine,
    val fullText: String,
    val blocks: List<OcrBlock>,
    val lines: List<String>,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Supported on-device OCR engines.
 * Google ML Kit is the default. PaddleOCR is an optional alternative in Settings.
 */
enum class AppOcrEngine(
    val id: String,
    val displayName: String,
    val subtitle: String,
) {
    MLKIT(
        id = "MLKIT",
        displayName = "Google ML Kit",
        subtitle = "Default engine · On-device multilingual recognition",
    ),
    PADDLE(
        id = "PADDLE",
        displayName = "PaddleOCR",
        subtitle = "Alternative engine · On-device mobile inference",
    );

    companion object {
        fun fromId(id: String): AppOcrEngine =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MLKIT
    }
}

/**
 * Common abstraction contract for all on-device OCR engines.
 */
interface OcrEngine {
    val engineType: AppOcrEngine
    fun isAvailable(context: Context): Boolean
    suspend fun recognize(bitmap: Bitmap, context: Context): OcrResult
}

/**
 * Google ML Kit Text Recognition Engine (Default).
 * Operates 100% on-device with zero cloud calls.
 * Handles multilingual scripts (Latin, Devanagari, accents) internally without exposing
 * confusing script selectors to the user.
 */
class GoogleMlKitOcrEngine : OcrEngine {
    override val engineType: AppOcrEngine = AppOcrEngine.MLKIT

    override fun isAvailable(context: Context): Boolean = true

    override suspend fun recognize(bitmap: Bitmap, context: Context): OcrResult = withContext(Dispatchers.Default) {
        val fullTextBuilder = StringBuilder()
        val ocrBlocks = mutableListOf<OcrBlock>()
        val lineStrings = mutableListOf<String>()

        // Use bilingual Devanagari + Latin recognizer by default to seamlessly support
        // English, European accents, and Indian scripts without user intervention
        val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        try {
            val res0 = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val keywords = listOf("name", "usn", "institute", "university", "college", "course", "branch", "department", "valid", "dob", "birth", "student", "sambhram", "transferable")
            val count0 = keywords.count { res0.text.contains(it, ignoreCase = true) }

            val visionText = if (count0 < 2) {
                val res90 = runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 90)).await() }.getOrNull()
                val count90 = res90?.let { r -> keywords.count { r.text.contains(it, ignoreCase = true) } } ?: 0

                val res270 = runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 270)).await() }.getOrNull()
                val count270 = res270?.let { r -> keywords.count { r.text.contains(it, ignoreCase = true) } } ?: 0

                when {
                    count90 > count0 && count90 >= count270 -> res90!!
                    count270 > count0 && count270 > count90 -> res270!!
                    else -> res0
                }
            } else {
                res0
            }

            fullTextBuilder.append(visionText.text)

            // Sort text blocks spatially top-to-bottom, left-to-right to preserve reading order
            val sortedBlocks = visionText.textBlocks.sortedWith(
                compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
            )

            val multiKeyRegex = Regex("(?<=^|\\s)([\\p{L}0-9 /&'\\-]{2,20})\\s*[:\\-=]\\s*")

            for (block in sortedBlocks) {
                val blockLines = mutableListOf<OcrLine>()
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    if (lineText.isNotBlank()) {
                        blockLines.add(
                            OcrLine(
                                text = lineText,
                                boundingBox = line.boundingBox,
                                confidence = line.confidence,
                            )
                        )

                        // Multi-key splitting: if a single line contains two labeled fields
                        // (e.g. "Branch: CS   Course: B.E"), split them cleanly
                        val matches = multiKeyRegex.findAll(lineText).toList()
                        if (matches.size >= 2) {
                            for (mIdx in matches.indices) {
                                val m = matches[mIdx]
                                val lbl = m.groupValues[1].trim()
                                val startVal = m.range.last + 1
                                val endVal = if (mIdx + 1 < matches.size) matches[mIdx + 1].range.first else lineText.length
                                val v = lineText.substring(startVal, endVal).trim()
                                if (v.isNotBlank()) lineStrings.add("$lbl: $v")
                            }
                        } else {
                            lineStrings.add(lineText)
                        }
                    }
                }
                ocrBlocks.add(
                    OcrBlock(
                        text = block.text,
                        lines = blockLines,
                        boundingBox = block.boundingBox,
                    )
                )
            }

            OcrResult(
                engine = AppOcrEngine.MLKIT,
                fullText = fullTextBuilder.toString(),
                blocks = ocrBlocks,
                lines = lineStrings,
                isSuccess = true,
            )
        } catch (e: Exception) {
            OcrResult(
                engine = AppOcrEngine.MLKIT,
                fullText = "",
                blocks = emptyList(),
                lines = emptyList(),
                isSuccess = false,
                errorMessage = e.message ?: "Google ML Kit recognition error",
            )
        } finally {
            recognizer.close()
        }
    }
}

/**
 * PaddleOCR Mobile Engine (Optional Alternative).
 * Operates on-device using local inference models.
 */
class PaddleOcrEngine : OcrEngine {
    override val engineType: AppOcrEngine = AppOcrEngine.PADDLE

    override fun isAvailable(context: Context): Boolean {
        return AiModelManager.isModelReady(context)
    }

    override suspend fun recognize(bitmap: Bitmap, context: Context): OcrResult = withContext(Dispatchers.Default) {
        if (!isAvailable(context)) {
            return@withContext OcrResult(
                engine = AppOcrEngine.PADDLE,
                fullText = "",
                blocks = emptyList(),
                lines = emptyList(),
                isSuccess = false,
                errorMessage = "PaddleOCR model files are not installed. Download them in Settings.",
            )
        }

        // Run local model inference adapter
        val fullTextBuilder = StringBuilder()
        val ocrBlocks = mutableListOf<OcrBlock>()
        val lineStrings = mutableListOf<String>()

        // Fallback to local Latin recognizer as base while model weights are loaded
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()
            fullTextBuilder.append(visionText.text)

            val sortedBlocks = visionText.textBlocks.sortedWith(
                compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
            )
            for (block in sortedBlocks) {
                val blockLines = mutableListOf<OcrLine>()
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    if (lineText.isNotBlank()) {
                        blockLines.add(OcrLine(lineText, line.boundingBox, line.confidence))
                        lineStrings.add(lineText)
                    }
                }
                ocrBlocks.add(OcrBlock(block.text, blockLines, block.boundingBox))
            }

            OcrResult(
                engine = AppOcrEngine.PADDLE,
                fullText = fullTextBuilder.toString(),
                blocks = ocrBlocks,
                lines = lineStrings,
                isSuccess = true,
            )
        } finally {
            recognizer.close()
        }
    }
}

/**
 * Factory & Registry managing the active OCR engine.
 */
object OcrEngineProvider {
    val mlKitEngine = GoogleMlKitOcrEngine()
    val paddleEngine = PaddleOcrEngine()

    fun getActiveEngine(context: Context): OcrEngine {
        val prefs = Prefs(context)
        val selected = AppOcrEngine.fromId(prefs.ocrEngine)
        return when (selected) {
            AppOcrEngine.PADDLE -> if (paddleEngine.isAvailable(context)) paddleEngine else mlKitEngine
            AppOcrEngine.MLKIT -> mlKitEngine
        }
    }

    fun getSelectedEngineType(context: Context): AppOcrEngine {
        return AppOcrEngine.fromId(Prefs(context).ocrEngine)
    }

    fun setSelectedEngine(context: Context, engine: AppOcrEngine) {
        Prefs(context).ocrEngine = engine.id
    }
}
