package com.shrivatsav.cardamom

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------- New card / edit card ----------------

@Composable
fun EditScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { if (id == "new") (vm.draft ?: WalletCard()) else (vm.card(id) ?: WalletCard()) }
    var c by remember { mutableStateOf(initial) }
    var keepOriginal by remember { mutableStateOf(true) }
    var isDirectEdit by remember { mutableStateOf(false) }
    var editModalField by remember { mutableStateOf<String?>(null) }
    var editAttributeIndex by remember { mutableStateOf<Int?>(null) }
    var categoryModal by remember { mutableStateOf(false) }
    var newFieldDialog by remember { mutableStateOf(false) }
    var newFieldLabel by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 8.dp)) {
        TopBar(
            title = if (id == "new") "New card" else "Edit card",
            onBack = onBack,
            trailing = {
                CircleIcon(if (isDirectEdit) Icons.Default.Check else Icons.Default.Edit, "Toggle edit fields") {
                    isDirectEdit = !isDirectEdit
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        CardTile(c, expanded = false, art = vm.illustrations)
        Spacer(Modifier.height(16.dp))

        if (!isDirectEdit) {
            BlockField("Card name", c.name, "Tap to enter card name") { isDirectEdit = true }
            Spacer(Modifier.height(12.dp))
            BlockField("Institution / Company", c.issuer, "Tap to enter institution or company") { isDirectEdit = true }
            Spacer(Modifier.height(12.dp))
            BlockField(
                label = "Category",
                value = c.category,
                placeholder = "Tap to select category",
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.Muted) },
                onClick = { categoryModal = true },
            )
            Spacer(Modifier.height(16.dp))
            // Read-only Code card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Code (${c.format.label})", color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = c.code.ifBlank { "No code scanned" },
                        color = C.Cream,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            CField("Card name", c.name, { c = c.copy(name = it) }, singleLine = false, maxLines = 4)
            Spacer(Modifier.height(12.dp))
            CField("Institution / Company", c.issuer, { c = c.copy(issuer = it) }, singleLine = false, maxLines = 4)
            Spacer(Modifier.height(12.dp))
            BlockField(
                label = "Category",
                value = c.category,
                placeholder = "Tap to select category",
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.Muted) },
                onClick = { categoryModal = true },
            )
            Spacer(Modifier.height(16.dp))
            // Read-only Code card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Code (${c.format.label})", color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = c.code.ifBlank { "No code scanned" },
                        color = C.Cream,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (c.fields.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Dynamic attributes", color = C.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            c.fields.forEachIndexed { i, f ->
                if (!isDirectEdit) {
                    BlockField(
                        label = f.label,
                        value = f.value,
                        placeholder = "Tap to enter value",
                        trailing = {
                            CircleIcon(Icons.Default.Close, "Remove") {
                                val list = c.fields.toMutableList()
                                list.removeAt(i)
                                c = c.copy(fields = list)
                            }
                        },
                        onClick = { isDirectEdit = true },
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CField(
                            label = f.label,
                            value = f.value,
                            onChange = { v ->
                                val list = c.fields.toMutableList()
                                list[i] = f.copy(value = v)
                                c = c.copy(fields = list)
                            },
                            singleLine = false,
                            maxLines = 6,
                            modifier = Modifier.weight(1f),
                        )
                        CircleIcon(Icons.Default.Close, "Remove") {
                            val list = c.fields.toMutableList()
                            list.removeAt(i)
                            c = c.copy(fields = list)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton("+ Add custom attribute", onClick = {
            newFieldLabel = ""
            newFieldValue = ""
            newFieldDialog = true
        })
        Spacer(Modifier.height(18.dp))
        Text("Card color", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            C.CardColors.forEachIndexed { i, col ->
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(col)
                        .then(if (c.colorIndex == i) Modifier.border(3.dp, C.Cream, CircleShape) else Modifier)
                        .clickable { c = c.copy(colorIndex = i) }
                )
            }
        }
        if (c.originalPath != null) {
            Spacer(Modifier.height(14.dp))
            ToggleRow("Keep original file", "Stored with the card", keepOriginal) { keepOriginal = it }
        }
        Spacer(Modifier.height(20.dp))
        val canSave = c.name.isNotBlank() && c.category.isNotBlank()
        PrimaryButton("Save card", enabled = canSave, onClick = {
            scope.launch {
                vm.busyText = "Saving"
                var card = c
                if (card.name.isBlank()) {
                    card = card.copy(name = card.issuer.ifBlank { if (card.isDocument) "Document" else "Card" })
                }
                if (!keepOriginal && card.originalPath != null) {
                    File(card.originalPath!!).delete()
                    card = card.copy(originalPath = null, originalName = null, originalMime = null)
                }
                if (card.logoMode == LogoMode.AUTO && card.website.isNotBlank() && vm.fetchLogos && card.logoPath == null) {
                    val p = withContext(Dispatchers.IO) { Favicon.fetch(ctx, card.id, card.website) }
                    if (p != null) card = card.copy(logoPath = p)
                }
                vm.save(card)
                vm.draft = null; vm.detected = null
                vm.busyText = null
                onSaved(card.id)
            }
        })
        Spacer(Modifier.height(16.dp))
    }


    if (newFieldDialog) {
        CardamomModal(
            onDismiss = { newFieldDialog = false },
            title = "Add custom attribute",
            subtitle = "Define a dynamic field like Department, Seat, or Validity",
        ) {
            CField("Attribute name (e.g. Department, Seat)", newFieldLabel, { newFieldLabel = it })
            Spacer(Modifier.height(12.dp))
            CField("Value", newFieldValue, { newFieldValue = it }, singleLine = false, maxLines = 2)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Add attribute",
                onClick = {
                    c = c.copy(fields = c.fields + CardField(newFieldLabel.trim(), newFieldValue.trim()))
                    newFieldDialog = false
                },
                enabled = newFieldLabel.isNotBlank() && newFieldValue.isNotBlank(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    if (categoryModal) {
        CardamomSelectModal(
            title = "Category",
            options = listOf(
                "Loyalty Card",
                "Library Card",
                "Institutional ID",
                "Movie Ticket",
                "Event Ticket",
                "Flight Ticket",
                "Transit Pass",
                "Membership",
                "Coupon",
                "Card",
            ),
            selected = c.category,
            optionLabel = { it },
            onSelect = {
                c = c.copy(category = it)
                categoryModal = false
            },
            onDismiss = { categoryModal = false },
        )
    }
    if (editModalField == "name") {
        FieldEditModal("Card name", c.name, singleLine = false, maxLines = 4, onConfirm = { c = c.copy(name = it) }, onDismiss = { editModalField = null })
    }
    if (editModalField == "issuer") {
        FieldEditModal("Institution / Company", c.issuer, singleLine = false, maxLines = 4, onConfirm = { c = c.copy(issuer = it) }, onDismiss = { editModalField = null })
    }
    if (editAttributeIndex != null && editAttributeIndex!! in c.fields.indices) {
        val f = c.fields[editAttributeIndex!!]
        FieldEditModal(f.label, f.value, singleLine = false, maxLines = 6, onConfirm = { v ->
            val list = c.fields.toMutableList()
            list[editAttributeIndex!!] = f.copy(value = v)
            c = c.copy(fields = list)
            editAttributeIndex = null
        }, onDismiss = { editAttributeIndex = null })
    }
}

// ---------------- Card detail ----------------

@Composable
fun DetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit) {
    val cards by vm.cards.collectAsState()
    val card = cards.firstOrNull { it.id == id }
    val lastKnownCard = remember { mutableStateOf(card) }
    if (card != null) {
        lastKnownCard.value = card
    }
    val displayCard = card ?: lastKnownCard.value
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var showFileViewer by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val w = ctx.findActivity()?.window
        if (w != null) { val lp = w.attributes; lp.screenBrightness = 1f; w.attributes = lp }
        onDispose {
            if (w != null) { val lp = w.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = lp }
        }
    }

    if (displayCard == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var viewMode by remember { mutableStateOf(0) }
    val frontBmp = remember(displayCard.frontImagePath) {
        displayCard.frontImagePath?.let { BitmapFactory.decodeFile(it) }
    }
    val backBmp = remember(displayCard.backImagePath) {
        displayCard.backImagePath?.let { BitmapFactory.decodeFile(it) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        TopBar("Card", onBack, trailing = {
            StarButton(
                pinned = (vm.pinnedCardId == displayCard.id) || displayCard.isPinned,
                desc = if (displayCard.isPinned) "Unpin card" else "Pin to top",
                onClick = {
                    vm.togglePinCard(displayCard.id)
                },
            )
        })
        Spacer(Modifier.height(10.dp))
        if (frontBmp != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Digital card", viewMode == 0, Modifier.weight(1f)) { viewMode = 0 }
                Choice("Front photo", viewMode == 1, Modifier.weight(1f)) { viewMode = 1 }
                if (backBmp != null) {
                    Choice("Back photo", viewMode == 2, Modifier.weight(1f)) { viewMode = 2 }
                }
            }
        }
        if (viewMode == 0) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(C.CardColors[displayCard.colorIndex.mod(C.CardColors.size)])) {
                if (vm.illustrations) Illustration(displayCard.seed, Modifier.matchParentSize())
                Column(Modifier.padding(22.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val issuerName = displayCard.issuer.ifBlank { displayCard.name.ifBlank { "Card" } }
                            Text(
                                text = issuerName,
                                color = C.Ink,
                                fontSize = if (issuerName.contains('\n') || issuerName.length > 24) 20.sp else 24.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subName = when {
                                displayCard.issuer.isNotBlank() && displayCard.name.isNotBlank() && !displayCard.name.equals(displayCard.issuer, true) -> displayCard.name
                                else -> displayCard.category
                            }
                            Text(
                                text = subName,
                                color = C.Ink.copy(alpha = 0.75f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (displayCard.code.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        CodePanel(displayCard, barHeight = 130.dp, allowCopyModal = true)
                    }
                    if (displayCard.fields.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        CardFieldsRow(displayCard)
                    } else if (displayCard.points.isNotBlank() || displayCard.status.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        PointsRow(displayCard)
                    }
                }
            }
        } else if (viewMode == 1) {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0E0E0E)), contentAlignment = Alignment.Center) {
                if (frontBmp != null) {
                    Image(frontBmp.asImageBitmap(), "Front of card", Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0E0E0E)), contentAlignment = Alignment.Center) {
                if (backBmp != null) {
                    Image(backBmp.asImageBitmap(), "Back of card", Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                }
            }
        }
        if (displayCard.fields.isNotEmpty() || displayCard.issuer.isNotBlank()) {
            GroupTitle("Card attributes")
            Group {
                if (displayCard.issuer.isNotBlank()) {
                    CardAttributeTwoLine(label = "Institution / Company", value = displayCard.issuer)
                    if (displayCard.fields.isNotEmpty()) Sep()
                }
                displayCard.fields.forEachIndexed { i, f ->
                    if (i > 0) Sep()
                    CardAttributeRow(label = f.label, value = f.value)
                }
            }
        }
        if (displayCard.originalPath != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(C.Surface)
                    .clickable { showFileViewer = true }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Original file", fontSize = 16.sp, maxLines = 1)
                    Text(displayCard.originalName ?: "", color = C.Muted, fontSize = 13.sp, maxLines = 1)
                }
                Text("View", color = C.Yellow, fontSize = 16.sp)
            }
        }
        Text("Screen brightness boosted for scanning", color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Share", onShare, Modifier.weight(1f))
            SecondaryButton("Edit", onEdit, Modifier.weight(1f))
            SecondaryButton("Delete", { confirm = true }, Modifier.weight(1f), color = C.Danger)
        }
        Spacer(Modifier.height(12.dp))
    }
    if (confirm) {
        CardamomConfirmModal(
            title = "Delete this card?",
            message = "This card, its barcode, and any stored photos or files will be permanently removed from this phone.",
            confirmText = "Delete card",
            confirmColor = C.Danger,
            onConfirm = {
                confirm = false
                vm.delete(id)
                onBack()
            },
            onDismiss = { confirm = false },
        )
    }
    if (showFileViewer && displayCard.originalPath != null) {
        InAppFileViewerDialog(
            filePath = displayCard.originalPath!!,
            fileName = displayCard.originalName ?: "Original file",
            mimeType = displayCard.originalMime,
            onDismiss = { showFileViewer = false },
        )
    }
}

// ---------------- Share ----------------

@Composable
private fun ShareOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(20.dp)).background(C.Surface)
            .then(if (selected) Modifier.border(2.dp, C.Yellow, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(title, fontSize = 17.sp, maxLines = 1)
        Text(sub, color = C.Muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun ShareScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val cards by vm.cards.collectAsState()
    val card = cards.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(0) }
    var include by remember { mutableStateOf(true) }
    var includeLinkData by remember { mutableStateOf(false) }

    if (card == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val hasCode = card.code.isNotBlank()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Share", fontSize = 42.sp, fontWeight = FontWeight.SemiBold)
            CircleIcon(Icons.Default.Close, "Close", onBack)
        }
        Text("Send ${card.name} as an image or a file. Nothing is uploaded.", color = C.Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
        Box(Modifier.padding(top = 32.dp, bottom = 28.dp, start = 16.dp, end = 4.dp).rotate(-6f)) {
            CardTile(card, expanded = include && hasCode, art = vm.illustrations, showCodeText = includeLinkData)
        }
        ShareOption("Share as image", "PNG of the card", sel == 0) { sel = 0 }
        ShareOption("Save to gallery", "Keep a copy on this phone", sel == 1) { sel = 1 }
        ShareOption("Export card file", "Import it on another phone", sel == 2) { sel = 2 }
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            "Include barcode",
            if (hasCode) "${card.format.label} found on this card" else "No code on this card",
            include && hasCode,
        ) { if (hasCode) include = it }
        if (sel != 2 && hasCode && include) {
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                if (card.format.is2D) "Include QR link / data" else "Include barcode data",
                if (card.format.is2D) "Display the actual link or text below the QR code" else "Display the raw data below the barcode",
                includeLinkData,
            ) { includeLinkData = it }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Share", {
            val showData = includeLinkData && include && hasCode
            when (sel) {
                0 -> { vm.external(); Share.send(ctx, Share.imageFile(ctx, card, include && hasCode, showData), "image/png") }
                1 -> Share.saveToGallery(ctx, card, include && hasCode, showData)
                else -> { vm.external(); Share.send(ctx, Share.cardFile(ctx, card), "application/octet-stream") }
            }
        })
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun InAppFileViewerDialog(
    filePath: String,
    fileName: String,
    mimeType: String?,
    onDismiss: () -> Unit,
) {
    val file = remember(filePath) { File(filePath) }
    val pdfPages = remember(filePath) {
        if (!file.exists()) emptyList<Bitmap>()
        else {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val count = minOf(renderer.pageCount, 20)
                val pages = (0 until count).map { i ->
                    val page = renderer.openPage(i)
                    val scale = minOf(1200f / page.width.coerceAtLeast(1), 2f).coerceAtLeast(1f)
                    val w = (page.width * scale).toInt()
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }
                renderer.close()
                pfd.close()
                pages
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val imageBmp = remember(filePath, pdfPages) {
        if (pdfPages.isNotEmpty() || !file.exists()) null
        else BitmapFactory.decodeFile(file.absolutePath)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = C.Bg,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIcon(Icons.Default.Close, "Close", onDismiss)
                    Text(
                        text = fileName,
                        color = C.Cream,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(44.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0E0E0E)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pdfPages.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(pdfPages.size) { i ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White),
                                ) {
                                    Image(
                                        bitmap = pdfPages[i].asImageBitmap(),
                                        contentDescription = "Page ${i + 1}",
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.FillWidth,
                                    )
                                }
                            }
                        }
                    } else if (imageBmp != null) {
                        Image(
                            bitmap = imageBmp.asImageBitmap(),
                            contentDescription = fileName,
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("Unable to open file preview", color = C.Muted, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
