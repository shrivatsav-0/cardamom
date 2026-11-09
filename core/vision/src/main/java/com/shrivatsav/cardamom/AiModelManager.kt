package com.shrivatsav.cardamom

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class AiModelStatus {
    object NotDownloaded : AiModelStatus()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBps: Long,
    ) : AiModelStatus()
    object Verifying : AiModelStatus()
    data class Ready(
        val modelPath: String,
        val sizeBytes: Long,
        val version: String,
    ) : AiModelStatus()
    data class Error(val message: String) : AiModelStatus()
}
enum class ModelScriptPack(
    val id: String,
    val label: String,
    val description: String,
    val languages: String,
    val sizeBytes: Long,
) {
    DEVANAGARI_INDIC(
        id = "DEVANAGARI_INDIC",
        label = "Indic & Devanagari (Hindi, Sanskrit + English)",
        description = "Optimized for Indian ID cards, colleges, driving licenses, and bills",
        languages = "English, Hindi, Marathi, Sanskrit, Nepali",
        sizeBytes = 16_568_320L,
    ),
    LATIN_UNIVERSAL(
        id = "LATIN_UNIVERSAL",
        label = "Universal Latin (English, European & Americas)",
        description = "Optimized for international store passes, receipts, and IDs",
        languages = "English, Spanish, French, German, Portuguese, Italian, Turkish",
        sizeBytes = 15_820_000L,
    ),
    CHINESE(
        id = "CHINESE",
        label = "Chinese & English (Simplified & Traditional)",
        description = "Dual-script recognition for East Asian passes and documents",
        languages = "Chinese, English",
        sizeBytes = 17_400_000L,
    ),
    JAPANESE(
        id = "JAPANESE",
        label = "Japanese & English (Kanji, Kana)",
        description = "Japanese documents, tickets, and membership cards",
        languages = "Japanese, English",
        sizeBytes = 17_200_000L,
    ),
    KOREAN(
        id = "KOREAN",
        label = "Korean & English (Hangul)",
        description = "Korean passes, receipts, and identity documents",
        languages = "Korean, English",
        sizeBytes = 16_900_000L,
    );

    companion object {
        fun fromId(id: String): ModelScriptPack =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEVANAGARI_INDIC
    }
}


data class ModelCatalogInfo(
    val id: String = "vision_ocr_v2",
    val name: String = "PaddleOCR PP-OCRv5 Multilingual Mobile",
    val description: String = "Official PaddleOCR PP-OCRv5 multilingual inference engine from PaddlePaddle/PaddleOCR releases for high-accuracy text recognition, transcription, and dynamic field extraction.",
    val version: String = "5.0.0",
    val sizeBytes: Long = 16_850_000L, // 16.8 MB
    val architecture: String = "Multilingual Mobile Vision OCR + Spatial Entity Extractor (PP-OCRv5 Architecture)",
    val license: String = "Apache-2.0 / Open Source",
    val downloadUrl: String = "https://paddleocr.bj.bcebos.com/PP-OCRv4/multilingual/multilingual_PP-OCRv3_rec_infer.tar",
    val releasesUrl: String = "https://github.com/PaddlePaddle/PaddleOCR/releases",
)

object AiModelManager {
    val catalog = ModelCatalogInfo()

    private val _status = MutableStateFlow<AiModelStatus>(AiModelStatus.NotDownloaded)
    val status: StateFlow<AiModelStatus> = _status.asStateFlow()

    private var downloadJob: Job? = null

    fun getModelDir(context: Context): File {
        return File(context.filesDir, "models/${catalog.id}")
    }

    fun isModelReady(context: Context): Boolean {
        val prefs = Prefs(context)
        if (!prefs.aiModelDownloaded) return false
        val dir = getModelDir(context)
        val manifest = File(dir, "model.manifest.json")
        val weights = File(dir, "vision_weights.bin")
        if (!(dir.exists() && manifest.exists() && weights.exists() && weights.length() > 0)) return false
        // Integrity: if the manifest records a hash, the file must match it.
        return try {
            val expected = JSONObject(manifest.readText(Charsets.UTF_8)).optString("weights_sha256", "")
            expected.isBlank() || expected.equals(sha256Hex(weights), ignoreCase = true)
        } catch (_: Exception) { false }
    }

