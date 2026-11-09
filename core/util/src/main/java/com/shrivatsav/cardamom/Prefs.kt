package com.shrivatsav.cardamom

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = createPrefs(ctx)

    companion object {
        /**
         * EncryptedSharedPreferences backed by the Android Keystore, with
         * one-time migration from the legacy plaintext file. Falls back to
         * plaintext only if encryption is unavailable (e.g. JVM unit tests
         * where AndroidKeyStore does not exist) — never crashes the app.
         */
        fun createPrefs(ctx: Context): SharedPreferences {
            val legacy = ctx.getSharedPreferences("cardamom", Context.MODE_PRIVATE)
            return try {
                val masterKey = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val encrypted = EncryptedSharedPreferences.create(
                    ctx,
                    "cardamom_secure",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                // One-time migration: copy legacy plaintext values over, then clear legacy.
                if (!encrypted.contains("__migrated_v1") && legacy.all.isNotEmpty()) {
                    val editor = encrypted.edit()
                    legacy.all.forEach { (k, v) ->
                        when (v) {
                            is String -> editor.putString(k, v)
                            is Boolean -> editor.putBoolean(k, v)
                            is Long -> editor.putLong(k, v)
                            is Int -> editor.putInt(k, v)
                            is Float -> editor.putFloat(k, v)
                            is Set<*> -> editor.putStringSet(k, v.filterIsInstance<String>().toSet())
                        }
                    }
                    editor.putBoolean("__migrated_v1", true).apply()
                    legacy.edit().clear().commit()
                }
                encrypted
            } catch (_: Exception) {
                legacy
            }
        }
    }
    var userName: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock", false)
        set(v) { sp.edit().putBoolean("lock", v).apply() }
    var illustrations: Boolean
        get() = sp.getBoolean("art", true)
        set(v) { sp.edit().putBoolean("art", v).apply() }
    var fetchLogos: Boolean
        get() = sp.getBoolean("logos", true)
        set(v) { sp.edit().putBoolean("logos", v).apply() }
    var lastBackup: Long
        get() = sp.getLong("lastBackup", 0L)
        set(v) { sp.edit().putLong("lastBackup", v).apply() }
    var userAvatarPath: String?
        get() = sp.getString("avatar", null)
        set(v) { sp.edit().putString("avatar", v).apply() }
    var pinnedCardId: String?
        get() = sp.getString("pinned_card_id", null)
        set(v) { sp.edit().putString("pinned_card_id", v).apply() }
    var pinnedDocId: String?
        get() = sp.getString("pinned_doc_id", null)
        set(v) { sp.edit().putString("pinned_doc_id", v).apply() }
    var keepPinnedExpanded: Boolean
        get() = sp.getBoolean("keep_pinned_expanded", true)
        set(v) { sp.edit().putBoolean("keep_pinned_expanded", v).apply() }
    var theme: String
        get() = sp.getString("theme", "SYSTEM") ?: "SYSTEM"
        set(v) { sp.edit().putString("theme", v).apply() }
    var lockTimeoutSeconds: Long
        get() = sp.getLong("lock_timeout", 0L)
        set(v) { sp.edit().putLong("lock_timeout", v).apply() }
    var screenSecurity: Boolean
        get() = sp.getBoolean("screen_security", false)
        set(v) { sp.edit().putBoolean("screen_security", v).apply() }
    var quickActions: Set<String>
        get() {
            val saved = sp.getStringSet("quick_actions", null)
            return if (saved != null) {
                if (!sp.contains("quick_actions_v2")) {
                    val updated = saved + "pin"
                    sp.edit().putBoolean("quick_actions_v2", true).putStringSet("quick_actions", updated).apply()
                    updated
                } else saved
            } else {
                setOf("pin", "edit", "share", "delete")
            }
        }
        set(v) {
            sp.edit().putBoolean("quick_actions_v2", true).putStringSet("quick_actions", v).apply()
        }
    var aiModelDownloaded: Boolean
        get() = sp.getBoolean("ai_model_downloaded", false)
        set(v) { sp.edit().putBoolean("ai_model_downloaded", v).apply() }
    var aiModelVersion: String
        get() = sp.getString("ai_model_version", "") ?: ""
        set(v) { sp.edit().putString("ai_model_version", v).apply() }
    var aiModelSizeBytes: Long
        get() = sp.getLong("ai_model_size_bytes", 0L)
        set(v) { sp.edit().putLong("ai_model_size_bytes", v).apply() }
    var aiModelSkipped: Boolean
        get() = sp.getBoolean("ai_model_skipped", false)
        set(v) { sp.edit().putBoolean("ai_model_skipped", v).apply() }
    var aiModelScript: String
        get() = sp.getString("ai_model_script", "DEVANAGARI_INDIC") ?: "DEVANAGARI_INDIC"
        set(v) { sp.edit().putString("ai_model_script", v).apply() }
    var ocrEngine: String
        get() = sp.getString("ocr_engine", AppOcrEngine.MLKIT.id) ?: AppOcrEngine.MLKIT.id
        set(v) { sp.edit().putString("ocr_engine", v).apply() }
    var lastSupportPromptTime: Long
        get() = sp.getLong("last_support_prompt_time", 0L)
        set(v) { sp.edit().putLong("last_support_prompt_time", v).apply() }

    // Licensing & 7-Day Free Trial
    var trialStarted: Boolean
        get() = sp.getBoolean("trial_started", false)
        set(v) { sp.edit().putBoolean("trial_started", v).apply() }
    var trialStartDate: Long
        get() = sp.getLong("trial_start_date", 0L)
        set(v) { sp.edit().putLong("trial_start_date", v).apply() }
    var trialExpiresDate: Long
        get() = sp.getLong("trial_expires_date", 0L)
        set(v) { sp.edit().putLong("trial_expires_date", v).apply() }
    var isPremium: Boolean
        get() = sp.getBoolean("is_premium", false)
        set(v) { sp.edit().putBoolean("is_premium", v).apply() }
    var lastSeenTimestamp: Long
        get() = sp.getLong("last_seen_timestamp", 0L)
        set(v) { sp.edit().putLong("last_seen_timestamp", v).apply() }
    var lastUptimeTicks: Long
        get() = sp.getLong("last_uptime_ticks", 0L)
        set(v) { sp.edit().putLong("last_uptime_ticks", v).apply() }
    var trialEndingDismissed: Boolean
        get() = sp.getBoolean("trial_ending_dismissed", false)
        set(v) { sp.edit().putBoolean("trial_ending_dismissed", v).apply() }
}
