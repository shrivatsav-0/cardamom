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

    private val distribution = DistributionBridge.createController(app, prefs, viewModelScope)

    val licenseState: LicenseState get() = distribution.licenseState
    val isPaywallLocked: Boolean get() = distribution.isPaywallLocked
    var showPurchaseSuccess: Boolean
        get() = distribution.showPurchaseSuccess
        set(value) { distribution.showPurchaseSuccess = value }
    val showTrialEndingSheet: Boolean get() = distribution.showTrialEndingSheet
    val premiumPrice: String get() = distribution.premiumPrice

    val cards = repo.cards
    private val docRepo = DocumentRepository(app)
    val documents = docRepo.documents
    var draftDoc by mutableStateOf<WalletDocument?>(null)
    var homeTab by mutableStateOf(0)
    var pinnedCardId by mutableStateOf(prefs.pinnedCardId)
    var pinnedDocId by mutableStateOf(prefs.pinnedDocId)
    var keepPinnedExpanded by mutableStateOf(prefs.keepPinnedExpanded)

    init {
        AiModelManager.initStatus(app)
    }

    val aiModelStatus = AiModelManager.status
    val isAiModelReady: Boolean get() = AiModelManager.isModelReady(getApplication())
    var aiModelSkipped by mutableStateOf(prefs.aiModelSkipped)
    var pendingUserName by mutableStateOf(prefs.userName)
    var pendingLock by mutableStateOf(prefs.lockEnabled)
    var pendingAvatarPath by mutableStateOf(prefs.userAvatarPath)
    var currentScriptPack by mutableStateOf(ModelScriptPack.fromId(prefs.aiModelScript))

    fun setScriptPack(pack: ModelScriptPack) {
        currentScriptPack = pack
        prefs.aiModelScript = pack.id
    }
    var currentOcrEngine by mutableStateOf(AppOcrEngine.fromId(prefs.ocrEngine))

    fun setOcrEngine(engine: AppOcrEngine) {
        currentOcrEngine = engine
        prefs.ocrEngine = engine.id
    }

    fun setPendingProfile(name: String, lock: Boolean, avatar: String?) {
        pendingUserName = name
        pendingLock = lock
        pendingAvatarPath = avatar
        userName = name
        lockEnabled = lock
        userAvatarPath = avatar
    }

    fun skipAiModel() {
        aiModelSkipped = true
        prefs.aiModelSkipped = true
    }

    fun downloadAiModel(simulatedDelay: Boolean = true) {
        viewModelScope.launch {
            AiModelManager.downloadModel(getApplication(), simulatedDelay = simulatedDelay)
        }
    }

    fun cancelAiModelDownload() {
        AiModelManager.cancelDownload()
    }

    fun deleteAiModel() {
        AiModelManager.deleteModel(getApplication())
    }


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
    var showPeriodicSupportModal by mutableStateOf(false)
    var showDonationModal by mutableStateOf(false)

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
        distribution.onForeground()
        checkPeriodicSupportPrompt()
    }

    fun startTrial() {
        distribution.startTrial()
    }

    fun launchBilling(activity: android.app.Activity) {
        distribution.launchBilling(activity)
    }

    fun restorePurchase(onResult: (Boolean) -> Unit) {
        distribution.restorePurchase(onResult)
    }

    fun checkTrialEndingPrompt() {
        distribution.checkTrialEndingPrompt()
    }

    fun dismissTrialEndingSheet() {
        distribution.dismissTrialEndingSheet()
    }

    fun dismissPurchaseSuccess() {
        distribution.dismissPurchaseSuccess()
    }

    override fun onCleared() {
        super.onCleared()
        distribution.destroy()
    }
    fun checkPeriodicSupportPrompt() {
        if (!onboarded || locked) return
        val now = System.currentTimeMillis()
        val last = prefs.lastSupportPromptTime
        if (last == 0L) {
            prefs.lastSupportPromptTime = now
            return
        }
        if (now - last >= SUPPORT_INTERVAL_MS) {
            showPeriodicSupportModal = true
        }
    }

    fun dismissPeriodicSupportModal() {
        showPeriodicSupportModal = false
        prefs.lastSupportPromptTime = System.currentTimeMillis()
    }

    fun openDonationFromSupportModal() {
        showPeriodicSupportModal = false
        prefs.lastSupportPromptTime = System.currentTimeMillis()
        showDonationModal = true
    }


    fun card(id: String): WalletCard? = cards.value.firstOrNull { it.id == id }
    fun save(c: WalletCard) = repo.upsert(c)
    fun delete(id: String) {
        if (pinnedCardId == id) {
            pinnedCardId = null
            prefs.pinnedCardId = null
        }
        pendingDeletedCard = repo.delete(id)
    }

    // Undo-delete: files were moved to trash/, not destroyed.
    var pendingDeletedCard: WalletCard? = null
        private set
    var pendingDeletedDoc: WalletDocument? = null
        private set

    fun undoDeleteCard() {
        pendingDeletedCard?.let { repo.restore(it) }
        pendingDeletedCard = null
    }

    fun undoDeleteDoc() {
        pendingDeletedDoc?.let { docRepo.restore(it) }
        pendingDeletedDoc = null
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
        pendingDeletedDoc = docRepo.delete(id)
    }

    /**
     * Wipe all vault data: clears card/document records AND deletes every
     * stored file (photos, doc pages, originals, logos, avatars, models),
     * then resets local prefs. This is the in-app data-deletion path —
     * uninstall alone should not be the only way to erase your data.
     */
    fun wipeAllData() {
        // Delete per-item files first (repo.delete removes files per item),
        // then clear the encrypted records themselves.
        cards.value.map { it.id }.toList().forEach { runCatching { repo.delete(it) } }
        documents.value.map { it.id }.toList().forEach { runCatching { docRepo.delete(it) } }
        repo.saveAll(emptyList())
        docRepo.saveAll(emptyList())
        // Belt-and-braces: remove any orphaned files left in app storage,
        // including the undo trash (wipe must be a true erase, not a move).
        runCatching {
            val dir = getApplication<Application>().filesDir
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
        }
        // Reset preferences (onboarding, pins, trial/premium flags, lock).
        // Includes the EncryptedSharedPreferences file ("cardamom_secure").
        runCatching {
            getApplication<Application>()
                .getSharedPreferences("cardamom", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
            getApplication<Application>()
                .getSharedPreferences("cardamom_secure", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
            getApplication<Application>()
                .getSharedPreferences("cardamom_widgets", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
        pinnedCardId = null
        pinnedDocId = null
    }

    fun togglePinDoc(id: String) {
        val newPinned = if (pinnedDocId == id) null else id
        pinnedDocId = newPinned
        prefs.pinnedDocId = newPinned
        val updated = documents.value.map { it.copy(isPinned = (it.id == newPinned)) }
        docRepo.saveAll(updated)
    }

    fun finishOnboarding(name: String = pendingUserName, lock: Boolean = pendingLock, avatarPath: String? = pendingAvatarPath) {
        val finalName = name.ifBlank { pendingUserName }
        userName = finalName; prefs.userName = finalName
        lockEnabled = lock; prefs.lockEnabled = lock
        userAvatarPath = avatarPath; prefs.userAvatarPath = avatarPath
        onboarded = true; prefs.onboarded = true
        if (prefs.lastSupportPromptTime == 0L) {
            prefs.lastSupportPromptTime = System.currentTimeMillis()
        }
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

    /** Restore document metadata from a backup (page images must be re-scanned). */
    fun importDocuments(list: List<WalletDocument>): Int {
        val have = documents.value.map { it.id }.toSet()
        val fresh = list.filter { it.id !in have }
        fresh.forEach { docRepo.upsert(it) }
        return fresh.size
    }

    fun importDocumentFromUri(uri: Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            busyText = "Importing document..."
            try {
                val ctx = getApplication<Application>()
                val processed = withContext(Dispatchers.IO) {
                    DocumentEngine.importDocument(ctx, uri)
                }
                if (processed != null) {
                    val doc = processed.toWalletDocument()
                    if (processed.bitmaps.isNotEmpty()) {
                        val aiResult = withContext(Dispatchers.Default) {
                            AiVisionEngine.processBitmaps(ctx, processed.bitmaps)
                        }
                        if (aiResult is AiVisionResult.Success && aiResult.issuer.isNotBlank()) {
                            draftDoc = doc.copy(issuer = aiResult.issuer)
                        } else {
                            draftDoc = doc
                        }
                    } else {
                        draftDoc = doc
                    }
                    onDone()
                } else {
                    android.widget.Toast.makeText(ctx, "Could not read this document", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication<Application>(), "Couldn't read document: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                busyText = null
            }
        }
    }

    fun importUri(uri: Uri, isDocument: Boolean = false, onDone: () -> Unit) {
        if (isDocument) {
            importDocumentFromUri(uri, onDone)
            return
        }
        viewModelScope.launch {
            busyText = "Reading file on your phone"
            try {
                val d = withContext(Dispatchers.Default) { Importer.analyze(getApplication<Application>(), uri) }
                detected = d
                draft = d.card.copy(isDocument = false)
                onDone()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication<Application>(), "Couldn't read that file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
                        title = "",
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
    fun importDocumentPages(bitmaps: List<android.graphics.Bitmap>, onDone: () -> Unit) {
        viewModelScope.launch {
            busyText = "Processing ${bitmaps.size} document page(s)"
            try {
                val ctx = getApplication<Application>()
                val id = java.util.UUID.randomUUID().toString()
                val docDir = Files.dir(ctx, "documents")
                val pagePaths = mutableListOf<String>()
                withContext(Dispatchers.IO) {
                    bitmaps.forEachIndexed { i, bmp ->
                        val pageFile = java.io.File(docDir, "doc_${id}_page_$i.jpg")
                        VaultCrypto.compressEncryptedBitmap(pageFile, bmp, android.graphics.Bitmap.CompressFormat.JPEG, 90)
                        pagePaths.add(pageFile.absolutePath)
                    }
                }

                // Recognize with active engine (Google ML Kit on-device by default)
                val aiResult = withContext(Dispatchers.Default) {
                    AiVisionEngine.processBitmaps(ctx, bitmaps)
                }

                val issuer = when (aiResult) {
                    is AiVisionResult.Success -> aiResult.issuer
                    else -> ""
                }

                val totalSize = pagePaths.sumOf { java.io.File(it).length() }
                draftDoc = WalletDocument(
                    id = id,
                    title = "",
                    docType = "Document",
                    issuer = issuer,
                    uriPath = pagePaths.firstOrNull() ?: "",
                    mimeType = "image/jpeg",
                    pageCount = pagePaths.size.coerceAtLeast(1),
                    pagePaths = pagePaths,
                    fileSize = totalSize,
                    fields = emptyList(),
                )
                onDone()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication<Application>(), "Failed to process pages: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                busyText = null
            }
        }
    }

    fun importDocumentUris(uris: List<Uri>, onDone: () -> Unit) {
        viewModelScope.launch {
            busyText = "Importing ${uris.size} document photo(s)"
            try {
                val ctx = getApplication<Application>()
                val id = java.util.UUID.randomUUID().toString()
                val docDir = Files.dir(ctx, "documents")
                val pagePaths = mutableListOf<String>()
                val bitmaps = mutableListOf<android.graphics.Bitmap>()
                withContext(Dispatchers.IO) {
                    uris.forEachIndexed { index, uri ->
                        val dest = java.io.File(docDir, "doc_${id}_page_$index.jpg")
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        // Encrypt the stored page copy at rest (readers decrypt on load)
                        try { VaultCrypto.writeEncryptedBytes(dest, dest.readBytes()) } catch (_: Exception) {}
                        if (dest.exists() && dest.length() > 0) {
                            pagePaths.add(dest.absolutePath)
                            VaultCrypto.decodeBitmapFile(dest)?.let { bitmaps.add(it) }
                        }
                    }
                }

                // Recognize with active engine (Google ML Kit on-device by default)
                val aiResult = withContext(Dispatchers.Default) {
                    AiVisionEngine.processBitmaps(ctx, bitmaps)
                }

                val issuer = when (aiResult) {
                    is AiVisionResult.Success -> aiResult.issuer
                    else -> ""
                }

                val totalSize = pagePaths.sumOf { java.io.File(it).length() }
                draftDoc = WalletDocument(
                    id = id,
                    title = "",
                    docType = "Document",
                    issuer = issuer,
                    uriPath = pagePaths.firstOrNull() ?: "",
                    mimeType = "image/jpeg",
                    pageCount = pagePaths.size.coerceAtLeast(1),
                    pagePaths = pagePaths,
                    fileSize = totalSize,
                    fields = emptyList(),
                )
                onDone()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication<Application>(), "Failed to import photos: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                busyText = null
            }
        }
    }

    fun addPagesToDraftDoc(newUris: List<Uri>) {
        val cur = draftDoc ?: return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val docDir = Files.dir(ctx, "documents")
            val newPaths = mutableListOf<String>()
            var nextIndex = cur.pagePaths.size
            withContext(Dispatchers.IO) {
                newUris.forEach { uri ->
                    val stream = DocumentEngine.openUriStream(ctx, uri)
                    if (stream != null) {
                        val temp = java.io.File(docDir, "temp_add_${java.util.UUID.randomUUID()}")
                        stream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                        val pages = DocumentEngine.renderFilePages(temp)
                        temp.delete()
                        for (bmp in pages) {
                            val dest = java.io.File(docDir, "doc_${cur.id}_page_$nextIndex.jpg")
                            VaultCrypto.compressEncryptedBitmap(dest, bmp, android.graphics.Bitmap.CompressFormat.JPEG, 90)
                            newPaths.add(dest.absolutePath)
                            nextIndex++
                        }
                    }
                }
            }
            if (newPaths.isNotEmpty()) {
                val updatedPaths = cur.pagePaths + newPaths
                val totalSize = updatedPaths.sumOf { java.io.File(it).length() }
                draftDoc = cur.copy(
                    pagePaths = updatedPaths,
                    pageCount = updatedPaths.size,
                    fileSize = totalSize,
                )
            }
        }
    }

    fun removePageFromDraftDoc(index: Int) {
        val cur = draftDoc ?: return
        if (index !in cur.pagePaths.indices) return
        val pathToRemove = cur.pagePaths[index]
        try { java.io.File(pathToRemove).delete() } catch (e: Exception) {}
        val updatedPaths = cur.pagePaths.toMutableList().apply { removeAt(index) }
        val primaryPath = updatedPaths.firstOrNull() ?: ""
        val totalSize = updatedPaths.sumOf { java.io.File(it).length() }
        draftDoc = cur.copy(
            uriPath = primaryPath,
            pagePaths = updatedPaths,
            pageCount = updatedPaths.size.coerceAtLeast(1),
            fileSize = totalSize,
        )
    }

    companion object {
        /** 75 days in milliseconds (between 2 and 3 months) */
        const val SUPPORT_INTERVAL_MS = 75L * 24 * 60 * 60 * 1000L
    }
}