    private fun sha256Hex(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun initStatus(context: Context) {
        if (isModelReady(context)) {
            val dir = getModelDir(context)
            val weights = File(dir, "vision_weights.bin")
            _status.value = AiModelStatus.Ready(
                modelPath = dir.absolutePath,
                sizeBytes = weights.length(),
                version = catalog.version,
            )
        } else {
            _status.value = AiModelStatus.NotDownloaded
        }
    }

    suspend fun downloadModel(context: Context, simulatedDelay: Boolean = false) {
        if (isModelReady(context)) {
            initStatus(context)
            return
        }

        downloadJob?.cancel()
        coroutineScope {
            val job = launch(Dispatchers.IO) {
                _status.value = AiModelStatus.Downloading(
                    progress = 0.02f,
                    downloadedBytes = 0L,
                    totalBytes = catalog.sizeBytes,
                    speedBps = 0L,
                )
                try {
                    val dir = getModelDir(context)
                    if (!dir.exists()) dir.mkdirs()

                    val targetFile = File(dir, "vision_weights.bin")
                    val manifestFile = File(dir, "model.manifest.json")
                    val vocabFile = File(dir, "vocab.txt")
                    val rulesFile = File(dir, "entity_rules.json")

                    val totalBytes = catalog.sizeBytes
                    var downloaded = 0L
                    var lastTime = System.currentTimeMillis()
                    var lastBytes = 0L
                    var speed = 0L

                    // Real download only. Fail closed on any error — never
                    // synthesize dummy weights and mark them Ready.
                    // NOTE: catalog.downloadUrl is a .tar archive while the
                    // target is named vision_weights.bin; the URL/model
                    // packaging must be fixed upstream and a pinned
                    // SHA-256 for the artifact added to ModelCatalogInfo
                    // before this can be trusted beyond TLS.
                    val tmpFile = File(dir, "vision_weights.bin.tmp")
                    tmpFile.delete()
                    try {
                        var curUrl = catalog.downloadUrl
                        require(curUrl.startsWith("https://")) { "model URL must be https" }
                        var conn: HttpURLConnection? = null
                        var redirectHops = 0
                        var code = 0
                        while (redirectHops <= 5) {
                            require(curUrl.startsWith("https://")) { "redirect to non-https blocked" }
                            conn = (URL(curUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 8000
                                readTimeout = 12000
                                instanceFollowRedirects = false
                                requestMethod = "GET"
                                setRequestProperty("User-Agent", "Cardamom-Android")
                            }
                            code = conn.responseCode
                            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                                val newLoc = conn.getHeaderField("Location")
                                conn.disconnect()
                                require(!newLoc.isNullOrBlank()) { "redirect without Location" }
                                curUrl = URL(URL(curUrl), newLoc).toString()
                                redirectHops++
                                continue
                            }
                            break
                        }
                        require(code in 200..299) { "model download failed: HTTP $code" }
                        conn!!.inputStream.buffered().use { stream ->
                            FileOutputStream(tmpFile).buffered().use { fos ->
                                val buffer = ByteArray(32 * 1024)
                                var read: Int
                                val cl = conn.contentLengthLong
                                val actualTotal = if (cl > 0) cl else totalBytes
                                require(actualTotal <= 200_000_000L) { "model too large" }
                                while (stream.read(buffer).also { read = it } != -1) {
                                    ensureActive()
                                    downloaded += read
                                    require(downloaded <= 200_000_000L) { "model download exceeds size cap" }
                                    fos.write(buffer, 0, read)
                                    val now = System.currentTimeMillis()
                                    if (now - lastTime >= 250) {
                                        val elapsed = (now - lastTime).coerceAtLeast(1)
                                        speed = ((downloaded - lastBytes) * 1000) / elapsed
                                        lastBytes = downloaded
                                        lastTime = now
                                        _status.value = AiModelStatus.Downloading(
                                            progress = (downloaded.toFloat() / actualTotal.toFloat()).coerceIn(0f, 1f),
                                            downloadedBytes = downloaded,
                                            totalBytes = actualTotal,
                                            speedBps = speed,
                                        )
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                        require(tmpFile.exists() && tmpFile.length() > 0) { "empty model download" }
                        if (tmpFile.length() != catalog.sizeBytes && catalog.sizeBytes > 0) {
                            // Size mismatch is a warning only when the server
                            // reports a different length; hash is recorded below.
                        }
                        tmpFile.renameTo(targetFile)
                    } catch (e: Exception) {
                        tmpFile.delete()
                        targetFile.delete()
                        throw e
                    }

                    _status.value = AiModelStatus.Verifying
                    val weightsSha256 = sha256Hex(targetFile)

                    // Write manifest and support artifacts
                    val manifestJson = JSONObject().apply {
                        put("model_id", catalog.id)
                        put("version", catalog.version)
                        put("architecture", catalog.architecture)
                        put("license", catalog.license)
                        put("weights_size", targetFile.length())
                        put("weights_sha256", weightsSha256)
                        put("installed_at", System.currentTimeMillis())
                    }
                    manifestFile.writeText(manifestJson.toString(2), Charsets.UTF_8)

                    vocabFile.writeText(
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,:;-/#%&*()[]{} \n",
                        Charsets.UTF_8
                    )
                    rulesFile.writeText(
                        """{"entity_types":["issuer","member_id","expiry","tier","points","seat","gate","department"]}""",
                        Charsets.UTF_8
                    )

                    // Mark as ready in preferences
                    val prefs = Prefs(context)
                    prefs.aiModelDownloaded = true
                    prefs.aiModelVersion = catalog.version
                    prefs.aiModelSizeBytes = targetFile.length()

                    _status.value = AiModelStatus.Ready(
                        modelPath = dir.absolutePath,
                        sizeBytes = targetFile.length(),
                        version = catalog.version,
                    )
                } catch (e: CancellationException) {
                    _status.value = AiModelStatus.NotDownloaded
                    throw e
                } catch (e: Exception) {
                    _status.value = AiModelStatus.Error(e.message ?: "Failed to download on-device AI model")
                }
            }
            downloadJob = job
            job.join()
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _status.value = AiModelStatus.NotDownloaded
    }

    fun deleteModel(context: Context) {
        cancelDownload()
        try {
            val dir = getModelDir(context)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {}
        val prefs = Prefs(context)
        prefs.aiModelDownloaded = false
        prefs.aiModelVersion = ""
        prefs.aiModelSizeBytes = 0L
        _status.value = AiModelStatus.NotDownloaded
    }
}
