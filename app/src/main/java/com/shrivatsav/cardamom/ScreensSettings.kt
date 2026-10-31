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
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onBackup: () -> Unit) {
    val ctx = LocalContext.current
    var renameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var themeDialog by remember { mutableStateOf(false) }
    var lockTimeoutDialog by remember { mutableStateOf(false) }
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
        }
        GroupTitle("Data")
        Group {
            Row2("Backup & restore", onClick = onBackup) { MutedText("Open") }
            Sep()
            Row2("Fetch logos online") { AppSwitch(vm.fetchLogos) { vm.updateFetchLogos(it) } }
        }
        GroupTitle("About")
        Group { Row2("Cardamom") { MutedText("Version 1.0") } }
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
