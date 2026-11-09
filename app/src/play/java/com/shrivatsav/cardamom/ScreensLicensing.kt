package com.shrivatsav.cardamom

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Yellow pill badge used across paywall headers (e.g. "[7 days]", "[PRO]", "[alex]")
 */
@Composable
fun YellowPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 30,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(C.Yellow)
            .padding(horizontal = 14.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = C.StaticInk,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * 7-segment indicator bar showing elapsed vs remaining trial days.
 */
@Composable
fun TrialSegmentsBar(
    daysLeft: Int,
    totalDays: Int = 7,
    modifier: Modifier = Modifier,
) {
    val activeCount = daysLeft.coerceIn(0, totalDays)
    val elapsedCount = totalDays - activeCount

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalDays) {
            val isYellow = i >= elapsedCount
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isYellow) C.Yellow else C.Outline.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun TrialFeatureBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(C.Yellow)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = C.Cream,
            lineHeight = 20.sp,
        )
    }
}

/**
 * Screen N-2 (Already Unlocked): Displayed during Onboarding when the user has reinstalled
 * the app and already owns Cardamom Premium from a previous purchase.
 */
@Composable
fun AlreadyUnlockedScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TopBar("", onBack)

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Welcome back,",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = C.Cream,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            ) {
                Text(
                    text = "you're ",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Cream,
                )
                YellowPillBadge("PRO", fontSize = 28)
            }

            Text(
                text = "You have already unlocked Cardamom Premium previously on this Google account. Your lifetime access is restored and being continued.",
                fontSize = 15.sp,
                color = C.Muted,
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // Pro Status Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.Surface)
                    .padding(20.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "LIFETIME STATUS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.Yellow,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Premium Active",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.Cream,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(C.Yellow),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = C.StaticInk,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(C.Outline.copy(alpha = 0.4f))
                    )
                    Spacer(Modifier.height(16.dp))

                    TrialFeatureBullet("Unlimited cards, IDs, and loyalty passes")
                    Spacer(Modifier.height(10.dp))
                    TrialFeatureBullet("Private on-device OCR and dynamic extraction")
                    Spacer(Modifier.height(10.dp))
                    TrialFeatureBullet("Biometric security & zero tracking forever")
                }
            }
        }

        // Bottom Navigation & Actions (Above system bottom navigation bar)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dots(active = 4, total = 7)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Continue setup",
                onClick = onNext,
            )
        }
    }
}

/**
 * Screen N-2 (Trial / Premium Intro): Displayed during Onboarding when the user
 * has not yet purchased Cardamom Premium.
 */
