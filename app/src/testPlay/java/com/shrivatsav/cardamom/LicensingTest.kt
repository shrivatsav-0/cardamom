package com.shrivatsav.cardamom

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil

class LicensingTest {

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
    fun testTrialCalculationLogic() {
        val totalMs = LicenseConfig.TRIAL_DURATION_MS
        assertEquals(7L * 24 * 60 * 60 * 1000L, totalMs)

        val now = 1730000000000L
        val expiresAt = now + (3L * 24 * 60 * 60 * 1000L) // 3 days remaining

        val msLeft = (expiresAt - now).coerceAtLeast(0)
        val daysLeft = ceil(msLeft.toDouble() / (24 * 60 * 60 * 1000.0)).toInt().coerceIn(1, 7)
        assertEquals(3, daysLeft)

        // Expired check
        val expiredAt = now - 5000L
        val isExpired = now >= expiredAt
        assertTrue(isExpired)
    }

    @Test
    fun testLicenseConfigConstants() {
        assertEquals("cardamom_lifetime_pro", LicenseConfig.PRODUCT_ID)
        assertEquals("₹99", LicenseConfig.DEFAULT_PRICE)
        assertTrue(LicenseConfig.WORKER_ENDPOINT.contains("workers.dev"))
    }
}
