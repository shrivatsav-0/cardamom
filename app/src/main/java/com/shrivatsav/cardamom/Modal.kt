package com.shrivatsav.cardamom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
