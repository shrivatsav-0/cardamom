package com.shrivatsav.cardamom

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CardRepository(app)
    private val prefs = Prefs(app)

    val cards = repo.cards
    private val docRepo = DocumentRepository(app)
    val documents = docRepo.documents
    var draftDoc by mutableStateOf<WalletDocument?>(null)
    var homeTab by mutableStateOf(0)
    var pinnedCardId by mutableStateOf(prefs.pinnedCardId)
    var pinnedDocId by mutableStateOf(prefs.pinnedDocId)
    var keepPinnedExpanded by mutableStateOf(prefs.keepPinnedExpanded)


    var onboarded by mutableStateOf(prefs.onboarded)
    var userName by mutableStateOf(prefs.userName)
    var userAvatarPath by mutableStateOf(prefs.userAvatarPath)
    var lockEnabled by mutableStateOf(prefs.lockEnabled)
    var illustrations by mutableStateOf(prefs.illustrations)
    var fetchLogos by mutableStateOf(prefs.fetchLogos)
    var lastBackup by mutableStateOf(prefs.lastBackup)
    var locked by mutableStateOf(prefs.onboarded && prefs.lockEnabled)
    var theme by mutableStateOf(runCatching { AppTheme.valueOf(prefs.theme) }.getOrDefault(AppTheme.SYSTEM))
    var lockTimeout by mutableStateOf(
        LockTimeout.values().firstOrNull { it.seconds == prefs.lockTimeoutSeconds } ?: LockTimeout.IMMEDIATELY
    )
    var screenSecurity by mutableStateOf(prefs.screenSecurity)
    var quickActions by mutableStateOf(prefs.quickActions)
    private var backgroundTimestamp: Long = 0L

    var draft by mutableStateOf<WalletCard?>(null)
    var detected by mutableStateOf<Detected?>(null)
    var busyText by mutableStateOf<String?>(null)

    /** Set before launching a system picker / share sheet so leaving the app doesn't re-lock it. */
    private var skipNextLock = false
    fun external() { skipNextLock = true }
    fun lockNow() {
        if (skipNextLock) { skipNextLock = false; return }
        if (lockEnabled && onboarded) locked = true
    }

    fun onBackground() {
        backgroundTimestamp = System.currentTimeMillis()
        if (lockTimeout == LockTimeout.IMMEDIATELY) {
            lockNow()
        }
    }

    fun onForeground() {
        if (skipNextLock) { skipNextLock = false; return }
        if (lockEnabled && onboarded && !locked && backgroundTimestamp > 0L) {
            val elapsedSec = (System.currentTimeMillis() - backgroundTimestamp) / 1000
            if (elapsedSec >= lockTimeout.seconds) {
                locked = true
            }
        }
    }

    fun card(id: String): WalletCard? = cards.value.firstOrNull { it.id == id }
    fun save(c: WalletCard) = repo.upsert(c)
    fun delete(id: String) {
        if (pinnedCardId == id) {
            pinnedCardId = null
            prefs.pinnedCardId = null
        }
        repo.delete(id)
    }

    fun togglePinCard(id: String) {
        val newPinned = if (pinnedCardId == id) null else id
        pinnedCardId = newPinned
        prefs.pinnedCardId = newPinned
        val updated = cards.value.map { it.copy(isPinned = (it.id == newPinned)) }
        repo.saveAll(updated)
    }
    fun doc(id: String): WalletDocument? = documents.value.firstOrNull { it.id == id }
    fun saveDoc(d: WalletDocument) = docRepo.upsert(d)
    fun deleteDoc(id: String) {
        if (pinnedDocId == id) {
            pinnedDocId = null
            prefs.pinnedDocId = null
        }
        docRepo.delete(id)
    }

    fun togglePinDoc(id: String) {
        val newPinned = if (pinnedDocId == id) null else id
        pinnedDocId = newPinned
        prefs.pinnedDocId = newPinned
        val updated = documents.value.map { it.copy(isPinned = (it.id == newPinned)) }
        docRepo.saveAll(updated)
    }

    fun finishOnboarding(name: String, lock: Boolean, avatarPath: String? = null) {
        userName = name; prefs.userName = name
        lockEnabled = lock; prefs.lockEnabled = lock
        userAvatarPath = avatarPath; prefs.userAvatarPath = avatarPath
        onboarded = true; prefs.onboarded = true
    }

    fun rename(name: String) { userName = name; prefs.userName = name }
    fun setLock(v: Boolean) { lockEnabled = v; prefs.lockEnabled = v }
    fun updateIllustrations(v: Boolean) { illustrations = v; prefs.illustrations = v }
    fun updateFetchLogos(v: Boolean) { fetchLogos = v; prefs.fetchLogos = v }
    fun updateKeepPinnedExpanded(v: Boolean) { keepPinnedExpanded = v; prefs.keepPinnedExpanded = v }
    fun markBackup() { lastBackup = System.currentTimeMillis(); prefs.lastBackup = lastBackup }
    fun setAvatar(path: String?) {
        userAvatarPath = path
        prefs.userAvatarPath = path
    }

    fun updateTheme(t: AppTheme) {
        theme = t
        prefs.theme = t.name
    }

    fun updateLockTimeout(t: LockTimeout) {
        lockTimeout = t
        prefs.lockTimeoutSeconds = t.seconds
    }

    fun updateScreenSecurity(v: Boolean) {
        screenSecurity = v
        prefs.screenSecurity = v
    }
    fun toggleQuickAction(id: String) {
        val current = quickActions.toMutableSet()
        if (current.contains(id)) {
            if (current.size > 1) current.remove(id)
        } else {
            current.add(id)
        }
        quickActions = current
        prefs.quickActions = current
    }

    fun importCards(list: List<WalletCard>): Int {
        val have = cards.value.map { it.id }.toSet()
        val fresh = list.filter { it.id !in have }
        fresh.forEach { repo.upsert(it) }
        return fresh.size
    }

    fun importUri(uri: Uri, isDocument: Boolean = false, onDone: () -> Unit) {
        viewModelScope.launch {
            busyText = "Reading file on your phone"
            try {
                val d = withContext(Dispatchers.Default) { Importer.analyze(getApplication<Application>(), uri) }
                detected = d
                draft = d.card.copy(isDocument = isDocument)
                if (isDocument) {
                    draftDoc = WalletDocument(
                        id = d.card.id,
                        title = d.card.name.ifBlank { "Document" },
                        docType = "Document",
                        issuer = d.card.issuer,
                        uriPath = d.card.frontImagePath ?: "",
                        mimeType = "image/jpeg",
                        pageCount = 1,
                        pagePaths = listOfNotNull(d.card.frontImagePath),
                        fields = d.card.fields,
                    )
                }
                onDone()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication<Application>(), "Couldn't read that file", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                busyText = null
            }
        }
    }

    fun importTwoWay(front: android.graphics.Bitmap, back: android.graphics.Bitmap?, isDocument: Boolean = false, onDone: () -> Unit) {
        viewModelScope.launch {
            busyText = if (isDocument) "Analyzing document with on-app ML" else "Analyzing card with on-app ML"
            try {
                val d = withContext(Dispatchers.Default) {
                    Importer.analyzeTwoWay(getApplication<Application>(), front, back)
                }
                detected = d
                draft = d.card.copy(isDocument = isDocument)
                if (isDocument) {
                    draftDoc = WalletDocument(
                        id = d.card.id,
                        title = d.card.name.ifBlank { "Scanned document" },
                        docType = "Document",
                        issuer = d.card.issuer,
                        uriPath = d.card.frontImagePath ?: "",
                        mimeType = "image/jpeg",
                        pageCount = 1,
                        pagePaths = listOfNotNull(d.card.frontImagePath),
                        fields = d.card.fields,
                    )
                }
                onDone()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication<Application>(), "Failed to read: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                busyText = null
            }
        }
    }
}
