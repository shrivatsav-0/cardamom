package com.shrivatsav.cardamom

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.tasks.Task
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

object Auth {
    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(a: FragmentActivity, credentialOnly: Boolean = false, onOk: () -> Unit) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Cardamom")
            .setAllowedAuthenticators(if (credentialOnly) DEVICE_CREDENTIAL else (BIOMETRIC_STRONG or DEVICE_CREDENTIAL))
            .build()
        BiometricPrompt(a, ContextCompat.getMainExecutor(a), cb).authenticate(info)
    }
}

object Codes {
    fun bitmap(value: String, f: CodeFormat, w: Int, h: Int): Bitmap? {
        fun enc(fmt: BarcodeFormat): Bitmap? = try {
            val m = MultiFormatWriter().encode(value, fmt, w, h, mapOf(EncodeHintType.MARGIN to 1))
            val px = IntArray(m.width * m.height) { if (m.get(it % m.width, it / m.width)) Color.BLACK else Color.WHITE }
            Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) { null }
        return enc(f.zx) ?: if (f.zx != BarcodeFormat.CODE_128) enc(BarcodeFormat.CODE_128) else null
    }
}

object Files {
    fun dir(ctx: Context, n: String): File = File(ctx.filesDir, n).also { it.mkdirs() }

    fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: "file"

    /** Copies the imported image/PDF into app storage. Returns (path, display name, mime). */
    fun copyOriginal(ctx: Context, id: String, uri: Uri): Triple<String, String, String>? {
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val ext = when { mime.contains("pdf") -> "pdf"; mime.contains("png") -> "png"; else -> "jpg" }
        val f = File(dir(ctx, "originals"), "$id.$ext")
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return Triple(f.absolutePath, displayName(ctx, uri), mime)
    }

    fun saveCustomLogo(ctx: Context, id: String, uri: Uri): String? {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val m = maxOf(bmp.width, bmp.height)
        val scaled = if (m > 256) Bitmap.createScaledBitmap(bmp, bmp.width * 256 / m, bmp.height * 256 / m, true) else bmp
        val f = File(dir(ctx, "logos"), "custom_$id.png")
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    fun saveCardSide(ctx: Context, id: String, side: String, bmp: Bitmap): String {
        val f = File(dir(ctx, "cards"), "${id}_$side.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return f.absolutePath
    }

    fun saveUserAvatar(ctx: Context, uri: Uri): String? {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val m = maxOf(bmp.width, bmp.height)
        val scaled = if (m > 512) Bitmap.createScaledBitmap(bmp, bmp.width * 512 / m, bmp.height * 512 / m, true) else bmp
        val f = File(dir(ctx, "avatars"), "user_avatar_${System.currentTimeMillis()}.png")
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }
}

/** Fetches a site's icon once (apple-touch-icon, then <link rel=icon>, then /favicon.ico) over HTTPS and stores it locally. */
object Favicon {
    private fun attr(tag: String, name: String): String? =
        Regex("$name\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    private fun read(i: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = i.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun get(url: String, max: Int): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 6000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Cardamom")
        return try {
            if (c.responseCode !in 200..299) null else c.inputStream.use { read(it, max) }
        } finally { c.disconnect() }
    }

    fun fetch(ctx: Context, id: String, website: String): String? {
        val raw = website.trim()
        val base = try { URL(if (raw.startsWith("http")) raw else "https://$raw") } catch (e: Exception) { return null }
        if (base.protocol != "https") return null
        val candidates = mutableListOf<String>()
        try {
            val html = get(base.toString(), 200_000)?.toString(Charsets.UTF_8) ?: ""
            for (m in Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)) {
                val tag = m.value
                val rel = attr(tag, "rel")?.lowercase() ?: continue
                val href = attr(tag, "href") ?: continue
                if (rel.contains("apple-touch-icon")) candidates.add(0, href) else if (rel.contains("icon")) candidates.add(href)
            }
        } catch (e: Exception) { /* fall through to /favicon.ico */ }
        candidates.add("/favicon.ico")
        for (c in candidates) {
            try {
                val u = URL(base, c)
                if (u.protocol != "https") continue
                val bytes = get(u.toString(), 1_000_000) ?: continue
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                val m = maxOf(bmp.width, bmp.height)
                val scaled = if (m > 256) Bitmap.createScaledBitmap(bmp, bmp.width * 256 / m, bmp.height * 256 / m, true) else bmp
                val f = File(Files.dir(ctx, "logos"), "$id.png")
                f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                return f.absolutePath
            } catch (e: Exception) { /* try next candidate */ }
        }
        return null
    }
}
