package com.shrivatsav.cardamom

import androidx.compose.animation.AnimatedVisibility
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
fun DocumentIcon(modifier: Modifier = Modifier, tint: Color = C.Yellow) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f),
        )
        drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.34f), androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.34f), strokeWidth = 3f)
        drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.50f), androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.50f), strokeWidth = 3f)
        drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.66f), androidx.compose.ui.geometry.Offset(w * 0.54f, h * 0.66f), strokeWidth = 3f)
    }
}

fun getDocumentImages(ctx: Context, doc: WalletDocument): List<Bitmap> {
    val fromPagePaths = doc.pagePaths.mapNotNull { path ->
        val f = File(path)
        if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(f.absolutePath) else null
    }
    if (fromPagePaths.isNotEmpty()) return fromPagePaths

    val uriFile = File(doc.uriPath)
    if (uriFile.exists() && uriFile.length() > 0 && !doc.uriPath.endsWith(".pdf", ignoreCase = true)) {
        val bmp = BitmapFactory.decodeFile(uriFile.absolutePath)
        if (bmp != null) return listOf(bmp)
    }

    val docDir = Files.dir(ctx, "documents")
    val jpegs: Array<File> = docDir.listFiles { f: File ->
        f.name.startsWith("doc_${doc.id}") &&
        (f.name.endsWith(".jpg", ignoreCase = true) || f.name.endsWith(".png", ignoreCase = true)) &&
        f.length() > 0
    } ?: emptyArray<File>()

    val fromDisk = jpegs.sortedBy { it.name }.mapNotNull { f: File -> BitmapFactory.decodeFile(f.absolutePath) }
    if (fromDisk.isNotEmpty()) return fromDisk

    return emptyList()
}

