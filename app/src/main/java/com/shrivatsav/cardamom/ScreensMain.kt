package com.shrivatsav.cardamom

import java.io.File
import androidx.core.content.FileProvider
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// ---------------- Wallet ----------------

@Composable
fun WalletScreen(
    vm: AppViewModel,
    onAddCard: () -> Unit,
    onAddDocument: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenDoc: (String) -> Unit,
    onEditCard: (String) -> Unit = {},
    onShareCard: (String) -> Unit = {},
    onEditDoc: (String) -> Unit = {},
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cards by vm.cards.collectAsState()
    val documents by vm.documents.collectAsState()
    val selectedTab = vm.homeTab
    var q by remember { mutableStateOf("") }
    var expandedCardId by remember { mutableStateOf<String?>(null) }
    var expandedDocId by remember { mutableStateOf<String?>(null) }
    var activeCardToolbar by remember { mutableStateOf<WalletCard?>(null) }
    var cardToDelete by remember { mutableStateOf<WalletCard?>(null) }
    var activeDocToolbar by remember { mutableStateOf<WalletDocument?>(null) }
    var docToDelete by remember { mutableStateOf<WalletDocument?>(null) }
    LaunchedEffect(Unit) {
        vm.checkPeriodicSupportPrompt()
    }

    val pinnedCard = remember(cards, vm.pinnedCardId) {
        cards.firstOrNull { it.id == vm.pinnedCardId }
    }
    val otherCards = remember(cards, q, vm.pinnedCardId) {
        cards
            .filter { it.id != vm.pinnedCardId }
            .sortedByDescending { it.createdAt }
            .filter {
                q.isBlank() || it.name.contains(q, true) || it.category.contains(q, true) || it.issuer.contains(q, true)
            }
    }

    val pinnedDoc = remember(documents, vm.pinnedDocId) {
        documents.firstOrNull { it.id == vm.pinnedDocId }
    }
    val otherDocs = remember(documents, q, vm.pinnedDocId) {
        documents
            .filter { it.id != vm.pinnedDocId }
            .sortedByDescending { it.createdAt }
            .filter {
                q.isBlank() || it.title.contains(q, true) || it.docType.contains(q, true) || it.issuer.contains(q, true)
            }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Wallet", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                UserAvatar(
                    name = vm.userName,
                    avatarPath = vm.userAvatarPath,
                    size = 48.dp,
                    onClick = onSettings,
                )
            }

            // Two tabs: Cards and Documents
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(C.Surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Choice(
                    text = "Cards",
                    selected = selectedTab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.homeTab = 0 },
                )
                Choice(
                    text = "Documents",
                    selected = selectedTab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.homeTab = 1 },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).height(52.dp).clip(RoundedCornerShape(26.dp)).background(C.Surface).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, "Search", tint = C.Muted)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    textStyle = TextStyle(color = C.Cream, fontSize = 16.sp, fontFamily = GoogleSansFlexFamily), cursorBrush = SolidColor(C.Yellow),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text("Search cards", color = C.Muted, fontSize = 16.sp)
                        inner()
                    },
                )
            }

            if (selectedTab == 0) {
                if (cards.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("No cards yet", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text("Tap Add card to scan your first one.", color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp))
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (pinnedCard != null && (q.isBlank() || pinnedCard.name.contains(q, true) || pinnedCard.category.contains(q, true) || pinnedCard.issuer.contains(q, true))) {
                            item(key = "pinned_${pinnedCard.id}") {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = "Pinned",
                                                tint = C.Yellow,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Pinned", color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(C.Surface)
                                                .clickable { vm.togglePinCard(pinnedCard.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Unpin",
                                                tint = C.Muted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    val pinnedCardExpanded = if (vm.keepPinnedExpanded) true else (expandedCardId == pinnedCard.id)
                                    CardTile(
                                        card = pinnedCard,
                                        expanded = pinnedCardExpanded,
                                        art = vm.illustrations,
                                        onClick = {
                                            if (vm.keepPinnedExpanded) onOpenCard(pinnedCard.id)
                                            else {
                                                if (pinnedCardExpanded) onOpenCard(pinnedCard.id) else expandedCardId = pinnedCard.id
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeCardToolbar = pinnedCard
                                        },
                                    )
                                }
                            }
                        }
                        items(otherCards, key = { it.id }) { c ->
                            val open = expandedCardId == c.id
                            CardTile(
                                card = c,
                                expanded = open,
                                art = vm.illustrations,
                                onClick = {
                                    if (open) onOpenCard(c.id) else expandedCardId = c.id
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeCardToolbar = c
                                },
                            )
                        }
                    }
                }
            } else {
                if (documents.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("No documents yet", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text("Tap Add document to scan or upload an ID, certificate, or receipt.", color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp))
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (pinnedDoc != null && (q.isBlank() || pinnedDoc.title.contains(q, true) || pinnedDoc.docType.contains(q, true) || pinnedDoc.issuer.contains(q, true))) {
                            item(key = "pinned_doc_${pinnedDoc.id}") {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = "Pinned",
                                                tint = C.Yellow,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Pinned", color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(C.Surface)
                                                .clickable { vm.togglePinDoc(pinnedDoc.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Unpin",
                                                tint = C.Muted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    val pinnedDocExpanded = if (vm.keepPinnedExpanded) true else (expandedDocId == pinnedDoc.id)
                                    DocumentTile(
                                        doc = pinnedDoc,
                                        expanded = pinnedDocExpanded,
                                        onToggleExpand = {
                                            if (vm.keepPinnedExpanded) onOpenDoc(pinnedDoc.id)
                                            else expandedDocId = if (pinnedDocExpanded) null else pinnedDoc.id
                                        },
                                        onViewDetails = { onOpenDoc(pinnedDoc.id) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeDocToolbar = pinnedDoc
                                        },
                                    )
                                }
                            }
                        }
                        items(otherDocs, key = { it.id }) { d ->
                            val open = expandedDocId == d.id
                            DocumentTile(
                                doc = d,
                                expanded = open,
                                onToggleExpand = { expandedDocId = if (open) null else d.id },
                                onViewDetails = { onOpenDoc(d.id) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeDocToolbar = d
                                },
                            )
                        }
                    }
                }
            }
        }

        PrimaryButton(
            text = if (selectedTab == 0) "+ Add card" else "+ Add document",
            onClick = if (selectedTab == 0) onAddCard else onAddDocument,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        )

        if (activeCardToolbar != null) {
            val targetCard = activeCardToolbar!!
            ItemPreviewToolbarModal(
                onDismiss = { activeCardToolbar = null },
                actions = vm.quickActions,
                isPinned = (vm.pinnedCardId == targetCard.id) || targetCard.isPinned,
                onAction = { actionId ->
                    when (actionId) {
                        "delete" -> {
                            cardToDelete = targetCard
                            activeCardToolbar = null
                        }
                        "edit" -> onEditCard(targetCard.id)
                        "share" -> onShareCard(targetCard.id)
                        "pin" -> vm.togglePinCard(targetCard.id)
                        "details" -> onOpenCard(targetCard.id)
                    }
                },
            ) {
                CardTile(
                    card = targetCard,
                    expanded = true,
                    art = vm.illustrations,
                )
            }
        }

        if (cardToDelete != null) {
            val c = cardToDelete!!
            CardamomConfirmModal(
                title = "Delete this card?",
                message = "This card, its barcode, and any stored photos or files will be permanently removed from this phone.",
                confirmText = "Delete card",
                confirmColor = C.Danger,
                onConfirm = {
                    val id = c.id
                    cardToDelete = null
                    vm.delete(id)
                },
                onDismiss = { cardToDelete = null },
            )
        }

        if (activeDocToolbar != null) {
            val targetDoc = activeDocToolbar!!
            ItemPreviewToolbarModal(
                onDismiss = { activeDocToolbar = null },
                actions = vm.quickActions,
                isPinned = (vm.pinnedDocId == targetDoc.id) || targetDoc.isPinned,
                onAction = { actionId ->
                    when (actionId) {
                        "delete" -> {
                            docToDelete = targetDoc
                            activeDocToolbar = null
                        }
                        "edit" -> onEditDoc(targetDoc.id)
                        "share" -> {
                            vm.external()
                            Share.sendDocument(ctx, targetDoc)
                        }
                        "pin" -> vm.togglePinDoc(targetDoc.id)
                        "details" -> onOpenDoc(targetDoc.id)
                    }
                },
            ) {
                DocumentTile(
                    doc = targetDoc,
                    expanded = true,
                    onToggleExpand = {},
                    onViewDetails = { onOpenDoc(targetDoc.id) },
                )
            }
        }

        if (docToDelete != null) {
            val d = docToDelete!!
            CardamomConfirmModal(
                title = "Delete this document?",
                message = "This document and all its stored pages will be permanently removed from this phone.",
                confirmText = "Delete document",
                confirmColor = C.Danger,
                onConfirm = {
                    val id = d.id
                    docToDelete = null
                    vm.deleteDoc(id)
                },
                onDismiss = { docToDelete = null },
            )
        }
    }
}

