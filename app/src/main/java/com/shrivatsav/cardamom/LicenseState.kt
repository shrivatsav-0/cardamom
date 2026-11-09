package com.shrivatsav.cardamom

import android.app.Activity

sealed interface LicenseState {
    object NotStarted : LicenseState
    data class TrialActive(val daysLeft: Int, val totalDays: Int = 7) : LicenseState
    object TrialExpired : LicenseState
    object Premium : LicenseState

    val isUnlocked: Boolean
        get() = this is Premium || this is TrialActive

    val isExpired: Boolean
        get() = this is TrialExpired

    val isPremium: Boolean
        get() = this is Premium
}

interface DistributionController {
    val licenseState: LicenseState
    val isPaywallLocked: Boolean
    var showPurchaseSuccess: Boolean
    val showTrialEndingSheet: Boolean
    val premiumPrice: String

    fun onForeground() {}
    fun onBackground() {}
    fun startTrial() {}
    fun launchBilling(activity: Activity) {}
    fun restorePurchase(onResult: (Boolean) -> Unit) { onResult(false) }
    fun checkTrialEndingPrompt() {}
    fun dismissTrialEndingSheet() {}
    fun dismissPurchaseSuccess() {}
    fun destroy() {}
}
