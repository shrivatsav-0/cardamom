# Privacy Policy for Cardamom

**Effective Date:** November 1, 2026  
**Application:** Cardamom — Private Offline Digital Wallet & Document Organizer  

Cardamom is built from the ground up on a fundamental commitment: **your personal data, identity cards, documents, barcodes, and credentials belong to you and stay strictly on your device.**

---

## 1. Zero Cloud Transmission & No Remote Servers
- Cardamom operates **100% offline-first**.
- We run **no remote servers**, maintain **no cloud databases**, and host **no user accounts**.
- Cardamom contains **zero analytics SDKs, zero telemetry collectors, zero crash trackers, and zero advertising libraries**.
- Your camera feed, scanned images, card numbers, and document files are processed directly in your device's memory and written only to your phone's local internal storage.

---

## 2. Hardware-Backed Local Cryptography
- Every stored wallet card, document record, and extracted attribute is encrypted at rest using **AES-256-GCM**.
- Cryptographic keys are generated and protected within the **hardware-backed Android Keystore** (utilizing StrongBox or TEE where supported by your hardware).
- Plaintext records cannot be decrypted or accessed outside the authenticated application context.

---

## 3. On-Device AI & OCR Text Recognition
- Text recognition, barcode detection, and document segmentation execute **strictly on-device** using embedded Google ML Kit and optional local neural networks (PaddleOCR).
- No image bytes, captured frames, or OCR transcriptions are ever uploaded, transmitted, or queried against external API endpoints.

---

## 4. Biometric App Lock & Screen Security
- Cardamom integrates Android BiometricPrompt (`BIOMETRIC_STRONG` / device credential lock).
- When enabled, biometric authentication is verified locally by the Android OS framework; Cardamom neither sees nor stores your biometric data.
- **FLAG_SECURE** is enforced across the application to prevent screen recorders, malicious apps, and recent task switchers from capturing your sensitive credentials.

---

## 5. Backups & User Custody
- When you export a backup, the file is encrypted with a key derived from your custom passphrase using **PBKDF2-SHA256 with 200,000 rounds** and encrypted via **AES-256-GCM**.
- You maintain 100% custody and control of your `.cardamom` backup files.
- The developers have zero access to your backup passwords and cannot recover lost passphrases.

---

## 6. Complete Data Deletion
- When you delete a card or document, its plaintext record, encrypted database entry, and associated image/PDF cache files are immediately and permanently erased from your device filesystem.
- Uninstalling the application completely wipes all encrypted records and keys.

---

## 7. Open Source & Verifiable
Cardamom is open source under the **Apache 2.0 license**. Its source code and cryptographic implementation are publicly auditable at [github.com/shrivatsav-0/cardamom](https://github.com/shrivatsav-0/cardamom).

---

## 8. Contact & Inquiries
For questions, vulnerability disclosures, or security reviews:
- **Repository:** [https://github.com/shrivatsav-0/cardamom](https://github.com/shrivatsav-0/cardamom)
- **Issues:** [https://github.com/shrivatsav-0/cardamom/issues](https://github.com/shrivatsav-0/cardamom/issues)
