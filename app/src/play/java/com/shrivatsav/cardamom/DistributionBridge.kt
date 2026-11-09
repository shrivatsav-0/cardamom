package com.shrivatsav.cardamom

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope

class PlayDistributionController(
    private val app: Application,
    private val prefs: Prefs,
    private val scope: CoroutineScope
) : DistributionController {
    private val trialManager = TrialManager(app, prefs)
    private val billingManager = BillingManager(app, prefs) { isNewPurchase ->
        licenseState = LicenseState.Premium
        if (isNewPurchase && prefs.userName.isNotBlank()) {
            showPurchaseSuccess = true
        }
        showTrialEndingSheet = false
    }

    override var licenseState by mutableStateOf<LicenseState>(trialManager.getLicenseState())
    override val isPaywallLocked: Boolean get() = licenseState is LicenseState.TrialExpired
    override var showPurchaseSuccess by mutableStateOf(false)
    override var showTrialEndingSheet by mutableStateOf(false)
    override val premiumPrice: String get() = billingManager.formattedPrice

    init {
        billingManager.initialize(scope)
        trialManager.syncWithServer(scope) { state ->
            licenseState = state
            checkTrialEndingPrompt()
        }
    }

    override fun onForeground() {
        licenseState = trialManager.getLicenseState()
        trialManager.syncWithServer(scope) { state ->
            licenseState = state
            checkTrialEndingPrompt()
        }
        billingManager.queryExistingPurchases()
        checkTrialEndingPrompt()
    }

    override fun startTrial() {
        trialManager.startTrial(scope) { state ->
            licenseState = state
            checkTrialEndingPrompt()
        }
    }

    override fun launchBilling(activity: Activity) {
        billingManager.launchBillingFlow(activity)
    }

    override fun restorePurchase(onResult: (Boolean) -> Unit) {
        billingManager.restorePurchases(scope) { restored ->
            if (restored) {
                licenseState = LicenseState.Premium
                showPurchaseSuccess = true
                showTrialEndingSheet = false
            }
            onResult(restored)
        }
    }

    override fun checkTrialEndingPrompt() {
        val state = licenseState
        if (state is LicenseState.TrialActive && state.daysLeft <= 1 && !prefs.trialEndingDismissed) {
            showTrialEndingSheet = true
        }
    }

    override fun dismissTrialEndingSheet() {
        showTrialEndingSheet = false
        prefs.trialEndingDismissed = true
    }

    override fun dismissPurchaseSuccess() {
        showPurchaseSuccess = false
    }

    override fun destroy() {
        billingManager.destroy()
    }
}

object DistributionBridge {
    const val isPlayDistribution: Boolean = true
    const val hasOnboardingPaywall: Boolean = true
    const val onboardingTotalSteps: Int = 7

    fun createController(app: Application, prefs: Prefs, scope: CoroutineScope): DistributionController {
        return PlayDistributionController(app, prefs, scope)
    }

    @Composable
    fun WalletTrialBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
        TrialBanner(vm = vm, modifier = modifier)
    }

    @Composable
    fun SettingsLicensingHeader(vm: AppViewModel) {
        if (vm.licenseState is LicenseState.Premium) {
            PremiumStatusCard(
                modifier = Modifier.padding(top = 14.dp),
                onClick = { vm.showPurchaseSuccess = true }
            )
        } else {
            TrialBanner(vm = vm, modifier = Modifier.padding(top = 14.dp))
        }
    }

    @Composable
    fun SettingsLicensingSection(vm: AppViewModel) {
        val ctx = LocalContext.current
        GroupTitle("Cardamom Pro")
        Group {
            Row2("License Status") {
                when (val state = vm.licenseState) {
                    is LicenseState.Premium -> {
                        Text(
                            text = "Lifetime Unlocked ✓",
                            color = C.Mint,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    is LicenseState.TrialActive -> {
                        Text(
                            text = "7-Day Trial (${state.daysLeft}d left)",
                            color = C.Yellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    is LicenseState.TrialExpired -> {
                        Text(
                            text = "Trial Expired",
                            color = C.Danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    else -> {
                        MutedText("Free Trial Available")
                    }
                }
            }
            Sep()
            if (vm.licenseState is LicenseState.Premium) {
                Row2("Lifetime Benefits", onClick = { vm.showPurchaseSuccess = true }) {
                    MutedText("View details")
                }
            } else {
                Row2("Upgrade to Lifetime Pro", onClick = {
                    val act = ctx.findActivity()
                    if (act != null) vm.launchBilling(act)
                }) {
                    Text(vm.premiumPrice, color = C.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Sep()
            Row2("Restore Purchases", onClick = {
                vm.restorePurchase { restored ->
                    val msg = if (restored) "Purchases restored successfully!" else "No previous purchase found on Google Play"
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                MutedText("Check Play Store")
            }
        }
    }

    @Composable
    fun OnboardingPaywallScreen(
        vm: AppViewModel,
        onBack: () -> Unit,
        onNext: () -> Unit,
    ) {
        if (vm.licenseState is LicenseState.Premium) {
            AlreadyUnlockedScreen(
                vm = vm,
                onBack = onBack,
                onNext = onNext,
            )
        } else {
            TrialIntroScreen(
                vm = vm,
                onBack = onBack,
                onStartTrial = {
                    vm.startTrial()
                    onNext()
                },
                onPremiumUnlocked = onNext,
            )
        }
    }

    @Composable
    fun TrialIntroRouteScreen(
        vm: AppViewModel,
        onBack: () -> Unit,
        onContinue: () -> Unit,
    ) {
        TrialIntroScreen(
            vm = vm,
            onBack = onBack,
            onStartTrial = {
                vm.startTrial()
                onContinue()
            },
            onPremiumUnlocked = onContinue,
        )
    }

    @Composable
    fun PaywallLockScreen(vm: AppViewModel) {
        TrialExpiredLockScreen(vm)
    }

    @Composable
    fun TrialEndingSheet(vm: AppViewModel) {
        TrialEndingBottomSheet(
            vm = vm,
            onDismiss = { vm.dismissTrialEndingSheet() }
        )
    }

    @Composable
    fun PurchaseSuccessModal(vm: AppViewModel, onDone: () -> Unit) {
        PurchaseSuccessScreen(vm = vm, onDone = onDone)
    }

    fun membershipLabel(vm: AppViewModel): String {
        return if (vm.licenseState is LicenseState.Premium) {
            "Lifetime Pro Unlocked ✓"
        } else {
            "7-Day Free Trial"
        }
    }
}