fun getDocumentPdfPages(doc: WalletDocument): List<Bitmap> {
    val pdfFile = listOfNotNull(doc.pdfPath, doc.uriPath)
        .map { File(it) }
        .firstOrNull { it.exists() && it.length() > 100 && it.name.endsWith(".pdf", ignoreCase = true) }
        ?: return emptyList()

    return try {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
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

// ---------------- Document Tile for Home Screen ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentTile(
    doc: WalletDocument,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit,
    onViewDetails: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = listOf(C.Sage, C.Orange, C.Mint, C.Purple, C.Pink)
    val bg = colors[kotlin.math.abs(doc.id.hashCode()).mod(colors.size)]

    val ctx = LocalContext.current
    val allDocPages = remember(doc, expanded) {
        if (!expanded) emptyList<Bitmap>()
        else getDocumentImages(ctx, doc).ifEmpty { getDocumentPdfPages(doc) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .combinedClickable(
                onClick = onToggleExpand,
                onLongClick = onLongClick,
            )
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title.ifBlank { doc.docType },
                    color = Color(0xFF161616),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = if (doc.issuer.isNotBlank()) doc.issuer else doc.docType
                Text(
                    text = subtitle,
                    color = Color(0xFF161616).copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                // Full width document container taking as much space as image takes (landscape or vertical)
                if (allDocPages.isNotEmpty()) {
                    val tilePagerState = rememberPagerState { allDocPages.size }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 340.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141414))
                            .clickable(onClick = onViewDetails),
                        contentAlignment = Alignment.Center,
                    ) {
                        HorizontalPager(
                            state = tilePagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val bmp = allDocPages[page]
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Document page ${page + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.High,
                            )
                        }
                    }
                    if (allDocPages.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Page ${tilePagerState.currentPage + 1} of ${allDocPages.size} · Swipe to switch",
                            color = Color(0xFF161616).copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141414))
                            .clickable(onClick = onViewDetails),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                Modifier
                                    .width(70.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF8B7CF6))
                            )
                            Box(Modifier.fillMaxWidth(0.9f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Box(Modifier.fillMaxWidth(0.95f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"}",
                        color = Color(0xFF161616).copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color(0xFF161616))
                            .clickable(onClick = onViewDetails)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "View",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Document Scanner & Import Screen ----------------

@Composable
fun DocumentScanScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onScanWithCamera: () -> Unit = {},
    onScanned: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val act = ctx.findActivity()


    // PDF file picker
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                vm.busyText = "Importing PDF document"
                try {
                    val id = UUID.randomUUID().toString()
                    val fileName = Files.displayName(ctx, uri).substringBeforeLast('.')
                    val docDir = Files.dir(ctx, "documents")
                    val dest = File(docDir, "doc_$id.pdf")
                    var pageCount = 1
                    val pagePaths = mutableListOf<String>()
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (dest.exists() && dest.length() > 0) {
                            try {
                                val pfd = ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY)
                                val renderer = PdfRenderer(pfd)
                                pageCount = renderer.pageCount
                                val renderLimit = minOf(renderer.pageCount, 10)
                                for (i in 0 until renderLimit) {
                                    val page = renderer.openPage(i)
                                    val scale = minOf(1200f / page.width.coerceAtLeast(1), 2f).coerceAtLeast(1f)
                                    val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    val canvas = android.graphics.Canvas(bmp)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    page.close()
                                    val pageFile = File(docDir, "doc_${id}_page_$i.jpg")
                                    pageFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                    pagePaths.add(pageFile.absolutePath)
                                }
                                renderer.close()
                                pfd.close()
                            } catch (_: Exception) {}
                        }
                    }
                    val doc = WalletDocument(
                        id = id,
                        title = "",
                        docType = "Document",
                        uriPath = dest.absolutePath,
                        mimeType = "application/pdf",
                        pageCount = pageCount.coerceAtLeast(1),
                        pagePaths = pagePaths,
                        pdfPath = dest.absolutePath,
                        fileSize = dest.length(),
                    )
                    vm.draftDoc = doc
                    onScanned()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Couldn't read PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    vm.busyText = null
                }
            }
        }
    }

    // Photo gallery picker (supports multiple photos / pages)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            vm.importDocumentUris(uris, onDone = onScanned)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        TopBar("Add document", onBack)
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Scan or import",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = C.Cream,
        )
        Text(
            text = "Edges, perspective and shadows are fixed automatically.",
            color = C.Muted,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )

        // 1. Scan with camera (Sage)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFCFE0CC))
                .clickable {
                    onScanWithCamera()
                }
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF161616),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Scan with camera",
                        color = Color(0xFF161616),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Up to 20 pages, saved as PDF",
                        color = Color(0xFF161616).copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2. Import a PDF (Orange)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF6B04B))
                .clickable {
                    vm.external()
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF161616),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Import a PDF",
                        color = Color(0xFF161616),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pick a file from your phone",
                        color = Color(0xFF161616).copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 3. From gallery (Purple)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF8B7CF6))
                .clickable {
                    vm.external()
                    photoPicker.launch("image/*")
                }
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF161616),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "From gallery",
                        color = Color(0xFF161616),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pick one or multiple photos (pages)",
                        color = Color(0xFF161616).copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

// ---------------- Document Upload & Info Screen ----------------