@Composable
fun TrialIntroScreen(
    vm: AppViewModel,
    onBack: () -> Unit = {},
    onStartTrial: () -> Unit,
    onPremiumUnlocked: () -> Unit,
) {
    val ctx = LocalContext.current

    // Auto-advance to next screen if premium is unlocked while on this screen
    LaunchedEffect(vm.licenseState) {
        if (vm.licenseState is LicenseState.Premium) {
            onPremiumUnlocked()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            TopBar("", onBack)

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Try it free",
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = C.Cream,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "for ",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Cream,
                )
                YellowPillBadge("7 days", fontSize = 28)
            }

            Text(
                text = "Everything is unlocked from day one. No limits, nothing to pay right now.",
                fontSize = 14.sp,
                color = C.Muted,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Pricing & Feature Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.Surface)
                    .padding(18.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text(
                                text = "Premium after the trial",
                                fontSize = 12.sp,
                                color = C.Muted,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = vm.premiumPrice,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.Cream,
                            )
                        }
                        Text(
                            text = "one-time payment",
                            fontSize = 12.sp,
                            color = C.Muted,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(C.Outline.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.height(14.dp))

                    TrialFeatureBullet("Every feature, unlocked for 7 days")
                    Spacer(Modifier.height(10.dp))
                    TrialFeatureBullet("No payment needed to start")
                    Spacer(Modifier.height(10.dp))
                    TrialFeatureBullet("Your ${vm.premiumPrice} covers development costs & supports indie dev")
                }
            }
        }

        // Action Buttons & Navigation Dots (Cleanly above bottom navigation)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dots(active = 4, total = 7)
            Spacer(Modifier.height(14.dp))
            AppButton(
                text = "Start 7-day free trial",
                onClick = onStartTrial,
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Get Premium for ${vm.premiumPrice}",
                onClick = {
                    val act = ctx.findActivity()
                    if (act != null) vm.launchBilling(act)
                    if (vm.licenseState is LicenseState.Premium) {
                        onPremiumUnlocked()
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Restore previous purchase",
                color = C.Muted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable {
                        vm.restorePurchase { restored ->
                            if (restored) {
                                onPremiumUnlocked()
                            } else {
                                Toast.makeText(ctx, "No previous purchase found on Google Play", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 12.dp),
            )
        }
    }
}

/**
 * Screen 2: Trial Status Banner
 * Displayed at the top of Wallet & Settings screens.
 */
@Composable
fun TrialBanner(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val state = vm.licenseState
    if (state !is LicenseState.TrialActive) return

    val ctx = LocalContext.current
    val daysLeft = state.daysLeft
    val daysText = if (daysLeft == 1) "1 day left" else "$daysLeft days left"
    val onLaunch = {
        val act = ctx.findActivity()
        if (act != null) vm.launchBilling(act)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Surface)
            .clickable(onClick = onLaunch)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Free trial: ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = C.Cream,
                    )
                    Text(
                        text = daysText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = C.Yellow,
                    )
                }

                // "Buy ₹99" pill button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.Yellow)
                        .clickable(onClick = onLaunch)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Buy ${vm.premiumPrice}",
                        color = C.StaticInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            TrialSegmentsBar(daysLeft = daysLeft, totalDays = 7)
        }
    }
}

/**
 * Screen 3: "1 day left in your free trial" Bottom Sheet Modal
 */
