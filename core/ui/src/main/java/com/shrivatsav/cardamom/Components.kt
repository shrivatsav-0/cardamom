package com.shrivatsav.cardamom

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate

// ---------- buttons & inputs ----------

@Composable
fun AppButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    container: Color = if (LocalCardamomPalette.current.isDark) Color(0xFFFBF3E4) else Color(0xFF1A1917),
    content: Color = if (LocalCardamomPalette.current.isDark) Color(0xFF161616) else Color(0xFFFBF3E4),
    enabled: Boolean = true,
) = Button(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().height(56.dp),
    enabled = enabled,
    shape = RoundedCornerShape(28.dp),
    contentPadding = PaddingValues(horizontal = 12.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = container, contentColor = content,
        disabledContainerColor = C.Surface, disabledContentColor = C.Muted,
    ),
) { Text(text, color = if (enabled) content else C.Muted, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false) }

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(text, onClick, modifier, enabled = enabled)

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Cream) =
    AppButton(text, onClick, modifier, container = C.Surface, content = color)

@Composable
fun Choice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selColor: Color = if (LocalCardamomPalette.current.isDark) Color(0xFFFBF3E4) else Color(0xFF1A1917),
    onClick: () -> Unit,
) {
    val unselBg = Color.Transparent
    val selText = if (LocalCardamomPalette.current.isDark) Color(0xFF161616) else Color(0xFFFBF3E4)
    val unselText = C.Muted
    // Animate the pill background + label color when selection changes
    // instead of snapping, so tab switches feel continuous.
    val animatedBg by animateColorAsState(
        targetValue = if (selected) selColor else unselBg,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "choiceBg",
    )
    val animatedText by animateColorAsState(
        targetValue = if (selected) selText else unselText,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "choiceText",
    )
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(animatedBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = animatedText, fontSize = 16.sp, maxLines = 1, softWrap = false,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
fun CField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    singleLine: Boolean = false,
    maxLines: Int = 4,
    onImeAction: () -> Unit = {},
) =
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = singleLine, maxLines = maxLines, shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction() },
            onNext = { onImeAction() },
            onSearch = { onImeAction() },
            onGo = { onImeAction() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Yellow, unfocusedBorderColor = C.Outline,
            focusedLabelColor = C.Yellow, unfocusedLabelColor = C.Muted, cursorColor = C.Yellow,
            focusedTextColor = C.Cream, unfocusedTextColor = C.Cream,
        ),
    )

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked = checked, onCheckedChange = onChange,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Color(0xFF161616),
        checkedTrackColor = C.Yellow,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = if (LocalCardamomPalette.current.isDark) Color(0xFFFBF3E4) else Color(0xFFFFFFFF),
        uncheckedTrackColor = if (LocalCardamomPalette.current.isDark) Color(0xFF4A4A4A) else Color(0xFFDCD7CC),
        uncheckedBorderColor = Color.Transparent,
    ),
)

@Composable
fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Surface)
            .then(if (!LocalCardamomPalette.current.isDark) Modifier.border(1.dp, C.Outline, RoundedCornerShape(20.dp)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 13.sp, color = C.Muted)
        }
        AppSwitch(checked, onChange)
    }
}
@Composable
fun BlockField(
    label: String,
    value: String,
    placeholder: String = "Tap to enter",
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                text = value.ifBlank { placeholder },
                color = if (value.isBlank()) C.Muted.copy(alpha = 0.6f) else C.Cream,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun FieldEditModal(
    title: String,
    value: String,
    label: String = title,
    singleLine: Boolean = false,
    maxLines: Int = 6,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    CardamomModal(onDismiss = onDismiss, title = title) {
        CField(
            label = label,
            value = text,
            onChange = { text = it },
            singleLine = singleLine,
            maxLines = maxLines,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton("Save", onClick = { onConfirm(text.trim()); onDismiss() }, modifier = Modifier.weight(1f))
        }
    }
}


// ---------- layout bits ----------

@Composable
fun CircleIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(C.Surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = C.Cream)
    }
}
@Composable
fun StarIcon(pinned: Boolean, modifier: Modifier = Modifier, tint: Color = C.Yellow) {
    Canvas(modifier.size(24.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension * 0.46f
        val innerR = outerR * 0.44f
        val path = Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerR else innerR
                val angle = (-Math.PI / 2 + i * Math.PI / 5).toFloat()
                val x = cx + r * kotlin.math.cos(angle)
                val y = cy + r * kotlin.math.sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        if (pinned) {
            drawPath(path, color = tint)
        } else {
            drawPath(
                path,
                color = tint,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
fun StarButton(pinned: Boolean, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(C.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StarIcon(pinned = pinned)
    }
}


@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) CircleIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", onBack) else Spacer(Modifier.size(44.dp))
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { trailing() }
    }
}

@Composable
fun Dots(active: Int, total: Int = 3) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                Modifier.padding(horizontal = 3.dp).height(8.dp).width(if (i == active) 24.dp else 8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(if (i == active) C.Yellow else Color(0xFF4A4A4A))
            )
        }
    }
}

