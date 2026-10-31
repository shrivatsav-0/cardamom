package com.shrivatsav.cardamom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Signature custom floating modal for Cardamom.
 * All 4 sides disconnected from screen edges, floating over a dimmed backdrop
 * with a dark neutral charcoal background, crisp border outline, zero yellow tint,
 * and raised/muted controls.
 */
@Composable
fun CardamomModal(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalCardamomPalette.current.isDark
    val modalBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val modalBorder = if (isDark) Color(0xFF38383C) else Color(0xFFE2DDD5)
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1A1917)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ProvideTextStyle(TextStyle(fontFamily = GoogleSansFlexFamily)) {
            Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .background(Color.Black.copy(alpha = 0.65f))
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Floating disconnected sheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.5f),
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.5.dp, modalBorder, RoundedCornerShape(28.dp)),
                color = modalBg,
                contentColor = textPrimary,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    // Small floating indicator pill
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFD6D1C7))
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))

                    // Title header
                    if (!title.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = subtitle,
                                        fontSize = 13.sp,
                                        color = if (isDark) Color(0xFF8E8E93) else Color(0xFF78746B),
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            CircleIcon(Icons.Default.Close, "Close", onDismiss)
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    // Main modal content
                    content()
                }
            }
            }
        }
    }
}

/**
 * Custom selection modal with floating pill-styled options and raised/muted buttons.
 * Used for Theme, Lock Timeout, etc.
 */
@Composable
fun <T> CardamomSelectModal(
    title: String,
    subtitle: String? = null,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    optionDescription: ((T) -> String?)? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    CardamomModal(onDismiss = onDismiss, title = title, subtitle = subtitle) {
        val isDark = LocalCardamomPalette.current.isDark

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { item ->
                val isSelected = item == selected
                val desc = optionDescription?.invoke(item)

                val itemBg = when {
                    isSelected && isDark -> Color(0xFF303034)
                    isSelected && !isDark -> Color(0xFFEDE9DF)
                    isDark -> Color(0xFF242427)
                    else -> Color(0xFFF7F5EE)
                }

                val itemBorder = when {
                    isSelected -> C.Yellow
                    isDark -> Color(0xFF38383C)
                    else -> Color(0xFFE2DDD5)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(itemBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = itemBorder,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable { onSelect(item) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = optionLabel(item),
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isSelected -> C.Yellow
                                isDark -> Color(0xFFF5F5F7)
                                else -> Color(0xFF1A1917)
                            },
                        )
                        if (!desc.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = desc,
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF78746B),
                                lineHeight = 17.sp,
                            )
                        }
                    }

                    if (isSelected) {
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(C.Yellow),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Custom confirmation modal for destructive or high-intent operations.
 */
@Composable
fun CardamomConfirmModal(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    confirmColor: Color = C.Yellow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CardamomModal(onDismiss = onDismiss, title = title) {
        val isDark = LocalCardamomPalette.current.isDark

        Text(
            text = message,
            color = if (isDark) Color(0xFF9E9EA4) else Color(0xFF6E6A63),
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Custom modal for displaying QR code links or raw code contents with copy button.
 */
@Composable
fun CardamomLinkModal(
    title: String,
    content: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val isDark = LocalCardamomPalette.current.isDark

    CardamomModal(onDismiss = onDismiss, title = title) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (isDark) Color(0xFF242427) else Color(0xFFF5F3EC))
                .border(1.dp, if (isDark) Color(0xFF38383C) else Color(0xFFE2DDD5), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Text(
                text = content,
                color = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1A1917),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Copy to clipboard",
            onClick = {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(title, content))
                Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ItemPreviewToolbarModal(
    onDismiss: () -> Unit,
    actions: Set<String>,
    onAction: (String) -> Unit,
    isPinned: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = LocalCardamomPalette.current.isDark
    val toolbarBg = if (isDark) Color(0xFF242426) else Color(0xFFFFFFFF)
    val toolbarBorder = if (isDark) Color(0xFF3E3E42) else Color(0xFFE4DFD5)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        SideEffect {
            if (Build.VERSION.SDK_INT >= 31) {
                val window = (view.parent as? DialogWindowProvider)?.window
                window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window?.attributes = window?.attributes?.apply {
                    blurBehindRadius = 32
                }
            }
        }
        ProvideTextStyle(TextStyle(fontFamily = GoogleSansFlexFamily)) {
            Box(Modifier.fillMaxSize()) {
                // Darkened backdrop scrim that dismisses on click
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )

                // Centered sharp preview and icon-only toolbar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        // Card or Document Preview at top (sharp, elevated)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    spotColor = Color.Black.copy(alpha = 0.6f),
                                ),
                        ) {
                            content()
                        }

                        Spacer(Modifier.height(18.dp))

                        // Floating Toolbar directly below the preview: icons only, no labels
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = toolbarBg,
                            shadowElevation = 16.dp,
                            border = BorderStroke(1.dp, toolbarBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val allActions = listOf(
                                    "delete" to ("Delete" to Icons.Default.Delete),
                                    "edit" to ("Edit" to Icons.Default.Edit),
                                    "share" to ("Share" to Icons.Default.Share),
                                    "pin" to ((if (isPinned) "Unpin" else "Pin") to Icons.Default.PushPin),
                                    "details" to ("Details" to Icons.Default.Visibility),
                                )

                                allActions.filter { actions.contains(it.first) }.forEach { (id, pair) ->
                                    val (label, icon) = pair
                                    val isDelete = id == "delete"
                                    val isPin = id == "pin"
                                    val tint = when {
                                        isDelete -> C.Danger
                                        isPin -> C.Yellow
                                        isDark -> Color(0xFFF5F5F7)
                                        else -> Color(0xFF161616)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                onAction(id)
                                                onDismiss()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = tint,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
