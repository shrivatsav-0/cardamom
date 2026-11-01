# Cardamom

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_11+-green.svg)](https://android.com)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM-red.svg)](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
[![Offline First](https://img.shields.io/badge/Architecture-100%25_Offline--First-orange.svg)]()

**Cardamom** is a private, offline-first digital wallet and personal document organizer for Android. It replaces cloud-dependent wallets and data-collecting aggregators with an encrypted personal vault that runs entirely on your phone.

---

## Highlights & Features

- **3D Flippable Digital Cards:** Digital cards replicate actual physical cards with interactive 3D perspective rotation. Tap or swipe to flip between front and back faces with strict zero-shift geometry and a raised physical-look corner.
- **100% Offline-First:** Zero remote servers, zero user accounts, zero analytics, and zero tracking SDKs. Data never leaves your device.
- **Hardware-Backed AES-256-GCM Encryption:** Plaintext records and images are encrypted locally with keys managed by the Android Keystore (StrongBox / TEE).
- **On-Device Vision & OCR:** Instant local optical character recognition and field parsing powered by Google ML Kit and optional local deep neural models (PaddleOCR).
- **Multi-Page Document Scanner:** Camera scanning with stacked controls (`Capture Page X` on top, live page counter and `Done` button below), automatic perspective unwarping, PDF assembly, and swipable edge-to-edge previews.
- **Universal Barcode & QR Code Engine:** Instant real-time detection and SVG rendering across 8 barcode formats (QR, Code 128, Code 39, EAN-13, EAN-8, UPC-A, PDF-417, Aztec).
- **Dual-Sided Photography & In-App Viewer:** Capture and inspect high-resolution front and back card photos in true aspect ratio with an in-app zoomable viewer.
- **Encrypted Self-Custody Backups:** Export complete vaults as `.cardamom` backup files encrypted with PBKDF2-SHA256 (200,000 rounds) and AES-256-GCM.

---

## 3D Interactive Digital Card Experience

```
+-------------------------------------------------------------+
|  CARDAMOM PHYSICAL-FEEL DIGITAL CARD                        |
|                                                             |
|  SHARAN SHRIVATSAV                                          |
|  Sambhram Institute of Technology                           |
|                                                             |
|  USN: 1ST25CS134          VALIDITY: 2025-2029               |
|  COURSE: B.E              BRANCH: Computer Science          |
+---------------------------------------------------------+---+
                                                          | → | <- Peeking flip button
                                                          +---+
```

- **Front Face:** Bold card title, institution subtitle, live barcode panel, and front attributes (*USN*, *Course*, *Branch*, *Validity*).
- **Back Face:** Mirrored upright with bold typography for *Date of Birth*, *Mobile No*, full multi-line *Address*, *Blood Group*, and *Transferability* status.
- **Static Peeking Action Button:** A 52dp circular action button sits underneath on the bottom-right corner (`[card]->`), half-exposed beneath the raised card edge, providing a clear visual cue to flip.

---

## Architecture & Technology Stack

| Layer | Component / Library |
| :--- | :--- |
| **Language & UI** | Kotlin 2.1+, Jetpack Compose, Material 3, Navigation Compose |
| **Local Cryptography** | Android Keystore (StrongBox/TEE), AES-256-GCM, PBKDF2-SHA256 |
| **On-Device OCR** | Google ML Kit Text Recognition (Default on-device), PaddleOCR |
| **Barcode Processing** | Google ML Kit Barcode Scanning + ZXing Core |
| **Camera & Imaging** | CameraX (Camera2, Lifecycle, View), Android `PdfRenderer` |
| **Biometric Security** | AndroidX `BiometricPrompt` (`BIOMETRIC_STRONG` + Device Lock) |
| **Screen Protection** | Android `FLAG_SECURE` window enforcement |

---

## Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat or later
- Android SDK 37 (Build Tools 35.0.0+)
- JDK 17 or JDK 21

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/shrivatsav-0/cardamom.git
cd cardamom

# Run unit and regression tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Build minified, R8-optimized release APK
./gradlew assembleRelease
```

The compiled release APK is generated at:
```
app/build/outputs/apk/release/app-release.apk
```

### Install onto Connected Device
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## Documentation & Showcase

- **Complete Technical Specification:** [DOCUMENTATION.md](./DOCUMENTATION.md) — Exhaustive specification covering every screen, entity, cipher, and navigation route.
- **Unique Features & Video Script:** [showcase.md](./showcase.md) — 8 standout features and a 30-second promotional video script.
- **Privacy Policy:** [PRIVACY_POLICY.md](./PRIVACY_POLICY.md)
- **Terms of Service:** [TERMS_OF_SERVICE.md](./TERMS_OF_SERVICE.md)

---

## License

Cardamom is open-source software licensed under the **Apache License, Version 2.0**.  
See [LICENSE](./LICENSE) or [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0) for details.