@Composable
fun GroupTitle(t: String) =
    Text(t, color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))

@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(C.Surface)
            .then(if (!LocalCardamomPalette.current.isDark) Modifier.border(1.dp, C.Outline, RoundedCornerShape(24.dp)) else Modifier),
        content = content,
    )

@Composable
fun Sep() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.Outline))

@Composable
fun Row2(label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 17.sp, maxLines = 1)
        trailing()
    }
}

@Composable
fun SettingLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null,
    trailingIcon: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = C.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!badge.isNullOrBlank()) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.Yellow.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(badge, color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (trailingIcon) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = C.Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun CardAttributeTwoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = C.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = C.Cream,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CardAttributeRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isTwoLine: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (isTwoLine || value.length > 22 || label.contains("Institution", ignoreCase = true) || label.contains("Company", ignoreCase = true)) {
        CardAttributeTwoLine(label = label, value = value, modifier = modifier, onClick = onClick)
    } else {
        Row2(label = label, onClick = onClick) {
            MutedText(value)
        }
    }
}

@Composable
fun MutedText(t: String) = Text(t, color = C.Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
fun BusyOverlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = C.Yellow)
            Spacer(Modifier.height(16.dp))
            Text(text, fontSize = 16.sp)
        }
    }
}

/**
 * App-themed Snackbar host matching Cardamom UI: Surface card (20dp radius,
 * outline border), Cream message text, Yellow action — instead of the
 * default grey Material pill. Slide+fade entrance comes from SnackbarHost's
 * built-in transitions.
 */
@Composable
fun CardamomSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            Row(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.Surface)
                    .border(1.dp, C.Outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.visuals.message,
                    color = C.Cream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                val actionLabel = data.visuals.actionLabel
                if (actionLabel != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.Yellow)
                            .clickable { data.performAction() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = actionLabel,
                            color = Color(0xFF161616),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    )
}

// ---------- brand & drawing ----------

@Composable
fun AppIcon(iconSize: Dp) {
    val ink = C.Ink
    val yellow = C.Yellow
    Canvas(Modifier.size(iconSize)) {
        val s = this.size.width / 120f
        drawRoundRect(ink, size = this.size, cornerRadius = CornerRadius(28f * s))
        val pivot = Offset(60f * s, 62f * s)
        fun card(deg: Float, c: Color) = drawRotate(deg, pivot) {
            drawRoundRect(c, Offset(35f * s, 26f * s), Size(50f * s, 68f * s), CornerRadius(10f * s))
        }
        card(-20f, C.Mint)
        card(-8f, C.Orange)
        drawRotate(8f, pivot) {
            drawRoundRect(yellow, Offset(35f * s, 26f * s), Size(50f * s, 68f * s), CornerRadius(10f * s))
            drawCircle(ink, 5f * s, Offset(47f * s, 40f * s))
            listOf(43f to 3f, 49f to 2f, 53f to 4f, 60f to 2f, 65f to 3f, 71f to 2f).forEach { (x, w) ->
                drawRect(ink, Offset(x * s, 68f * s), Size(w * s, 18f * s))
            }
        }
    }
}

