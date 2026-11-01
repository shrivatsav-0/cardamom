package com.shrivatsav.cardamom

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.fragment.app.FragmentActivity

@Composable
private fun CardFan(modifier: Modifier = Modifier) {
    val fanCodes = remember {
        listOf(
            CodeFormat.CODE128 to "CARD${(100000..999999).random()}",
            CodeFormat.QR to "cardamom://card/${java.util.UUID.randomUUID()}",
            CodeFormat.CODE128 to "LOYALTY${(1000..9999).random()}",
            CodeFormat.QR to "https://cardamom.app/${(1000..9999).random()}",
        )
    }
    val fanColors = listOf(C.Orange, C.Mint, C.Sage, C.Purple)
    val fanRotations = listOf(-20f, -10f, 4f, 14f)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        fanColors.forEachIndexed { i, c ->
            val (format, value) = fanCodes[i]
            val bmp = remember(format, value) { Codes.bitmap(value, format, if (format.is2D) 220 else 320, if (format.is2D) 220 else 110) }
            Box(
                Modifier
                    .size(200.dp, 260.dp)
                    .rotate(fanRotations[i])
                    .clip(RoundedCornerShape(28.dp))
                    .background(c)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) Image(
                        bmp.asImageBitmap(),
                        "Code",
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
        Box(Modifier.size(150.dp, 100.dp).rotate(14f).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.35f)))
    }
}

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(40.dp)
            Spacer(Modifier.width(12.dp))
            Text("Cardamom", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(36.dp))
        Text(
            buildAnnotatedString {
                append("Keep your\nloyalty ")
                withStyle(SpanStyle(background = C.Yellow, color = C.Ink)) { append(" safe ") }
            },
            fontSize = 46.sp, lineHeight = 49.sp, fontWeight = FontWeight.SemiBold,
        )
        Text("Every store card and code in one place, private on your phone.", color = C.Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 14.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) { CardFan() }
        Dots(0, total = 6)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Next", onNext)
    }
}

@Composable
private fun Bullet(t: String) {
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(C.Yellow))
        Spacer(Modifier.width(12.dp))
        Text(t, fontSize = 17.sp)
    }
}

@Composable
fun ScanIntroScreen(onBack: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        TopBar("", onBack)
        Text("Scan it once,\nuse it anywhere", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Box(Modifier.fillMaxWidth().height(230.dp).padding(top = 20.dp), contentAlignment = Alignment.Center) {
            Corners(Modifier.fillMaxSize())
            Column(
                Modifier.rotate(-5f).size(220.dp, 130.dp).clip(RoundedCornerShape(24.dp)).background(C.Orange).padding(16.dp),
            ) {
                Text("Bloom & Ember", color = C.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.85f)).padding(6.dp), contentAlignment = Alignment.Center) {
                     val bmp = remember { Codes.bitmap("BLOOM482910", CodeFormat.CODE128, 400, 120) }
                     if (bmp != null) Image(bmp.asImageBitmap(), "Code", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                }
            }
        }
        Bullet("Point your camera at any barcode or QR")
        Bullet("Or pick a photo, or type the number")
        Bullet("Cards stay only on your phone")
        Spacer(Modifier.weight(1f))
        Dots(1, total = 6)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Next", onNext)
    }
}

