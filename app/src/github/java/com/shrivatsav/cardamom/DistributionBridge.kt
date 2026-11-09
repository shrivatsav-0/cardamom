package com.shrivatsav.cardamom

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope

class GithubDistributionController : DistributionController {
    override val licenseState: LicenseState = LicenseState.Premium
    override val isPaywallLocked: Boolean = false
    override var showPurchaseSuccess: Boolean = false
    override val showTrialEndingSheet: Boolean = false
    override val premiumPrice: String = ""

    override fun onForeground() {}
    override fun onBackground() {}
    override fun startTrial() {}
    override fun launchBilling(activity: Activity) {}
    override fun restorePurchase(onResult: (Boolean) -> Unit) { onResult(false) }
    override fun checkTrialEndingPrompt() {}
    override fun dismissTrialEndingSheet() {}
    override fun dismissPurchaseSuccess() {}
    override fun destroy() {}
}

object DistributionBridge {
    const val isPlayDistribution: Boolean = false
    const val hasOnboardingPaywall: Boolean = false
    const val onboardingTotalSteps: Int = 6

    fun createController(app: Application, prefs: Prefs, scope: CoroutineScope): DistributionController {
        return GithubDistributionController()
    }

    @Composable
    fun WalletTrialBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
        // No paid or trial banners in the GitHub build
    }

    @Composable
    fun SettingsLicensingHeader(vm: AppViewModel) {
        // No paid or subscription headers in the GitHub build
    }

    @Composable
    fun SettingsLicensingSection(vm: AppViewModel) {
        // No paid, subscription, or one-time purchase plans rows in the GitHub build
    }

    @Composable
    fun OnboardingPaywallScreen(
        vm: AppViewModel,
        onBack: () -> Unit,
        onNext: () -> Unit,
    ) {
        // Paywall is excluded from GitHub onboarding
    }

    @Composable
    fun TrialIntroRouteScreen(
        vm: AppViewModel,
        onBack: () -> Unit,
        onContinue: () -> Unit,
    ) {
        // No trial intro screen in GitHub build
    }

    @Composable
    fun PaywallLockScreen(vm: AppViewModel) {
        // Paywall lock screen is never displayed in GitHub build
    }

    @Composable
    fun TrialEndingSheet(vm: AppViewModel) {
        // No trial ending sheet in GitHub build
    }

    @Composable
    fun PurchaseSuccessModal(vm: AppViewModel, onDone: () -> Unit) {
        // No in-app purchase modals in GitHub build
    }

    fun membershipLabel(vm: AppViewModel): String {
        return "100% Free & Open Source ✓"
    }
}
