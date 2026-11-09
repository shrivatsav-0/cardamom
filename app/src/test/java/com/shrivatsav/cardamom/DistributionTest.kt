package com.shrivatsav.cardamom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionTest {

    @Test
    fun testLicenseStateHierarchy() {
        val notStarted: LicenseState = LicenseState.NotStarted
        assertFalse(notStarted.isUnlocked)
        assertFalse(notStarted.isExpired)
        assertFalse(notStarted.isPremium)

        val active: LicenseState = LicenseState.TrialActive(daysLeft = 5, totalDays = 7)
        assertTrue(active.isUnlocked)
        assertFalse(active.isExpired)
        assertFalse(active.isPremium)
        assertEquals(5, (active as LicenseState.TrialActive).daysLeft)
        assertEquals(7, active.totalDays)

        val expired: LicenseState = LicenseState.TrialExpired
        assertFalse(expired.isUnlocked)
        assertTrue(expired.isExpired)
        assertFalse(expired.isPremium)

        val premium: LicenseState = LicenseState.Premium
        assertTrue(premium.isUnlocked)
        assertFalse(premium.isExpired)
        assertTrue(premium.isPremium)
    }

    @Test
    fun testDistributionBridgeFlags() {
        // In Play, hasOnboardingPaywall is true and total steps is 7
        // In GitHub, hasOnboardingPaywall is false and total steps is 6
        if (DistributionBridge.isPlayDistribution) {
            assertTrue(DistributionBridge.hasOnboardingPaywall)
            assertEquals(7, DistributionBridge.onboardingTotalSteps)
        } else {
            assertFalse(DistributionBridge.hasOnboardingPaywall)
            assertEquals(6, DistributionBridge.onboardingTotalSteps)
        }
    }
}