@Composable
fun FingerprintIcon(iconSize: Dp = 56.dp, tint: Color = C.Yellow) {
    Canvas(Modifier.size(iconSize)) {
        val sw = this.size.minDimension * 0.06f
        val cx = this.size.width / 2; val cy = this.size.height / 2
        listOf(0.12f, 0.24f, 0.36f, 0.47f).forEachIndexed { i, f ->
            val r = this.size.minDimension * f
            drawArc(
                color = tint, startAngle = 195f + i * 12f, sweepAngle = 280f - i * 25f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r), style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun Illustration(seed: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = kotlin.random.Random(seed)
        val w = size.width; val h = size.height
        when (seed.mod(3)) {
            0 -> repeat(4) { i ->
                drawCircle(
                    color = (if (i % 2 == 0) Color.White else Color.Black).copy(alpha = 0.10f + r.nextFloat() * 0.10f),
                    radius = h * (0.3f + r.nextFloat() * 0.5f),
                    center = Offset(w * (0.6f + r.nextFloat() * 0.5f), h * r.nextFloat()),
                )
            }
            1 -> repeat(6) { i ->
                val x = w * (0.45f + i * 0.12f)
                drawLine(Color.White.copy(alpha = 0.18f), Offset(x, 0f), Offset(x - h * 0.5f, h), strokeWidth = 14f + r.nextFloat() * 20f)
            }
            else -> repeat(3) { i ->
                val p = Path().apply {
                    moveTo(0f, h * (0.5f + i * 0.15f))
                    cubicTo(w * 0.3f, h * (0.3f + i * 0.15f + r.nextFloat() * 0.2f), w * 0.6f, h * (0.9f + i * 0.1f), w, h * (0.5f + i * 0.15f))
                    lineTo(w, h); lineTo(0f, h); close()
                }
                drawPath(p, Color.Black.copy(alpha = 0.06f + i * 0.03f))
            }
        }
    }
}

@Composable
fun LogoView(card: WalletCard, size: Dp = 44.dp) {
    val path = when (card.logoMode) { LogoMode.IMAGE -> card.customPath; LogoMode.AUTO -> card.logoPath; else -> null }
    val bmp = remember(path) { path?.let { VaultCrypto.decodeBitmapPath(it)?.asImageBitmap() } }
    Box(
        Modifier.size(size).clip(CircleShape).background(if (bmp != null) Color.White else C.Ink),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit)
        else if (card.logoMode == LogoMode.EMOJI && card.emoji.isNotBlank()) Text(card.emoji, fontSize = (size.value * 0.5f).sp)
        else Text(card.initials, color = C.Cream, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun UserAvatar(
    name: String,
    avatarPath: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    editBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bmp = remember(avatarPath) {
        if (!avatarPath.isNullOrBlank()) {
            val f = java.io.File(avatarPath)
            if (f.exists()) VaultCrypto.decodeBitmapFile(f) else null
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(C.Yellow),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = name.trim().take(1).uppercase().ifEmpty { "?" },
                    color = C.Ink,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (editBadge) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.35f).dp.coerceAtLeast(22.dp))
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(C.Surface)
                    .border(2.dp, C.Bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Change photo",
                    tint = C.Yellow,
                    modifier = Modifier.size((size.value * 0.20f).dp.coerceAtLeast(14.dp)),
                )
            }
        }
    }
}

@Composable
fun CodePanel(
    card: WalletCard,
    modifier: Modifier = Modifier,
    barHeight: Dp = 110.dp,
    allowCopyModal: Boolean = false,
    showText: Boolean = true,
) {
    val ctx = LocalContext.current
    var showLinkDialog by remember { mutableStateOf(false) }
    val bmp = remember(card.code, card.format) {
        if (card.code.isBlank()) null
        else Codes.bitmap(card.code, card.format, if (card.format.is2D) 600 else 900, if (card.format.is2D) 600 else 300)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .then(
                if (allowCopyModal && card.code.isNotBlank()) {
                    Modifier.clickable { showLinkDialog = true }
                } else Modifier
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bmp != null) Image(
            bmp.asImageBitmap(), "Code", Modifier.fillMaxWidth().height(if (card.format.is2D) 170.dp else barHeight),
            contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
        ) else Text("No code yet", color = C.Ink)
        if (showText && card.code.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                card.code,
                color = C.Ink,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showLinkDialog) {
        CardamomLinkModal(
            title = "Code contents",
            content = card.code,
            onDismiss = { showLinkDialog = false },
        )
    }
}

@Composable
fun PointsRow(card: WalletCard) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("Available", color = C.Ink, fontSize = 14.sp)
            Text(card.points.ifBlank { "-" }, color = C.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Status", color = C.Ink, fontSize = 14.sp)
            Text(card.status.ifBlank { "-" }, color = C.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CardFieldsRow(card: WalletCard) {
    if (card.fields.isNotEmpty()) {
        val uniqueFields = card.fields.distinctBy { it.label.lowercase() }.distinctBy { it.value.lowercase() }
        val longField = uniqueFields.firstOrNull { it.value.length > 16 }
        val compactFields = uniqueFields.filter { it != longField }.take(if (longField != null) 2 else 3)

        Column(Modifier.fillMaxWidth()) {
            if (longField != null) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = longField.label.uppercase(),
                        color = C.Ink.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = longField.value,
                        color = C.Ink,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (compactFields.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (compactFields.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    compactFields.forEach { f ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = f.label.uppercase(),
                                color = C.Ink.copy(alpha = 0.70f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = f.value,
                                color = C.Ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    } else if (card.points.isNotBlank() || card.status.isNotBlank()) {
        PointsRow(card)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardTile(
    card: WalletCard,
    expanded: Boolean,
    art: Boolean,
    modifier: Modifier = Modifier,
    showCodeText: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val bg = C.CardColors[card.colorIndex.mod(C.CardColors.size)]
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else Modifier
            )
    ) {
        if (art) Illustration(card.seed, Modifier.matchParentSize())
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val primaryTitle = card.name.ifBlank { card.category.ifBlank { card.issuer.ifBlank { "Card name" } } }
                    Text(
                        text = primaryTitle,
                        color = C.Ink,
                        fontSize = if (primaryTitle.contains('\n') || primaryTitle.length > 22) 17.sp else 20.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val secondaryText = if (card.issuer.isNotBlank() && !card.issuer.equals(primaryTitle, ignoreCase = true)) {
                        card.issuer
                    } else if (card.name.isNotBlank() && !card.category.equals(primaryTitle, ignoreCase = true)) {
                        card.category
                    } else ""
                    if (secondaryText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = secondaryText,
                                color = C.Ink.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!expanded && card.code.isNotBlank()) {
                    val miniBmp = remember(card.code, card.format) {
                        if (card.format.is2D) Codes.bitmap(card.code, card.format, 160, 160)
                        else Codes.bitmap(card.code, card.format, 220, 100)
                    }
                    if (miniBmp != null) {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(if (card.format.is2D) 44.dp else 56.dp, 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                miniBmp.asImageBitmap(),
                                "Mini code",
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.None,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(expanded) {
                Column {
                    if (card.code.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        CodePanel(card, allowCopyModal = false, showText = showCodeText)
                    }
                    if (card.fields.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        CardFieldsRow(card)
                    } else if (card.points.isNotBlank() || card.status.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        PointsRow(card)
                    }
                }
            }
        }
    }
}

@Composable
fun Corners(modifier: Modifier = Modifier, color: Color = C.Yellow) {
    Canvas(modifier) {
        val side = minOf(size.width, size.height) * 0.7f
        val l = (size.width - side) / 2; val t = (size.height - side) / 2
        val len = side * 0.2f
        val sw = 12f
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
        }
        corner(l, t, 1f, 1f); corner(l + side, t, -1f, 1f)
        corner(l, t + side, 1f, -1f); corner(l + side, t + side, -1f, -1f)
    }
}