@Composable
fun DocumentUploadScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onUploaded: (String) -> Unit,
) {
    val draft = vm.draftDoc
    if (draft == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var title by remember { mutableStateOf(draft.title) }
    var docType by remember { mutableStateOf(draft.docType.ifBlank { "Document" }) }
    var issuer by remember { mutableStateOf(draft.issuer) }
    var typeModal by remember { mutableStateOf(false) }
    var isDirectEdit by remember { mutableStateOf(false) }
    var editModalField by remember { mutableStateOf<String?>(null) }
    var docFields by remember { mutableStateOf(draft.fields) }
    var customLabelInput by remember { mutableStateOf("") }
    var showCustomFieldDialog by remember { mutableStateOf(false) }
    var selectedPageIndex by remember { mutableStateOf(0) }

    val addMorePhotos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            vm.addPagesToDraftDoc(uris)
        }
    }

    val docTypes = listOf(
        "National ID",
        "Passport / Visa",
        "Educational Certificate",
        "Receipt / Invoice",
        "Exit Permit",
        "Health / Vaccine Record",
        "Insurance Policy",
        "Work Permit / Contract",
        "Document",
    )

    val pageCount = draft.pagePaths.size.coerceAtLeast(1)
    val safePageIndex = selectedPageIndex.coerceIn(0, pageCount - 1)
    val currentPath = draft.pagePaths.getOrNull(safePageIndex) ?: draft.uriPath

    val previewBmp = remember(currentPath) {
        if (currentPath.endsWith(".pdf", ignoreCase = true)) {
            try {
                val f = File(currentPath)
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val page = renderer.openPage(0)
                val scale = minOf(600f / page.width.coerceAtLeast(1), 2f).coerceAtLeast(1f)
                val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bmp
            } catch (e: Exception) { null }
        } else {
            val f = File(currentPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        TopBar("New document", onBack, trailing = {
            CircleIcon(if (isDirectEdit) Icons.Default.Check else Icons.Default.Edit, "Toggle edit fields") {
                isDirectEdit = !isDirectEdit
            }
        })
        Spacer(Modifier.height(16.dp))

        val uploadPageCount = draft.pagePaths.size.coerceAtLeast(1)
        val uploadPagerState = rememberPagerState { uploadPageCount }
        LaunchedEffect(uploadPagerState.currentPage) {
            selectedPageIndex = uploadPagerState.currentPage
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, C.Outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalPager(
                    state = uploadPagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val path = draft.pagePaths.getOrNull(page) ?: ""
                    val pageBmp = remember(path) {
                        if (path.endsWith(".pdf", ignoreCase = true)) {
                            try {
                                val f = File(path)
                                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                                val renderer = PdfRenderer(pfd)
                                val p = renderer.openPage(0)
                                val scale = minOf(600f / p.width.coerceAtLeast(1), 2f).coerceAtLeast(1f)
                                val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                val canvas = android.graphics.Canvas(bmp)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                p.close()
                                renderer.close()
                                pfd.close()
                                bmp
                            } catch (e: Exception) { null }
                        } else {
                            val f = File(path)
                            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                        }
                    }

                    if (pageBmp != null) {
                        Image(
                            bitmap = pageBmp.asImageBitmap(),
                            contentDescription = "Document preview page ${page + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(Modifier.width(70.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF8B7CF6)))
                            Box(Modifier.fillMaxWidth(0.9f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Box(Modifier.fillMaxWidth(0.95f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                        }
                    }
                }
            }

            if (draft.pagePaths.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Page ${uploadPagerState.currentPage + 1} of ${draft.pagePaths.size} · Swipe to switch",
                    color = C.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.Surface)
                        .clickable {
                            vm.external()
                            addMorePhotos.launch("image/*")
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = C.Yellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add more pages", color = C.Yellow, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (draft.pagePaths.size > 1) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(C.Surface)
                            .clickable {
                                vm.removePageFromDraftDoc(safePageIndex)
                                if (selectedPageIndex >= draft.pagePaths.size - 1) {
                                    selectedPageIndex = (draft.pagePaths.size - 2).coerceAtLeast(0)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = C.Danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Remove page", color = C.Danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
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
                        "Manual entry: On-device AI model not downloaded. Type document fields manually.",
                        color = C.Cream,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        if (!isDirectEdit) {
            BlockField("Document title *", title, "Tap to enter title * (Required)") { isDirectEdit = true }
            Spacer(Modifier.height(12.dp))
            BlockField(
                label = "Document type",
                value = docType,
                placeholder = "Tap to select document type",
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.Muted) },
                onClick = { typeModal = true },
            )
            Spacer(Modifier.height(12.dp))
            BlockField("Issuing authority", issuer, "Tap to enter issuing authority") { isDirectEdit = true }
        } else {
            CField("Document title *", title, { title = it }, singleLine = false, maxLines = 4)
            Spacer(Modifier.height(12.dp))
            BlockField(
                label = "Document type",
                value = docType,
                placeholder = "Tap to select document type",
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.Muted) },
                onClick = { typeModal = true },
            )
            Spacer(Modifier.height(12.dp))
            CField("Issuing authority", issuer, { issuer = it }, singleLine = false, maxLines = 4)
        }

        // Suggested Attributes section
        val suggestedAttrs = listOf("Document No", "Date of Issue", "Expiry Date", "Holder Name", "Account No", "Reference No")
        val availableSuggestions = suggestedAttrs.filter { sug -> docFields.none { it.label.equals(sug, true) } }

        if (availableSuggestions.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Suggested attributes", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableSuggestions.forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.Surface)
                            .border(1.dp, C.Outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                docFields = (docFields + CardField(label, "")).toMutableList()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = C.Yellow, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, color = C.Cream, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Custom attributes list
        if (docFields.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Document attributes", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            docFields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CField(
                        label = field.label,
                        value = field.value,
                        onChange = { newVal ->
                            val updatedList = docFields.toMutableList()
                            updatedList[index] = field.copy(value = newVal)
                            docFields = updatedList
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val updatedList = docFields.toMutableList()
                            updatedList.removeAt(index)
                            docFields = updatedList
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove field", tint = C.Danger)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        PrimaryButton("Upload document", enabled = title.isNotBlank() && docType.isNotBlank(), onClick = {
            val updated = draft.copy(
                title = title.trim(),
                docType = docType,
                issuer = issuer.trim(),
                fields = docFields,
            )
            vm.saveDoc(updated)
            onUploaded(updated.id)
        })
        Spacer(Modifier.height(16.dp))
    }
    if (typeModal) {
        CardamomSelectModal(
            title = "Document type",
            options = docTypes,
            selected = docType,
            optionLabel = { it },
            onSelect = {
                docType = it
                typeModal = false
            },
            onDismiss = { typeModal = false },
        )
    }
    if (editModalField == "title") {
        FieldEditModal("Document title", title, singleLine = false, maxLines = 4, onConfirm = { title = it }, onDismiss = { editModalField = null })
    }
    if (editModalField == "issuer") {
        FieldEditModal("Issuing authority", issuer, singleLine = false, maxLines = 4, onConfirm = { issuer = it }, onDismiss = { editModalField = null })
    }
}

// ---------------- Document Detail & Full View Screen ----------------

@Composable
fun DocumentDetailScreen(
    vm: AppViewModel,
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val documents by vm.documents.collectAsState()
    val doc = documents.firstOrNull { it.id == id }
    val lastKnownDoc = remember { mutableStateOf(doc) }
    if (doc != null) lastKnownDoc.value = doc
    val displayDoc = doc ?: lastKnownDoc.value
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showFullViewer by remember { mutableStateOf(false) }

    if (displayDoc == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val images = remember(displayDoc) { getDocumentImages(ctx, displayDoc) }
    val pdfPages = remember(displayDoc, images) {
        if (images.isNotEmpty()) emptyList() else getDocumentPdfPages(displayDoc)
    }
    val allPages = if (images.isNotEmpty()) images else pdfPages

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        TopBar(
            title = displayDoc.title.ifBlank { displayDoc.docType },
            onBack = onBack,
            trailing = {
                StarButton(
                    pinned = (vm.pinnedDocId == displayDoc.id) || displayDoc.isPinned,
                    desc = if (displayDoc.isPinned) "Unpin document" else "Pin to top",
                    onClick = {
                        vm.togglePinDoc(displayDoc.id)
                    },
                )
            },
        )

        Spacer(Modifier.height(12.dp))

        // Details Card (TYPE and ISSUER) placed above the preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(C.Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TYPE",
                        color = C.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = displayDoc.docType,
                        color = C.Cream,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "ISSUER",
                        color = C.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = displayDoc.issuer.ifBlank { "-" },
                        color = C.Cream,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Primary Document Container with true aspect ratio (16:9, 9:16, etc.)
        var currentDetailPageIndex by remember { mutableStateOf(0) }
        val safeDetailIndex = currentDetailPageIndex.coerceIn(0, (allPages.size - 1).coerceAtLeast(0))
        val currentDetailBmp = allPages.getOrNull(safeDetailIndex)
        val docAspect = if (currentDetailBmp != null && currentDetailBmp.height > 0) {
            (currentDetailBmp.width.toFloat() / currentDetailBmp.height.toFloat()).coerceIn(0.5f, 2.0f)
        } else (3f / 4f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF141414))
                .clickable { showFullViewer = true }
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(docAspect)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentDetailBmp != null) {
                        Image(
                            bitmap = currentDetailBmp.asImageBitmap(),
                            contentDescription = "Page ${safeDetailIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(Modifier.width(80.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF8B7CF6)))
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.fillMaxWidth(0.9f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth(0.95f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD0C8B8)))
                        }
                    }
                }
                if (allPages.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { if (safeDetailIndex > 0) currentDetailPageIndex-- },
                            enabled = safeDetailIndex > 0,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous page",
                                tint = if (safeDetailIndex > 0) C.Cream else C.Muted.copy(alpha = 0.3f),
                            )
                        }
                        Text(
                            text = "Page ${safeDetailIndex + 1} of ${allPages.size}",
                            color = C.Cream,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        IconButton(
                            onClick = { if (safeDetailIndex < allPages.size - 1) currentDetailPageIndex++ },
                            enabled = safeDetailIndex < allPages.size - 1,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next page",
                                tint = if (safeDetailIndex < allPages.size - 1) C.Cream else C.Muted.copy(alpha = 0.3f),
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "1 page",
                        color = C.Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        // Action buttons: Share (cream pill), Edit (dark pill), Delete (dark pill with red text)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = "Share",
                onClick = {
                    val shareFile = when {
                        displayDoc.pdfPath != null -> File(displayDoc.pdfPath)
                        displayDoc.pagePaths.isNotEmpty() -> File(displayDoc.pagePaths.first())
                        else -> File(displayDoc.uriPath)
                    }
                    if (shareFile.exists()) {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shareFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = displayDoc.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(shareIntent, "Share document"))
                    } else {
                        Toast.makeText(ctx, "File not found on device", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1.2f),
            )
            SecondaryButton(
                text = "Edit",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Delete",
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f),
                color = C.Danger,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmDelete) {
        CardamomConfirmModal(
            title = "Delete this document?",
            message = "This document and its stored files will be permanently removed from this phone.",
            confirmText = "Delete document",
            confirmColor = C.Danger,
            onConfirm = {
                confirmDelete = false
                vm.deleteDoc(displayDoc.id)
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showFullViewer) {
        DocumentFullViewerDialog(
            doc = displayDoc,
            pages = allPages,
            onDismiss = { showFullViewer = false },
        )
    }
}

@Composable
fun DocumentFullViewerDialog(
    doc: WalletDocument,
    pages: List<Bitmap>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0A0A),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIcon(Icons.Default.Close, "Close", onDismiss)
                    Text(
                        text = doc.title.ifBlank { doc.docType },
                        color = Color.White,
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
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pages.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(pages.size) { i ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White),
                                    ) {
                                        Image(
                                            bitmap = pages[i].asImageBitmap(),
                                            contentDescription = "Page ${i + 1}",
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = ContentScale.FillWidth,
                                        )
                                    }
                                    if (pages.size > 1) {
                                        Text(
                                            text = "Page ${i + 1} of ${pages.size}",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DocumentIcon(modifier = Modifier.size(40.dp), tint = C.Yellow)
                            Spacer(Modifier.height(10.dp))
                            Text("No preview available", color = C.Muted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Document Edit Screen ----------------

@Composable
fun DocumentEditScreen(
    vm: AppViewModel,
    id: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val initial = remember { vm.doc(id) ?: WalletDocument() }
    var d by remember { mutableStateOf(initial) }
    var typeModal by remember { mutableStateOf(false) }
    var isDirectEdit by remember { mutableStateOf(false) }
    var editModalField by remember { mutableStateOf<String?>(null) }
    var editAttrKey by remember { mutableStateOf<String?>(null) }
    var editAttrVal by remember { mutableStateOf("") }

    val docTypes = listOf(
        "National ID",
        "Passport / Visa",
        "Educational Certificate",
        "Receipt / Invoice",
        "Exit Permit",
        "Health / Vaccine Record",
        "Insurance Policy",
        "Work Permit / Contract",
        "Document",
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        TopBar("Edit document", onBack, trailing = {
            CircleIcon(if (isDirectEdit) Icons.Default.Check else Icons.Default.Edit, "Toggle edit fields") {
                isDirectEdit = !isDirectEdit
            }
        })
        Spacer(Modifier.height(16.dp))

        if (!isDirectEdit) {
            BlockField("Document title", d.title, "Tap to enter title") { isDirectEdit = true }
            Spacer(Modifier.height(12.dp))
            BlockField(
                label = "Document type",
                value = d.docType,
                placeholder = "Tap to select document type",
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.Muted) },
                onClick = { typeModal = true },
            )
            Spacer(Modifier.height(12.dp))
            BlockField("Issuing authority", d.issuer, "Tap to enter issuing authority") { isDirectEdit = true }
        } else {
            CField("Document title", d.title, { d = d.copy(title = it) }, singleLine = false, maxLines = 4)
            Spacer(Modifier.height(12.dp))
            BlockField(
                label = "Document type",
                value = d.docType,
                placeholder = "Tap to select document type",
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.Muted) },
                onClick = { typeModal = true },
            )
            Spacer(Modifier.height(12.dp))
            CField("Issuing authority", d.issuer, { d = d.copy(issuer = it) }, singleLine = false, maxLines = 4)
        }
        Spacer(Modifier.height(20.dp))

        Text("Attributes", color = C.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))

        val attr1 = d.fields.getOrNull(0) ?: CardField("Valid till", "2034")
        val attr2 = d.fields.getOrNull(1) ?: CardField("Number", "N1234567")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Surface)
                    .clickable { editAttrKey = attr1.label; editAttrVal = attr1.value }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column {
                    Text(attr1.label, color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(attr1.value, color = C.Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Surface)
                    .clickable { editAttrKey = attr2.label; editAttrVal = attr2.value }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column {
                    Text(attr2.label, color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(attr2.value, color = C.Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        PrimaryButton("Save", enabled = d.title.isNotBlank() && d.docType.isNotBlank(), onClick = {
            vm.saveDoc(d)
            onSaved()
        })
        Spacer(Modifier.height(16.dp))
    }

    if (typeModal) {
        CardamomSelectModal(
            title = "Document type",
            options = docTypes,
            selected = d.docType,
            optionLabel = { it },
            onSelect = {
                d = d.copy(docType = it)
                typeModal = false
            },
            onDismiss = { typeModal = false },
        )
    }
    if (editModalField == "title") {
        FieldEditModal("Document title", d.title, singleLine = false, maxLines = 4, onConfirm = { d = d.copy(title = it) }, onDismiss = { editModalField = null })
    }
    if (editModalField == "issuer") {
        FieldEditModal("Issuing authority", d.issuer, singleLine = false, maxLines = 4, onConfirm = { d = d.copy(issuer = it) }, onDismiss = { editModalField = null })
    }
    if (editAttrKey != null) {
        FieldEditModal(editAttrKey!!, editAttrVal, onConfirm = { v ->
            val list = d.fields.toMutableList()
            val idx = list.indexOfFirst { it.label == editAttrKey }
            if (idx >= 0) list[idx] = list[idx].copy(value = v)
            else list.add(CardField(editAttrKey!!, v))
            d = d.copy(fields = list)
            editAttrKey = null
        }, onDismiss = { editAttrKey = null })
    }
}