@Composable
fun UsernameScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf(vm.pendingUserName) }
    var lock by remember { mutableStateOf(if (vm.pendingUserName.isNotBlank()) vm.pendingLock else true) }
    var avatarPath by remember { mutableStateOf<String?>(vm.pendingAvatarPath) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val saved = Files.saveUserAvatar(ctx, uri)
            if (saved != null) avatarPath = saved
        }
    }

    fun submit() {
        if (name.isBlank()) return
        keyboard?.hide()
        val ok = lock && Auth.available(ctx)
        if (lock && !ok) Toast.makeText(ctx, "Set up a screen lock in Android settings to use app lock", Toast.LENGTH_LONG).show()
        vm.setPendingProfile(name.trim(), ok, avatarPath)
        onDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minH = maxHeight - 48.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minH),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    TopBar("", onBack)
                    Text("What should we call\nyou?", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(28.dp))

                    // Centered circular profile photo picker
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatarBmp = remember(avatarPath) {
                            avatarPath?.let { BitmapFactory.decodeFile(it) }
                        }
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(CircleShape)
                                .clickable {
                                    vm.external()
                                    photoPicker.launch("image/*")
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = Color(0xFF4A4A4A),
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f), 0f),
                                    ),
                                )
                            }
                            if (avatarBmp != null) {
                                Image(
                                    bitmap = avatarBmp.asImageBitmap(),
                                    contentDescription = "Profile photo",
                                    modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Add photo",
                                        tint = C.Muted,
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Add photo",
                                        color = C.Muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))

                    CField(
                        label = "Username",
                        value = name,
                        onChange = { name = it.take(24) },
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            if (name.isNotBlank()) submit() else keyboard?.hide()
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    ToggleRow("Lock with fingerprint or PIN", "Recommended", lock) { lock = it }
                }
                Column(Modifier.padding(top = 28.dp)) {
                    Dots(2, total = 6)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Next", onClick = { submit() }, enabled = name.isNotBlank())
                }
            }
        }
    }
}
@Composable
fun ModelDownloadScreen(vm: AppViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val ctx = LocalContext.current
    val status by vm.aiModelStatus.collectAsState()
    var showSkipModal by remember { mutableStateOf(false) }
    var scriptModal by remember { mutableStateOf(false) }
    val catalog = AiModelManager.catalog

    val isReady = status is AiModelStatus.Ready || vm.isAiModelReady
    val isDownloading = status is AiModelStatus.Downloading || status is AiModelStatus.Verifying
    val isError = status is AiModelStatus.Error

    var isInitiating by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        TopBar("", onBack)
        Text(
            "Multilingual on-device\nAI recognition",
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Transcribe cards, IDs & documents in multiple scripts locally. Your documents never touch a cloud server.",
            color = C.Muted,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        // Features using clean vector icons
        Group {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Yellow.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = C.Yellow, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("100% Offline & Private", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Runs on your phone. No external servers or tracking.", color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Sep()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Orange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = C.Orange, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Multilingual & Multi-Script", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Recognizes Indian Devanagari, English, and international scripts.", color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Sep()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Mint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = C.Mint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dynamic Field Extraction", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Auto-detects cardholders, institutions, dates, IDs, and attributes.", color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Sep()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Purple.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = C.Purple, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Open Source & Compact", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Lightweight model (~16.8 MB) stored safely in app storage.", color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Active Engine Presentation Card
        Group {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "OCR ENGINE",
                    color = C.Yellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Google ML Kit",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = C.Cream,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Default engine · On-device recognition",
                            color = C.Muted,
                            fontSize = 13.sp,
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.Mint.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "✓ Ready",
                            color = C.Mint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Dots(3, total = 6)
        Spacer(Modifier.height(18.dp))

        PrimaryButton("Continue", onClick = onNext)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SetupCompleteScreen(
    vm: AppViewModel,
    onStart: () -> Unit,
    onAddFirstCard: () -> Unit,
) {
    val ctx = LocalContext.current
    val isAiReady = vm.isAiModelReady

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minH = maxHeight - 48.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minH),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(C.Yellow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", color = C.Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("You're all set!", fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Your private cardamom wallet is configured and ready to use.",
                        color = C.Muted,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
                    )

                    // Setup Summary Card
                    Group {
                        Row2("Wallet Owner") {
                            Text(
                                vm.pendingUserName.ifBlank { vm.userName.ifBlank { "You" } },
                                color = C.Cream,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            )
                        }
                        Sep()
                        Row2("App Lock") {
                            Text(
                                if (vm.pendingLock) "Fingerprint / PIN Protected" else "Unlocked",
                                color = if (vm.pendingLock) C.Mint else C.Muted,
                                fontSize = 14.sp,
                            )
                        }
                        Sep()
                        Row2("OCR Engine") {
                            Text(
                                text = "Google ML Kit (Default)",
                                color = C.Mint,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Tips Banner
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(C.Surface)
                            .padding(16.dp)
                    ) {
                        Column {
                            Text("What to do next", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (isAiReady)
                                    "Point your camera at any store card, loyalty pass, ID, or ticket. Cardamom will auto-fill your numbers and attributes."
                                else
                                    "Add cards by scanning barcodes or typing numbers. You can download the on-device AI model anytime in Settings for automatic field recognition.",
                                color = C.Muted,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            )
                        }
                    }
                }

                Column(Modifier.padding(top = 28.dp)) {
                    Dots(5, total = 6)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Start adding cards", onClick = {
                        vm.finishOnboarding()
                        onAddFirstCard()
                    })
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Go to wallet", onClick = {
                        vm.finishOnboarding()
                        onStart()
                    })
                }
            }
        }
    }
}

@Composable
fun DeveloperSupportScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingDeveloperSupportScreen(vm = vm, onBack = onBack, onNext = onNext)
}

@Composable
fun UnlockScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val act = ctx.findActivity() as? FragmentActivity
    fun go(credentialOnly: Boolean) {
        if (!Auth.available(ctx)) { vm.locked = false; return }
        act?.let { Auth.prompt(it, credentialOnly) { vm.locked = false } }
    }
    LaunchedEffect(Unit) { go(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AppIcon(80.dp)
            Text(
                text = if (vm.userName.isNotBlank()) "Welcome back, ${vm.userName}" else "Welcome back",
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Unlock to see your cards",
                color = C.Muted,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(56.dp))
            Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { go(false) }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FingerprintIcon()
                Spacer(Modifier.height(12.dp))
                Text("Unlock with fingerprint", fontSize = 17.sp, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
        SecondaryButton("Use PIN instead", onClick = { go(true) })
    }
}