// ---------------- Scan ----------------

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    onPreviewViewReady: (PreviewView) -> Unit,
    onBarcodeDetected: ((String, Int) -> Unit)? = null,
    onCameraReady: ((Camera) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val owner = ctx.findActivity() as LifecycleOwner
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    LaunchedEffect(previewView) { onPreviewViewReady(previewView) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(ctx)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(input)
                    .addOnSuccessListener { codes ->
                        val hit = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                        if (hit != null) onBarcodeDetected?.invoke(hit.rawValue!!, hit.format)
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            p.unbindAll()
            val camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            onCameraReady?.invoke(camera)
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun CardFrameOverlay(
    step: Int,
    liveCode: String?,
    isDocument: Boolean = false,
    isPortraitCard: Boolean = false,
    isLandscapeDoc: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val cardWidth = if (isDocument) {
            if (isLandscapeDoc) maxWidth * 0.90f else maxWidth * 0.85f
        } else if (isPortraitCard) {
            maxHeight * 0.60f / 1.586f
        } else {
            maxWidth * 0.90f
        }
        val cardHeight = if (isDocument) {
            if (isLandscapeDoc) cardWidth / 1.35f else cardWidth * 1.35f
        } else if (isPortraitCard) {
            maxHeight * 0.60f
        } else {
            cardWidth / 1.586f
        }
        val isDetected = !isDocument && liveCode != null
        val borderColor = if (isDetected) Color(0xFFA8D8B4) else Color.White
        val bracketDefaultColor = C.Yellow

        Canvas(Modifier.fillMaxSize()) {
            val cw = cardWidth.toPx()
            val ch = cardHeight.toPx()
            val left = (size.width - cw) / 2f
            val top = (size.height - ch) / 2f

            val cardRect = Rect(left, top, left + cw, top + ch)
            val fullRect = Rect(0f, 0f, size.width, size.height)
            val path = Path().apply {
                addRect(fullRect)
                addRoundRect(RoundRect(cardRect, CornerRadius(24f, 24f)))
                fillType = PathFillType.EvenOdd
            }
            drawPath(path, Color.Black.copy(alpha = 0.55f))

            drawRoundRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(cw, ch),
                cornerRadius = CornerRadius(24f, 24f),
                style = Stroke(width = if (isDetected) 6f else 4f),
            )

            if (isDocument) {
                val bracketLen = 32f
                val bracketW = 7f
                // Top-Left corner
                drawLine(bracketDefaultColor, Offset(left, top + 16f), Offset(left, top + 16f + bracketLen), bracketW, StrokeCap.Round)
                drawLine(bracketDefaultColor, Offset(left + 16f, top), Offset(left + 16f + bracketLen, top), bracketW, StrokeCap.Round)
                // Top-Right corner
                drawLine(bracketDefaultColor, Offset(left + cw, top + 16f), Offset(left + cw, top + 16f + bracketLen), bracketW, StrokeCap.Round)
                drawLine(bracketDefaultColor, Offset(left + cw - 16f, top), Offset(left + cw - 16f - bracketLen, top), bracketW, StrokeCap.Round)
                // Bottom-Left corner
                drawLine(bracketDefaultColor, Offset(left, top + ch - 16f), Offset(left, top + ch - 16f - bracketLen), bracketW, StrokeCap.Round)
                drawLine(bracketDefaultColor, Offset(left + 16f, top + ch), Offset(left + 16f + bracketLen, top + ch), bracketW, StrokeCap.Round)
                // Bottom-Right corner
                drawLine(bracketDefaultColor, Offset(left + cw, top + ch - 16f), Offset(left + cw, top + ch - 16f - bracketLen), bracketW, StrokeCap.Round)
                drawLine(bracketDefaultColor, Offset(left + cw - 16f, top + ch), Offset(left + cw - 16f - bracketLen, top + ch), bracketW, StrokeCap.Round)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cardHeight + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isDocument) {
                    if (isLandscapeDoc) "Align horizontal document inside frame" else "Align vertical document inside frame"
                } else if (isPortraitCard) {
                    "Align portrait ID badge inside frame"
                } else if (isDetected) {
                    "Barcode detected"
                } else {
                    "Align card inside frame"
                },
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
fun ScanScreen(
    vm: AppViewModel,
    isDocument: Boolean = false,
    onBack: () -> Unit,
    onCode: () -> Unit,
    onImported: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.CAMERA) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.importUri(uri, isDocument = isDocument, onDone = onImported) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importUri(uri, isDocument = isDocument, onDone = onImported) }
    val docPages = remember { mutableStateListOf<Bitmap>() }
    val multiPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            if (isDocument) {
                vm.importDocumentUris(uris, onDone = onImported)
            } else {
                uris.firstOrNull()?.let { vm.importUri(it, isDocument = false, onDone = onImported) }
            }
        }
    }
    var step by remember { mutableStateOf(1) }
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var liveCode by remember { mutableStateOf<String?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var isPortraitCard by remember { mutableStateOf(false) }
    var isLandscapeDoc by remember { mutableStateOf(false) }

    fun cropCardFrame(): Bitmap? {
        val pv = previewViewRef ?: return null
        val full = pv.bitmap ?: return null
        val targetW: Int
        val targetH: Int
        if (isDocument) {
            if (isLandscapeDoc) {
                targetW = (full.width * 0.90f).toInt().coerceAtLeast(10)
                targetH = (targetW / 1.35f).toInt().coerceAtLeast(10)
            } else {
                targetW = (full.width * 0.85f).toInt().coerceAtLeast(10)
                targetH = (targetW * 1.35f).toInt().coerceAtLeast(10)
            }
        } else if (isPortraitCard) {
            targetH = (full.height * 0.65f).toInt().coerceAtLeast(10)
            targetW = (targetH / 1.586f).toInt().coerceAtLeast(10)
        } else {
            targetW = (full.width * 0.90f).toInt().coerceAtLeast(10)
            targetH = (targetW / 1.586f).toInt().coerceAtLeast(10)
        }
        val left = ((full.width - targetW) / 2).coerceAtLeast(0)
        val top = ((full.height - targetH) / 2).coerceAtLeast(0)
        val safeW = minOf(targetW, full.width - left)
        val safeH = minOf(targetH, full.height - top)
        return Bitmap.createBitmap(full, left, top, safeW, safeH)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", onClick = {
                if (step == 2) step = 1 else onBack()
            })
            val yellow = C.Yellow
            val muted = C.Muted
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isDocument) "Scan document" else "Scan card", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!isDocument) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = yellow, fontWeight = FontWeight.SemiBold)) {
                                append(if (step == 1) "Step 1 of 2" else "Step 2 of 2")
                            }
                            withStyle(SpanStyle(color = muted)) {
                                append(if (step == 1) " · Front" else " · Back")
                            }
                        },
                        fontSize = 13.sp,
                    )
                } else {
                    Text(if (isLandscapeDoc) "Horizontal document" else "Vertical document", color = muted, fontSize = 13.sp)
                }
            }
            if (isDocument) {
                CircleIcon(
                    if (isLandscapeDoc) Icons.Default.CropLandscape else Icons.Default.CropPortrait,
                    if (isLandscapeDoc) "Switch to vertical document" else "Switch to horizontal document",
                    onClick = { isLandscapeDoc = !isLandscapeDoc }
                )
                Spacer(Modifier.width(8.dp))
            } else {
                CircleIcon(
                    if (isPortraitCard) Icons.Default.CropPortrait else Icons.Default.CropLandscape,
                    if (isPortraitCard) "Switch to landscape card" else "Switch to portrait ID badge",
                    onClick = { isPortraitCard = !isPortraitCard }
                )
                Spacer(Modifier.width(8.dp))
            }
            CircleIcon(
                if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                "Flashlight",
                onClick = {
                    torchOn = !torchOn
                    cameraRef?.cameraControl?.enableTorch(torchOn)
                },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF0E0E0E))) {
            if (granted) {
                CameraPreview(
                    onPreviewViewReady = { previewViewRef = it },
                    onBarcodeDetected = { code, format ->
                        if (liveCode == null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        liveCode = "${CodeFormat.fromMl(format).label}: $code"
                    },
                    onCameraReady = { cameraRef = it },
                    modifier = Modifier.fillMaxSize(),
                )
                CardFrameOverlay(
                    step = step,
                    liveCode = liveCode,
                    isDocument = isDocument,
                    isPortraitCard = isPortraitCard,
                    isLandscapeDoc = isLandscapeDoc,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Camera access is needed to scan", textAlign = TextAlign.Center, fontSize = 17.sp)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Allow camera", { perm.launch(Manifest.permission.CAMERA) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (isDocument) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Top: Full-width Capture Button
                Button(
                    onClick = {
                        isCapturing = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val b = cropCardFrame()
                        if (b != null) {
                            docPages.add(b)
                        }
                        isCapturing = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Yellow),
                    enabled = !isCapturing,
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF161616),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (docPages.isEmpty()) "Capture Page 1" else "Capture Page ${docPages.size + 1}",
                        color = Color(0xFF161616),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Bottom: [ Count ] on left, [ Done ] on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: Page Count Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(C.Surface)
                            .border(1.dp, C.Outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${docPages.size}",
                                color = if (docPages.isNotEmpty()) C.Yellow else C.Muted,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (docPages.size == 1) "page captured" else "pages captured",
                                color = C.Muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Right: Done Button
                    Button(
                        onClick = {
                            if (docPages.isNotEmpty()) {
                                isCapturing = true
                                vm.importDocumentPages(docPages.toList(), onDone = onImported)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (docPages.isNotEmpty()) Color(0xFF2E7D32) else C.Surface,
                            disabledContainerColor = C.Surface,
                        ),
                        enabled = docPages.isNotEmpty() && !isCapturing,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (docPages.isNotEmpty()) Color.White else C.Muted.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Done",
                            color = if (docPages.isNotEmpty()) Color.White else C.Muted.copy(alpha = 0.4f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else if (step == 1) {
            PrimaryButton("Capture Front", onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val b = cropCardFrame()
                if (b != null) {
                    frontBitmap = b
                    step = 2
                    liveCode = null
                }
            })
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Photo", onClick = { vm.external(); photo.launch("image/*") }, modifier = Modifier.weight(1f))
                SecondaryButton("PDF or file", onClick = { vm.external(); file.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.weight(1f))
            }
        } else {
            PrimaryButton("Capture Back", onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val back = cropCardFrame()
                frontBitmap?.let { f ->
                    vm.importTwoWay(f, back, isDocument = isDocument, onDone = onImported)
                }
            })
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Photo", onClick = { vm.external(); photo.launch("image/*") }, modifier = Modifier.weight(1f))
                SecondaryButton("Skip back side", onClick = {
                    frontBitmap?.let { f ->
                        vm.importTwoWay(f, null, isDocument = isDocument, onDone = onImported)
                    }
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ReviewScreen(vm: AppViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val d = vm.detected
    val draft = vm.draft
    if (d == null || draft == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var idx by remember { mutableStateOf(0) }
    var showBackThumb by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        TopBar("Review card", onBack)
        Spacer(Modifier.height(10.dp))

        // Front / Back Pill Switcher (only shown if back side was actually scanned)
        val hasBack = (d.backThumb != null) || (draft.backImagePath != null)
        if (hasBack) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(C.Surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Choice("Front", !showBackThumb, Modifier.weight(1f)) { showBackThumb = false }
                Choice("Back", showBackThumb, Modifier.weight(1f)) { showBackThumb = true }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Card Preview - formatted to match the scanned image's true aspect ratio
        val currentThumb = if (showBackThumb && d.backThumb != null) d.backThumb else (d.frontThumb ?: d.thumb)
        val isPortrait = currentThumb != null && currentThumb.height > currentThumb.width
        val aspect = if (currentThumb != null && currentThumb.height > 0) {
            (currentThumb.width.toFloat() / currentThumb.height.toFloat()).coerceIn(0.5f, 2.2f)
        } else 1.586f

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (currentThumb != null) {
                Box(
                    modifier = (if (isPortrait) Modifier.fillMaxWidth(0.68f) else Modifier.fillMaxWidth())
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, C.Outline.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = currentThumb.asImageBitmap(),
                        contentDescription = "Card photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    val primaryTitle = draft.name.ifBlank { draft.category.ifBlank { draft.issuer.ifBlank { "Card" } } }
                    val secondaryText = if (draft.issuer.isNotBlank() && !draft.issuer.equals(primaryTitle, ignoreCase = true)) {
                        draft.issuer
                    } else if (draft.name.isNotBlank() && !draft.category.equals(primaryTitle, ignoreCase = true)) {
                        draft.category
                    } else ""
                    Text(
                        text = primaryTitle,
                        color = C.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (secondaryText.isNotBlank()) {
                        Text(
                            text = secondaryText,
                            color = C.Ink.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        val isEngineReady = (vm.currentOcrEngine == AppOcrEngine.MLKIT) || vm.isAiModelReady
        if (!isEngineReady) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.Yellow.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ℹ️", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Manual entry: PaddleOCR package not downloaded. Download in Settings to enable auto-fill.",
                        color = C.Cream,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Text(
            if (isEngineReady) "Auto-filled with on-device AI" else "Read on your phone (manual entry)",
            color = if (isEngineReady) C.Mint else C.Muted,
            fontSize = 14.sp,
            fontWeight = if (isEngineReady) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        // Attributes container
        Group {
            Row2("Category") { MutedText(draft.category) }
            if (draft.issuer.isNotBlank()) {
                Sep()
                Row2("Issuer") {
                    Text(
                        draft.issuer,
                        color = C.Muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.End,
                        lineHeight = 18.sp,
                    )
                }
            }
            if (draft.fields.isNotEmpty()) {
                draft.fields.forEach { f ->
                    Sep()
                    Row2(f.label) { MutedText(f.value) }
                }
            }
            Sep()
            Row2("Code found") {
                MutedText(if (draft.code.isBlank()) "None detected" else draft.format.label)
            }
        }

        Text(
            "Check these before saving. Title and ID number stay blank for you.",
            color = C.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )

        PrimaryButton("Continue", onNext)
        Spacer(Modifier.height(16.dp))
    }
}
