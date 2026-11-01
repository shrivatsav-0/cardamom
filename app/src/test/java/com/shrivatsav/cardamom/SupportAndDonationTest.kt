package com.shrivatsav.cardamom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportAndDonationTest {

    @Test
    fun testSupportLinksConstants() {
        assertEquals("shrivatsav0412@gmail.com", SupportLinks.EMAIL)
        assertEquals("https://linkedin.com/in/sharanshrivatsav", SupportLinks.LINKEDIN)
        assertEquals("linkedin.com/in/sharanshrivatsav", SupportLinks.LINKEDIN_DISPLAY)
        assertEquals("https://millosaurs.me", SupportLinks.WEBSITE)
        assertEquals("millosaurs.me", SupportLinks.WEBSITE_DISPLAY)
        assertEquals("https://github.com/shrivatsav-0", SupportLinks.GITHUB)
        assertEquals("github.com/shrivatsav-0", SupportLinks.GITHUB_DISPLAY)
        assertEquals("https://ko-fi.com/sharumilli", SupportLinks.KOFI)
        assertEquals("ko-fi.com/sharumilli", SupportLinks.KOFI_DISPLAY)
        assertEquals("shrivatsav@slc", SupportLinks.UPI_ID)
    }
    @Test
    fun testUpiUriGeneration() {
        val uri = SupportLinks.upiUri("shrivatsav@slc")
        assertEquals("upi://pay?pa=shrivatsav@slc&mode=02", uri)
    }

    @Test
    fun testDecodeQrImage() {
        val file = java.io.File("src/main/res/drawable/upi_qr_shrivatsav.jpg")
        if (file.exists()) {
            val img = javax.imageio.ImageIO.read(file)
            val intArray = IntArray(img.width * img.height)
            img.getRGB(0, 0, img.width, img.height, intArray, 0, img.width)
            val source = com.google.zxing.RGBLuminanceSource(img.width, img.height, intArray)
            val bitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            try {
                val result = com.google.zxing.MultiFormatReader().decode(bitmap)
                println("DECODED_QR_DATA: " + result.text)
            } catch (e: Exception) {
                println("ZXING decode failed: " + e.message)
            }
        }
    }
    @Test
    fun testSupportIntervalDuration() {
        val dayMs = 24L * 60 * 60 * 1000L
        val minIntervalMs = 60L * dayMs // 2 months
        val maxIntervalMs = 90L * dayMs // 3 months

        val interval = AppViewModel.SUPPORT_INTERVAL_MS
        assertTrue("Support interval should be at least 60 days (2 months)", interval >= minIntervalMs)
        assertTrue("Support interval should be at most 90 days (3 months)", interval <= maxIntervalMs)
        assertEquals("Support interval is set to 75 days (~2.5 months)", 75L * dayMs, interval)
    }

    @Test
    fun testSupportTimingEvaluation() {
        val interval = AppViewModel.SUPPORT_INTERVAL_MS
        val now = 1770000000000L

        // Scenario 1: Initial install/baseline (0L) -> Should not prompt immediately
        val isFirstRun = 0L
        fun shouldPrompt(last: Long, current: Long): Boolean {
            if (last == 0L) return false
            return (current - last) >= interval
        }

        assertFalse(shouldPrompt(isFirstRun, now))

        // Scenario 2: 30 days passed -> Should not prompt yet
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)
        assertFalse(shouldPrompt(thirtyDaysAgo, now))

        // Scenario 3: 74 days passed -> Should not prompt yet
        val seventyFourDaysAgo = now - (74L * 24 * 60 * 60 * 1000L)
        assertFalse(shouldPrompt(seventyFourDaysAgo, now))

        // Scenario 4: 75 days passed (2.5 months) -> Should prompt
        val seventyFiveDaysAgo = now - (75L * 24 * 60 * 60 * 1000L)
        assertTrue(shouldPrompt(seventyFiveDaysAgo, now))

        // Scenario 5: 90 days passed (3 months) -> Should prompt
        val ninetyDaysAgo = now - (90L * 24 * 60 * 60 * 1000L)
        assertTrue(shouldPrompt(ninetyDaysAgo, now))
    }
}
