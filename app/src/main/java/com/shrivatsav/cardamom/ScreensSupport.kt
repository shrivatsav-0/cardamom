package com.shrivatsav.cardamom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Developer contacts, community networking links, and donation details.
 */
object SupportLinks {
    const val EMAIL = "shrivatsav0412@gmail.com"
    const val LINKEDIN = "https://linkedin.com/in/sharanshrivatsav"
    const val LINKEDIN_DISPLAY = "linkedin.com/in/sharanshrivatsav"
    const val GITHUB = "https://github.com/shrivatsav-0"
    const val GITHUB_DISPLAY = "github.com/shrivatsav-0"
    const val WEBSITE = "https://millosaurs.me"
    const val WEBSITE_DISPLAY = "millosaurs.me"
    const val KOFI = "https://ko-fi.com/sharumilli"
    const val KOFI_DISPLAY = "ko-fi.com/sharumilli"
    const val UPI_ID = "shrivatsav@slc"
    const val UPI_NAME = "Sharan Shrivatsav"

    fun upiUri(id: String = UPI_ID): String {
        return "upi://pay?pa=$id&mode=02"
    }

    fun openUrl(ctx: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmail(ctx: Context, email: String = EMAIL) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not open email app", Toast.LENGTH_SHORT).show()
        }
    }

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Top modal for donations supporting Ko-fi, UPI ID copy, and UPI QR Code.
 */
@Composable
fun DonationModal(
    onDismiss: () -> Unit,
    vm: AppViewModel? = null,
) {
    val ctx = LocalContext.current
    var showQrCode by remember { mutableStateOf(true) }

    CardamomModal(
        onDismiss = onDismiss,
        title = "Support Cardamom",
        subtitle = "100% free, offline & open source. Donations help support ongoing development!",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Ko-fi Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.Surface)
                    .border(1.dp, C.Outline, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(C.Orange.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = C.Orange,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Ko-fi", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    "International cards & PayPal",
                                    color = C.Muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        SupportLinks.KOFI_DISPLAY,
                        color = C.Muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Open Ko-fi Page",
                        onClick = {
                            vm?.external()
                            SupportLinks.openUrl(ctx, SupportLinks.KOFI)
                        },
                        modifier = Modifier.height(48.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // UPI Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.Surface)
                    .border(1.dp, C.Outline, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(C.Mint.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = C.Mint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("UPI (India)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                "GPay, PhonePe, Paytm, BHIM & more",
                                color = C.Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // UPI ID row with copy button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.Bg)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UPI ID", color = C.Muted, fontSize = 11.sp)
                            Text(
                                SupportLinks.UPI_ID,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = C.Cream,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(C.Surface)
                                .clickable {
                                    SupportLinks.copy(ctx, "UPI ID", SupportLinks.UPI_ID)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = C.Yellow,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Toggle or Show QR
                    if (showQrCode) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.upi_qr_shrivatsav),
                                    contentDescription = "UPI QR Code - Sharan Shrivatsav",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Scan with any UPI app to pay",
                                color = C.Muted,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    SecondaryButton(
                        text = if (showQrCode) "Hide QR Code" else "Show QR Code",
                        onClick = { showQrCode = !showQrCode },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SecondaryButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Polite non-intrusive modal that pops up every 2-3 months to remind users
 * about supporting Cardamom or connecting with the student developer.
 */
@Composable
fun PeriodicSupportModal(
    onDismiss: () -> Unit,
    onSupport: () -> Unit,
    onConnect: () -> Unit,
) {
    CardamomModal(
        onDismiss = onDismiss,
        title = "Enjoying Cardamom?",
        subtitle = "A note from the developer",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Surface)
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "Cardamom has no ads, no trackers, and no subscription fees. It's completely private, offline, and built by an independent college student.",
                        color = C.Cream,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If Cardamom has been helpful to you over the past few months, consider supporting its future development or saying hello!",
                        color = C.Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = "Support & Donate",
                onClick = onSupport,
                modifier = Modifier.height(50.dp),
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Connect with Me",
                onClick = onConnect,
                modifier = Modifier.height(50.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Remind me later",
                color = C.Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * Onboarding N-1 page: Meet the developer, networking links, and donation box.
 */
@Composable
fun OnboardingDeveloperSupportScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val ctx = LocalContext.current
    var showQrModal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        TopBar("", onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "A note from\nthe developer",
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Message box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Surface)
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "Hi there! I am a college student and need networking. If you'd like to reach out, feel free to email me, connect on LinkedIn, or check out my website!",
                        color = C.Cream,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Socials & Networking Section
            Text(
                "CONNECT & NETWORKING",
                color = C.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(10.dp))

            Group {
                SettingLinkRow(
                    title = "Email",
                    subtitle = SupportLinks.EMAIL,
                    onClick = {
                        vm.external()
                        SupportLinks.sendEmail(ctx)
                    },
                )
                Sep()
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
                    title = "Personal Website",
                    subtitle = SupportLinks.WEBSITE_DISPLAY,
                    onClick = {
                        vm.external()
                        SupportLinks.openUrl(ctx, SupportLinks.WEBSITE)
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
            }

            Spacer(Modifier.height(22.dp))

            // Donations Section
            Text(
                "SUPPORT & DONATIONS",
                color = C.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.Surface)
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "Donation Box",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Text(
                        "Cardamom has no ads or subscriptions. If you enjoy the app, tips are greatly appreciated!",
                        color = C.Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    )

                    // Ko-fi Button
                    SecondaryButton(
                        text = "Support via Ko-fi",
                        onClick = {
                            vm.external()
                            SupportLinks.openUrl(ctx, SupportLinks.KOFI)
                        },
                        modifier = Modifier.height(46.dp),
                    )

                    Spacer(Modifier.height(12.dp))

                    // UPI Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.Bg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UPI ID", color = C.Muted, fontSize = 11.sp)
                            Text(
                                SupportLinks.UPI_ID,
                                color = C.Cream,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(C.Surface)
                                    .clickable {
                                        SupportLinks.copy(ctx, "UPI ID", SupportLinks.UPI_ID)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                Text("Copy", color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(C.Surface)
                                    .clickable { showQrModal = true }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                Text("QR", color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Dots(4, total = 6)
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Continue", onClick = onNext)
    }

    if (showQrModal) {
        DonationModal(
            onDismiss = { showQrModal = false },
            vm = vm,
        )
    }
}
