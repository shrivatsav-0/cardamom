package com.shrivatsav.cardamom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelAndVisionTest {

    @Test
    fun testAiModelCatalogMetadata() {
        val catalog = AiModelManager.catalog
        assertEquals("vision_ocr_v2", catalog.id)
        assertEquals("5.0.0", catalog.version)
        assertEquals(16_850_000L, catalog.sizeBytes)
        assertTrue(catalog.license.contains("Open Source") || catalog.license.contains("Apache"))
        assertTrue(catalog.architecture.contains("PP-OCRv5") || catalog.architecture.contains("Mobile Vision"))
    }

    @Test
    fun testAiVisionEngineUniversityIdExtraction() {
        val frontLines = listOf(
            "STANFORD UNIVERSITY",
            "STUDENT IDENTITY CARD",
            "Dept: Computer Science",
            "Valid Thru: 05/2026",
            "Blood Group: O+",
            "Cardholder: Jane Doe",
        )
        val backLines = listOf(
            "This card is non-transferable",
            "Emergency: 555-0192",
        )
        val frontText = frontLines.joinToString("\n")
        val backText = backLines.joinToString("\n")

        val result = AiVisionEngine.extractStructuredFields(
            frontText = frontText,
            backText = backText,
            frontLines = frontLines,
            backLines = backLines,
        )

        assertEquals("Institutional ID", result.category)
        assertTrue(result.issuer.contains("STANFORD UNIVERSITY", ignoreCase = true))

        val fieldsMap = result.dynamicFields.associate { it.label to it.value }
        assertEquals("Computer Science", fieldsMap["Department"])
        assertEquals("05/2026", fieldsMap["Validity"])
        assertEquals("O+", fieldsMap["Blood Group"])
        assertEquals("Non-Transferable", fieldsMap["Transferability"])
        assertEquals("Jane Doe", fieldsMap["Cardholder"])
    }

    @Test
    fun testAiVisionEngineAirlineBoardingPassExtraction() {
        val lines = listOf(
            "UNITED AIRLINES",
            "BOARDING PASS",
            "Flight: UA 482",
            "Gate: B22",
            "Seat: 14A",
            "SFO TO JFK",
        )
        val text = lines.joinToString("\n")

        val result = AiVisionEngine.extractStructuredFields(
            frontText = text,
            backText = "",
            frontLines = lines,
            backLines = emptyList(),
        )

        assertEquals("Flight Ticket", result.category)
        assertEquals("UNITED AIRLINES", result.issuer)

        val fieldsMap = result.dynamicFields.associate { it.label to it.value }
        assertEquals("UA 482", fieldsMap["Flight No"] ?: fieldsMap["Flight"])
        assertEquals("B22", fieldsMap["Gate"])
        assertEquals("14A", fieldsMap["Seat"])
        assertEquals("SFO to JFK", fieldsMap["Route"])
    }

    @Test
    fun testAiVisionEngineLoyaltyCardExtraction() {
        val lines = listOf(
            "BLOOM & EMBER REWARDS",
            "Member ID: BE-883192",
            "Tier: Platinum",
            "Available Points: 12,450",
            "Valid Thru: 12/2027",
        )
        val text = lines.joinToString("\n")

        val result = AiVisionEngine.extractStructuredFields(
            frontText = text,
            backText = "",
            frontLines = lines,
            backLines = emptyList(),
        )

        assertEquals("Loyalty Card", result.category)
        assertEquals("Platinum", result.status)
        assertEquals("12,450", result.points)

        val fieldsMap = result.dynamicFields.associate { it.label to it.value }
        assertEquals("BE-883192", fieldsMap["Member ID"])
        assertEquals("Platinum", fieldsMap["Tier"])
        assertEquals("12,450", fieldsMap["Points"])
        assertEquals("12/2027", fieldsMap["Validity"])
    }

    @Test
    fun testAiVisionEngineDynamicArbitraryFields() {
        val lines = listOf(
            "ACME HEALTH CLINIC",
            "Patient Name: John Smith",
            "Policy No: POL-99214",
            "Group ID: G-4001",
            "Co-Pay: $20",
        )
        val text = lines.joinToString("\n")

        val result = AiVisionEngine.extractStructuredFields(
            frontText = text,
            backText = "",
            frontLines = lines,
            backLines = emptyList(),
        )

        val fieldsMap = result.dynamicFields.associate { it.label to it.value }
        assertEquals("John Smith", fieldsMap["Patient Name"])
        assertEquals("POL-99214", fieldsMap["Policy No"])
        assertEquals("G-4001", fieldsMap["Group ID"])
        assertEquals("$20", fieldsMap["Co-Pay"])
    }

    @Test
    fun testCardWithDynamicFieldsJsonSerialization() {
        val card = WalletCard(
            name = "Student Pass",
            issuer = "Stanford University",
            category = "Institutional ID",
            fields = listOf(
                CardField("Department", "Engineering"),
                CardField("Validity", "2024-2028"),
                CardField("Blood Group", "A+"),
                CardField("Student ID", "ST-9021"),
            ),
            status = "Undergraduate",
        )

        val json = card.toJson()
        val restored = json.toCard()

        assertEquals(4, restored.fields.size)
        assertEquals("Department", restored.fields[0].label)
        assertEquals("Engineering", restored.fields[0].value)
        assertEquals("Student ID", restored.fields[3].label)
        assertEquals("ST-9021", restored.fields[3].value)
        assertEquals("Undergraduate", restored.status)
    }
    @Test
    fun testAiVisionEngineMovieTicketExtraction() {
        val lines = listOf(
            "AMC THEATRES",
            "MOVIE TICKET",
            "Screen: IMAX 4",
            "Row: G",
            "Seat: 12",
            "Showtime: 19:30",
            "Booking Ref: AMC-994812",
        )
        val text = lines.joinToString("\n")

        val result = AiVisionEngine.extractStructuredFields(
            frontText = text,
            backText = "",
            frontLines = lines,
            backLines = emptyList(),
        )

        assertEquals("Movie Ticket", result.category)
        assertEquals("AMC THEATRES", result.issuer)

        val fieldsMap = result.dynamicFields.associate { it.label to it.value }
        assertEquals("IMAX 4", fieldsMap["Screen"])
        assertEquals("G", fieldsMap["Row"])
        assertEquals("12", fieldsMap["Seat"])
        assertEquals("19:30", fieldsMap["Showtime"])
        assertEquals("AMC-994812", fieldsMap["Booking Ref"])
    }
    @Test
    fun testSambhramIdCardFrontAndBackExtraction() {
        val frontLines = listOf(
            "SAMBHRAM",
            "INSTITUTE OF TECHNOLOGY",
            "M.S. Palya, Bangalore-560097",
            "Name : Sharan Shrivatsav",
            "USN : 1ST25CS134",
            "Course : B.E",
            "Branch : Computer Science & Engineering",
            "Valid : 2025-2029",
            "Principal",
        )
        val backLines = listOf(
            "DOB : 12.04.2007",
            "Blood Group : -",
            "Mobile No. : 9844514129",
            "Address : #55/2, Near Begurappa Building, 3rd Cross, Byalakere, Bangalore - 560089",
            "IF FOUND RETURN TO: SAMBHRAM INSTITUTE OF TECHNOLOGY",
            "M.S. Palya, Bengaluru - 560 097",
        )

        val result = AiVisionEngine.extractStructuredFields(
            frontText = frontLines.joinToString("\n"),
            backText = backLines.joinToString("\n"),
            frontLines = frontLines,
            backLines = backLines,
        )

        assertEquals("Institutional ID", result.category)
        assertTrue("Issuer must be Sambhram Institute of Technology", result.issuer.contains("SAMBHRAM", ignoreCase = true) && result.issuer.contains("INSTITUTE", ignoreCase = true))
        assertFalse("Issuer must NEVER be 'Valid'", result.issuer.equals("Valid", ignoreCase = true))

        val fieldsMap = result.dynamicFields.associate { it.label to it.value }
        assertEquals("Sharan Shrivatsav", fieldsMap["Name"])
        assertEquals("1ST25CS134", fieldsMap["USN"])
        assertEquals("B.E", fieldsMap["Course"])
        assertEquals("Computer Science & Engineering", fieldsMap["Branch"])
        assertEquals("2025-2029", fieldsMap["Validity"])
        assertEquals("12.04.2007", fieldsMap["Date of Birth"])
        assertEquals("9844514129", fieldsMap["Mobile No"])
        assertEquals("#55/2, Near Begurappa Building, 3rd Cross, Byalakere, Bangalore - 560089", fieldsMap["Address"])

        // Ensure cities/pincodes were not extracted as fake fields
        assertFalse("Bengaluru must not be a field label", fieldsMap.containsKey("Bengaluru"))
        assertFalse("Bangalore must not be a field label", fieldsMap.containsKey("Bangalore"))
        assertFalse("Department must not be duplicated when Branch exists", fieldsMap.containsKey("Department"))
        assertFalse("2025 must not be a field label", fieldsMap.containsKey("2025"))
    }

    @Test
    fun testAiModelStatusTransitions() {
        val s1: AiModelStatus = AiModelStatus.NotDownloaded
        val s2: AiModelStatus = AiModelStatus.Downloading(0.5f, 8_000_000L, 16_000_000L, 2_000_000L)
        val s3: AiModelStatus = AiModelStatus.Verifying
        val s4: AiModelStatus = AiModelStatus.Ready("/models/vision_ocr_v1", 16_568_320L, "1.0.0")
        val s5: AiModelStatus = AiModelStatus.Error("Network error")

        assertTrue(s1 is AiModelStatus.NotDownloaded)
        assertTrue(s2 is AiModelStatus.Downloading)
        assertEquals(0.5f, (s2 as AiModelStatus.Downloading).progress)
        assertEquals(8_000_000L, s2.downloadedBytes)
        assertTrue(s3 is AiModelStatus.Verifying)
        assertTrue(s4 is AiModelStatus.Ready)
        assertEquals("1.0.0", (s4 as AiModelStatus.Ready).version)
        assertTrue(s5 is AiModelStatus.Error)
        assertEquals("Network error", (s5 as AiModelStatus.Error).message)
    }
    @Test
    fun testAndroidPackageManifestVerification() {
        val manifestFile = java.io.File("C:/Users/Shiro/Projects/cardamom-ai/releases/cardamom-ai-1.0.0/manifest.json")
        if (!manifestFile.exists()) return // Skip if standalone test run

        val jsonStr = manifestFile.readText(Charsets.UTF_8)
        val json = org.json.JSONObject(jsonStr)

        assertEquals("cardamom-ai-v1", json.getString("package_id"))
        assertEquals("1.0.0", json.getString("package_version"))
        assertEquals("1.0", json.getString("schema_version"))
        assertEquals(1, json.getInt("minimum_engine_api"))

        val modules = json.getJSONArray("modules")
        assertTrue(modules.length() >= 3)
        for (i in 0 until modules.length()) {
            val m = modules.getJSONObject(i)
            assertTrue(m.getString("id").isNotBlank())
            assertTrue(m.getString("artifact_path").isNotBlank())
            assertTrue(m.getLong("size_bytes") > 0L)
            assertEquals(64, m.getString("sha256").length)
        }
    }

    @Test
    fun testAndroidPackageChecksumsVerification() {
        val pkgDir = java.io.File("C:/Users/Shiro/Projects/cardamom-ai/releases/cardamom-ai-1.0.0")
        val checksumsFile = java.io.File(pkgDir, "checksums.sha256")
        if (!checksumsFile.exists()) return

        val md = java.security.MessageDigest.getInstance("SHA-256")
        val lines = checksumsFile.readLines(Charsets.UTF_8)
        assertTrue(lines.isNotEmpty())

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            assertEquals(2, parts.size)
            val expectedHash = parts[0]
            val relPath = parts[1]

            val targetFile = java.io.File(pkgDir, relPath)
            assertTrue("File listed in checksums must exist: $relPath", targetFile.exists())

            val bytes = targetFile.readBytes()
            val actualHash = md.digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals("Hash mismatch for $relPath", expectedHash.lowercase(), actualHash.lowercase())
        }
    }
}
