package com.shrivatsav.cardamom

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onBackup: () -> Unit) {
    val ctx = LocalContext.current
    var renameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var themeDialog by remember { mutableStateOf(false) }
    var lockTimeoutDialog by remember { mutableStateOf(false) }
    var quickActionsDialog by remember { mutableStateOf(false) }
    var aiModelDialog by remember { mutableStateOf(false) }
    var confirmDeleteAiModel by remember { mutableStateOf(false) }
    var ocrEngineDialog by remember { mutableStateOf(false) }
    var privacyModal by remember { mutableStateOf(false) }
    var termsModal by remember { mutableStateOf(false) }
    var donationModal by remember { mutableStateOf(false) }
    val aiStatus by vm.aiModelStatus.collectAsState()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val saved = Files.saveUserAvatar(ctx, uri)
            if (saved != null) vm.setAvatar(saved)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 8.dp)) {
        TopBar("Settings", onBack)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp)).background(C.Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                name = vm.userName,
                avatarPath = vm.userAvatarPath,
                size = 56.dp,
                editBadge = true,
                onClick = {
                    vm.external()
                    photoPicker.launch("image/*")
                },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable { newName = vm.userName; renameDialog = true }) {
                Text(vm.userName, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    if (vm.userAvatarPath != null) "Tap photo to change" else "Tap photo to add avatar",
                    color = C.Muted,
                    fontSize = 13.sp,
                )
            }
            Text(
                "Edit",
                color = C.Yellow,
                modifier = Modifier.clickable { newName = vm.userName; renameDialog = true },
            )
        }
        GroupTitle("Security")
        Group {
            Row2("App lock") {
                AppSwitch(vm.lockEnabled) { on ->
                    if (on && !Auth.available(ctx)) Toast.makeText(ctx, "Set up a screen lock in Android settings first", Toast.LENGTH_LONG).show()
                    else vm.setLock(on)
                }
            }
            Sep()
            Row2("Lock after", onClick = { lockTimeoutDialog = true }) {
                MutedText(vm.lockTimeout.label)
            }
            Sep()
            Row2("Block screenshots & recents") {
                AppSwitch(vm.screenSecurity) { vm.updateScreenSecurity(it) }
            }
        }
        GroupTitle("Appearance")
        Group {
            Row2("Theme", onClick = { themeDialog = true }) {
                MutedText(vm.theme.label)
            }
            Sep()
            Row2("Always enlarge pinned items") {
                AppSwitch(vm.keepPinnedExpanded) { vm.updateKeepPinnedExpanded(it) }
            }
            Sep()
            Row2("Card illustrations") { AppSwitch(vm.illustrations) { vm.updateIllustrations(it) } }
            Sep()
            Row2("Floating quick actions", onClick = { quickActionsDialog = true }) {
                MutedText("${vm.quickActions.size} active")
            }
        }
        GroupTitle("AI & Recognition")
        Group {
            Row2("OCR Engine", onClick = { ocrEngineDialog = true }) {
                MutedText(vm.currentOcrEngine.displayName)
            }
            Sep()
            Row2("Engine Status") {
                val statusText = when (vm.currentOcrEngine) {
                    AppOcrEngine.MLKIT -> "Ready (Default · On-device)"
                    AppOcrEngine.PADDLE -> if (vm.isAiModelReady) "Ready (Local model)" else "Needs download (Tap to setup)"
                }
                Text(
                    text = statusText,
                    color = if (vm.currentOcrEngine == AppOcrEngine.MLKIT || vm.isAiModelReady) C.Mint else C.Yellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        if (vm.currentOcrEngine == AppOcrEngine.PADDLE && !vm.isAiModelReady) {
                            aiModelDialog = true
                        }
                    }
                )
            }
            if (vm.currentOcrEngine == AppOcrEngine.PADDLE) {
                Sep()
                Row2("PaddleOCR Package", onClick = { aiModelDialog = true }) {
                    MutedText(if (vm.isAiModelReady) "Installed (16.8 MB)" else "Not installed (Tap to download)")
                }
            }
        }
        GroupTitle("Data")
        Group {
            Row2("Backup & restore", onClick = onBackup) { MutedText("Open") }
        }
        GroupTitle("Developer & Socials")
        Group {
            SettingLinkRow(
                title = "LinkedIn",
                subtitle = SupportLinks.LINKEDIN_DISPLAY,
                onClick = {
                    vm.external()
                    SupportLinks.openUrl(ctx, SupportLinks.LINKEDIN)
                },
            )
            Sep()
            SettingLinkRow(
                title = "GitHub",
                subtitle = SupportLinks.GITHUB_DISPLAY,
                onClick = {
                    vm.external()
                    SupportLinks.openUrl(ctx, SupportLinks.GITHUB)
                },
            )
            Sep()
            SettingLinkRow(
                title = "Personal Website",
                subtitle = SupportLinks.WEBSITE_DISPLAY,
                onClick = {
                    vm.external()
                    SupportLinks.openUrl(ctx, SupportLinks.WEBSITE)
                },
            )
            Sep()
            SettingLinkRow(
                title = "Email Developer",
                subtitle = SupportLinks.EMAIL,
                onClick = {
                    vm.external()
                    SupportLinks.sendEmail(ctx)
                },
            )
        }
        GroupTitle("Support & Donations")
        Group {
            SettingLinkRow(
                title = "Donate & Support",
                subtitle = "Ko-fi, UPI (${SupportLinks.UPI_ID}) & QR code",
                badge = "Donate",
                onClick = { donationModal = true },
            )
        }
        GroupTitle("Legal & About")
        Group {
            Row2("Privacy Policy", onClick = { privacyModal = true }) { MutedText("Read") }
            Sep()
            Row2("Terms of Service", onClick = { termsModal = true }) { MutedText("Read") }
            Sep()
            Row2("Cardamom", onClick = {}) { MutedText("Version 1.0 · Offline First") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (renameDialog) {
        CardamomModal(
            onDismiss = { renameDialog = false },
            title = "Profile details",
            subtitle = "Manage your wallet username and profile photo",
        ) {
            CField("Username", newName, { newName = it.take(24) })
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = if (vm.userAvatarPath != null) "Change photo" else "Add photo",
                    onClick = {
                        renameDialog = false
                        vm.external()
                        photoPicker.launch("image/*")
                    },
                    modifier = Modifier.weight(1f),
                )
                if (vm.userAvatarPath != null) {
                    SecondaryButton(
                        text = "Remove photo",
                        onClick = {
                            vm.setAvatar(null)
                        },
                        color = C.Danger,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Save changes",
                onClick = {
                    if (newName.isNotBlank()) vm.rename(newName.trim())
                    renameDialog = false
                },
                enabled = newName.isNotBlank(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (themeDialog) {
        CardamomSelectModal(
            title = "Theme",
            subtitle = "Choose how Cardamom looks on your phone",
            options = AppTheme.values().toList(),
            selected = vm.theme,
            optionLabel = { it.label },
            optionDescription = {
                when (it) {
                    AppTheme.SYSTEM -> "Match your Android device appearance"
                    AppTheme.DARK -> "Rich obsidian surface with cream accents"
                    AppTheme.LIGHT -> "Warm clean canvas with crisp typography"
                }
            },
            onSelect = {
                vm.updateTheme(it)
                themeDialog = false
            },
            onDismiss = { themeDialog = false },
        )
    }

    if (lockTimeoutDialog) {
        CardamomSelectModal(
            title = "Lock after",
            subtitle = "Automatically lock wallet when app is in the background",
            options = LockTimeout.values().toList(),
            selected = vm.lockTimeout,
            optionLabel = { it.label },
            optionDescription = {
                when (it) {
                    LockTimeout.IMMEDIATELY -> "Lock as soon as you leave the app"
                    LockTimeout.SECS_15 -> "Lock after 15 seconds in the background"
                    LockTimeout.MIN_1 -> "Lock after 1 minute in the background"
                    LockTimeout.MIN_5 -> "Lock after 5 minutes in the background"
                }
            },
            onSelect = {
                vm.updateLockTimeout(it)
                lockTimeoutDialog = false
            },
            onDismiss = { lockTimeoutDialog = false },
        )
    }
    if (quickActionsDialog) {
        CardamomModal(
            onDismiss = { quickActionsDialog = false },
            title = "Floating quick actions",
            subtitle = "Customise the actions shown when long-pressing a card or document",
        ) {
            listOf(
                "delete" to ("Delete" to "Remove card or document from wallet"),
                "edit" to ("Edit" to "Open editor directly"),
                "share" to ("Share" to "Export image, file, or send"),
                "pin" to ("Pin / Unpin" to "Toggle pin status on home screen"),
                "details" to ("View details" to "Open full detail viewer"),
            ).forEach { (id, info) ->
                val (title, sub) = info
                val isChecked = vm.quickActions.contains(id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { vm.toggleQuickAction(id) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = C.Cream)
                        Text(sub, fontSize = 13.sp, color = C.Muted)
                    }
                    AppSwitch(checked = isChecked, onChange = { vm.toggleQuickAction(id) })
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Done", onClick = { quickActionsDialog = false })
            Spacer(Modifier.height(8.dp))
        }
    }
    if (aiModelDialog) {
        val catalog = AiModelManager.catalog
        val isReady = aiStatus is AiModelStatus.Ready || vm.isAiModelReady
        val isDownloading = aiStatus is AiModelStatus.Downloading || aiStatus is AiModelStatus.Verifying
        val isError = aiStatus is AiModelStatus.Error

        CardamomModal(
            onDismiss = { aiModelDialog = false },
            title = "On-Device AI Engine",
            subtitle = "Transcribe photos & auto-fill dynamic fields locally",
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Group {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(catalog.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("v${catalog.version} • Open Source (${catalog.license})", color = C.Muted, fontSize = 12.sp)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isReady -> C.Mint.copy(alpha = 0.2f)
                                        isDownloading -> C.Yellow.copy(alpha = 0.2f)
                                        isError -> C.Danger.copy(alpha = 0.2f)
                                        else -> Color(0xFF2C2C2C)
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = when {
                                    isReady -> "✓ Active"
                                    aiStatus is AiModelStatus.Downloading -> "${((aiStatus as AiModelStatus.Downloading).progress * 100).toInt()}%"
                                    aiStatus is AiModelStatus.Verifying -> "Verifying"
                                    isError -> "Error"
                                    else -> "Not installed"
                                },
                                color = when {
                                    isReady -> C.Mint
                                    isDownloading -> C.Yellow
                                    isError -> C.Danger
                                    else -> C.Muted
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (isDownloading) {
                        val s = aiStatus
                        if (s is AiModelStatus.Downloading) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = C.Yellow,
                                    trackColor = Color(0xFF2C2C2C),
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val downloadedMb = s.downloadedBytes / (1024f * 1024f)
                                    val totalMb = s.totalBytes / (1024f * 1024f)
                                    val speedMb = s.speedBps / (1024f * 1024f)
                                    Text(String.format("%.1f MB / %.1f MB", downloadedMb, totalMb), color = C.Muted, fontSize = 12.sp)
                                    Text(String.format("%.1f MB/s", speedMb), color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "This on-device AI model powers image text transcription and dynamic field extraction (cardholder names, member IDs, expiration dates, loyalty points, tiers, and travel attributes). Everything stays private on your phone.",
                    color = C.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(20.dp))

                if (isReady) {
                    SecondaryButton("Remove AI model (Free up 15.8 MB)", onClick = {
                        confirmDeleteAiModel = true
                    })
                } else if (isDownloading) {
                    SecondaryButton("Cancel download", onClick = { vm.cancelAiModelDownload() })
                } else {
                    PrimaryButton("Download AI model (15.8 MB)", onClick = { vm.downloadAiModel() })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirmDeleteAiModel) {
        CardamomConfirmModal(
            onDismiss = { confirmDeleteAiModel = false },
            title = "Delete on-device AI model?",
            message = "Removing the AI model will free 15.8 MB of storage, but automatic OCR and dynamic field filling will be disabled. You will have to pre-fill all fields manually.",
            confirmText = "Delete model",
            confirmColor = C.Danger,
            onConfirm = {
                vm.deleteAiModel()
                confirmDeleteAiModel = false
                Toast.makeText(ctx, "AI model removed", Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (ocrEngineDialog) {
        CardamomSelectModal(
            title = "OCR Engine",
            subtitle = "Choose text recognition engine for cards and documents",
            options = AppOcrEngine.values().toList(),
            selected = vm.currentOcrEngine,
            optionLabel = { it.displayName },
            optionDescription = { it.subtitle },
            onSelect = { selectedEngine ->
                vm.setOcrEngine(selectedEngine)
                ocrEngineDialog = false
                if (selectedEngine == AppOcrEngine.PADDLE && !vm.isAiModelReady) {
                    aiModelDialog = true
                }
            },
            onDismiss = { ocrEngineDialog = false },
        )
    }

    if (privacyModal) {
        CardamomModal(
            onDismiss = { privacyModal = false },
            title = "Privacy Policy",
            subtitle = "Zero tracking. Local encryption. Your data stays on your phone.",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Cardamom is designed from the ground up as a private, offline-first digital wallet and document organizer.\n\n" +
                        "1. Zero Cloud Transmission\n" +
                        "Your cards, documents, barcodes, images, and personal attributes are stored strictly on your local device. We run zero remote servers, maintain zero analytics, and collect zero telemetry.\n\n" +
                        "2. Hardware-Backed Encryption\n" +
                        "All vault data is encrypted using AES-256-GCM with keys managed by the hardware-backed Android Keystore. Encrypted records cannot be accessed outside the authenticated application context.\n\n" +
                        "3. On-Device AI & OCR\n" +
                        "All document scanning, image transcription, and barcode detection operate 100% on your device using on-device ML Kit and local models. No camera frames or images ever leave your device.\n\n" +
                        "4. Backups & Portability\n" +
                        "When you export a backup, the encrypted payload is encrypted with PBKDF2-SHA256 and AES-GCM using your custom password. You maintain total custody and sovereignty over your backup files.\n\n" +
                        "5. Data Deletion\n" +
                        "Deleting a card, document, or wiping the app permanently removes the plaintext and associated image files from your phone.",
                    color = C.Cream,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Close", onClick = { privacyModal = false })
            }
        }
    }

    if (termsModal) {
        CardamomModal(
            onDismiss = { termsModal = false },
            title = "Terms of Service",
            subtitle = "Open source, personal wallet software.",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Welcome to Cardamom. By using this application, you agree to the following terms:\n\n" +
                        "1. License & Ownership\n" +
                        "Cardamom is open-source software provided under the Apache 2.0 license. You are granted the right to run, inspect, and modify the application for personal and non-commercial use.\n\n" +
                        "2. Personal Custody & Device Security\n" +
                        "Cardamom is a self-custody wallet. You are solely responsible for securing your physical device, biometric authentication, and backup passwords. The developers cannot recover lost passwords or forgotten credentials.\n\n" +
                        "3. As-Is Provision\n" +
                        "The application is provided \"as is\", without warranty of any kind, express or implied. In no event shall the developers be liable for any data loss, device issues, or barcode scanning discrepancies.\n\n" +
                        "4. Offline Usage\n" +
                        "Cardamom requires no user account, subscription, or recurring payments to unlock its features.",
                    color = C.Cream,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Close", onClick = { termsModal = false })
            }
        }
    }
    if (donationModal) {
        DonationModal(
            onDismiss = { donationModal = false },
            vm = vm,
        )
    }
}

@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cards by vm.cards.collectAsState()
    var protect by remember { mutableStateOf(true) }
    var exportDialog by remember { mutableStateOf(false) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var password by remember { mutableStateOf("") }
    var pendingPass by remember { mutableStateOf<CharArray?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            try {
                val plain = Backup.build(cards)
                val bytes = pendingPass?.let { Backup.encrypt(plain, it) } ?: plain
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                vm.markBackup()
                Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Couldn't save the backup", Toast.LENGTH_SHORT).show()
            }
        }
        pendingPass = null
    }

    fun finishImport(bytes: ByteArray, pass: CharArray?) {
        try {
            val plain = if (Backup.isEncrypted(bytes)) Backup.decrypt(bytes, pass ?: CharArray(0)) else bytes
            val n = vm.importCards(Backup.parse(plain))
            Toast.makeText(ctx, "Imported $n card(s)", Toast.LENGTH_SHORT).show()
            importBytes = null
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't read that file. Wrong password?", Toast.LENGTH_LONG).show()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                if (Backup.isEncrypted(bytes)) { password = ""; importBytes = bytes } else finishImport(bytes, null)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp)) {
        TopBar("Backup", onBack)
        Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(28.dp)).background(C.Surface).padding(24.dp)) {
            Text("${cards.size} cards", fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
            Text("Stored on this phone", color = C.Muted, fontSize = 16.sp)
            Text(
                "Last backup: " + if (vm.lastBackup == 0L) "never" else DateFormat.getDateInstance().format(Date(vm.lastBackup)),
                color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        ToggleRow("Protect with a password", "Encrypts the file", protect) { protect = it }
        Text(
            "Backups hold card details only. Original files and logos stay on this phone. Nothing leaves your phone unless you send it.",
            color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Export backup file", {
            if (protect) { password = ""; exportDialog = true } else { vm.external(); pendingPass = null; create.launch("cardamom-backup.cardamom") }
        })
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Import backup file", { vm.external(); open.launch(arrayOf("*/*")) })
        Spacer(Modifier.height(12.dp))
    }

    if (exportDialog) {
        CardamomModal(
            onDismiss = { exportDialog = false },
            title = "Set backup password",
            subtitle = "Choose a password with at least 4 characters to encrypt your backup",
        ) {
            CField("Password", password, { password = it }, keyboard = KeyboardType.Password)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Encrypt & export",
                onClick = {
                    pendingPass = password.toCharArray()
                    exportDialog = false
                    vm.external()
                    create.launch("cardamom-backup.cardamom")
                },
                enabled = password.length >= 4,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    importBytes?.let { bytes ->
        CardamomModal(
            onDismiss = { importBytes = null },
            title = "Enter backup password",
            subtitle = "This backup is password-protected. Enter the password to restore.",
        ) {
            CField("Password", password, { password = it }, keyboard = KeyboardType.Password)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Unlock & import",
                onClick = {
                    finishImport(bytes, password.toCharArray())
                },
                enabled = password.isNotEmpty(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