@Composable
fun TrialEndingBottomSheet(
    vm: AppViewModel,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val state = vm.licenseState as? LicenseState.TrialActive ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(C.Bg)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(C.Outline)
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "1 day left",
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Cream,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = "in your free ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = C.Cream,
                    )
                    YellowPillBadge("trial", fontSize = 26)
                }

                Text(
                    text = "Your cards, barcodes, and dynamic field recognition will lock after tomorrow.",
                    fontSize = 14.sp,
                    color = C.Muted,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 18.dp),
                )

                // Price Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(C.Surface)
                        .padding(18.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Lifetime access",
                                    fontSize = 12.sp,
                                    color = C.Muted,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = vm.premiumPrice,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = C.Cream,
                                )
                            }
                            Text(
                                text = "one-time payment",
                                fontSize = 12.sp,
                                color = C.Muted,
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        TrialFeatureBullet("All current and future features")
                        Spacer(Modifier.height(8.dp))
                        TrialFeatureBullet("Unlimited cards and documents")
                        Spacer(Modifier.height(8.dp))
                        TrialFeatureBullet("Private on-device AI recognition")
                    }
                }

                Spacer(Modifier.height(20.dp))

                AppButton(
                    text = "Unlock forever for ${vm.premiumPrice}",
                    onClick = {
                        val act = ctx.findActivity()
                        if (act != null) vm.launchBilling(act)
                        onDismiss()
                    },
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Remind me later",
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * Screen 4: "Your 7-day trial has ended" Locked Paywall
 * Hard lock when trial has expired and user has not paid.
 * Backdoor-proof: Home screen widgets stay working, but the app itself is locked.
 */
@Composable
fun TrialExpiredLockScreen(
    vm: AppViewModel,
) {
    // Intercept back gesture completely: cannot escape
    BackHandler(enabled = true) {}

    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Lock icon indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(C.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = C.Yellow,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Your 7-day trial",
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = C.Cream,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            ) {
                Text(
                    text = "has ",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Cream,
                )
                YellowPillBadge("ended", fontSize = 28)
            }

            Text(
                text = "Unlock Cardamom permanently for ${vm.premiumPrice}. One payment, yours forever. No subscriptions.",
                fontSize = 15.sp,
                color = C.Muted,
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 22.dp),
            )

            // Features Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.Surface)
                    .padding(20.dp),
            ) {
                Column {
                    TrialFeatureBullet("Unlimited cards and barcodes")
                    Spacer(Modifier.height(14.dp))
                    TrialFeatureBullet("Full offline document vault")
                    Spacer(Modifier.height(14.dp))
                    TrialFeatureBullet("On-device multilingual AI recognition")
                    Spacer(Modifier.height(14.dp))
                    TrialFeatureBullet("Home screen widgets and quick tiles")
                    Spacer(Modifier.height(14.dp))
                    TrialFeatureBullet("All future updates included")
                }
            }
        }

        // Action Buttons
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            AppButton(
                text = "Unlock forever for ${vm.premiumPrice}",
                onClick = {
                    val act = ctx.findActivity()
                    if (act != null) vm.launchBilling(act)
                },
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Restore purchases",
                    color = C.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            vm.restorePurchase { restored ->
                                val msg = if (restored) "Purchases restored successfully!" else "No purchases found"
                                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}

/**
 * Screen 5: "Cardamom Pro unlocked" Full-Screen Modal
 */
@Composable
fun PurchaseSuccessScreen(
    vm: AppViewModel,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // 3 tilted cards illustration with checkmark
            CardsIllustration()

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Welcome to",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = C.Cream,
                textAlign = TextAlign.Center,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            ) {
                Text(
                    text = "Cardamom ",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Cream,
                )
                YellowPillBadge("PRO", fontSize = 26)
            }

            Text(
                text = "Your lifetime purchase is confirmed. All current and future features are permanently unlocked on this device.",
                fontSize = 15.sp,
                color = C.Muted,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp),
            )

            // Order Breakdown Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.Surface)
                    .padding(18.dp),
            ) {
                Column {
                    ReceiptRow("Status", "Active", valueColor = C.Mint, bold = true)
                    Spacer(Modifier.height(12.dp))
                    ReceiptRow("Plan", "Lifetime Access", valueColor = C.Cream)
                    Spacer(Modifier.height(12.dp))
                    ReceiptRow("Payment", "${vm.premiumPrice} · One-time", valueColor = C.Cream)
                    Spacer(Modifier.height(12.dp))
                    ReceiptRow("License", "Linked to Google Play", valueColor = C.Muted)
                }
            }
        }

        // Action Button
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            AppButton(
                text = "Continue to Wallet",
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, valueColor: Color = C.Cream, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = C.Muted,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
        )
    }
}

/**
 * Visual illustration of 3 tilted cards with a checkmark badge inside.
 */
@Composable
private fun CardsIllustration() {
    Box(
        modifier = Modifier
            .size(170.dp, 190.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Back card 1 (Amber, tilted -14 degrees)
        Box(
            modifier = Modifier
                .size(130.dp, 160.dp)
                .rotate(-14f)
                .clip(RoundedCornerShape(22.dp))
                .background(C.Amber)
        )
        // Back card 2 (Mint, tilted +10 degrees)
        Box(
            modifier = Modifier
                .size(130.dp, 160.dp)
                .rotate(10f)
                .clip(RoundedCornerShape(22.dp))
                .background(C.Mint)
        )
        // Front Card (Purple/Lavender, upright)
        Box(
            modifier = Modifier
                .size(135.dp, 165.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF8B7CF6)),
            contentAlignment = Alignment.Center,
        ) {
            // Dark circle badge with checkmark inside
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Verified",
                    tint = C.Yellow,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Displayed in Settings when Premium is active.
 */
@Composable
fun PremiumStatusCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(C.Yellow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Premium Unlocked",
                        tint = C.StaticInk,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Premium Unlocked",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = C.Cream,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Lifetime access • All features active",
                        fontSize = 12.sp,
                        color = C.Muted,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.Yellow.copy(alpha = 0.15f))
                    .border(1.dp, C.Yellow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "LIFETIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Yellow,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
