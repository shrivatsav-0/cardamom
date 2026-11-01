# Cardamom — App Showcase & Features

Cardamom is a private, offline-first digital wallet and personal document organizer for Android. It replaces bloated, cloud-dependent wallet apps with a secure, hardware-encrypted personal vault that runs 100% on your device.

---

## 8 Standout Features

### 1. 3D Flippable Physical-Feel Digital Cards
- Digital cards replicate the experience of actual physical cards.
- **Swipe or Tap to Flip**: Smooth 3D perspective animation rotates the card 180° between Front and Back faces.
- **Fixed Zero-Shift Geometry**: Front and back share identical fixed dimensions so flipping never jumps or resizes.
- **Raised Corner Cue**: The card's right edge is subtly raised in 3D perspective above a peeking circular button to indicate that the card has a back.

### 2. 100% Offline-First Architecture
- **Zero Cloud Transmission**: Cardamom operates completely offline with zero remote servers, zero analytics, zero crash telemetry, and zero tracking SDKs.
- Your identity cards, tickets, passes, and documents stay strictly in your device's internal storage.

### 3. Hardware-Backed Local Cryptography (AES-256-GCM)
- Every stored wallet card, document record, and extracted field is encrypted at rest with **AES-256-GCM**.
- Cryptographic keys reside inside the **hardware-backed Android Keystore** (StrongBox / TEE).
- Features biometric authentication (`BIOMETRIC_STRONG`) and enforced `FLAG_SECURE` to prevent screen recorders and task switchers from capturing credentials.

### 4. Instant On-Device AI & Multilingual OCR
- On-device text recognition transcribes cards and documents locally in milliseconds with **zero cloud latency**.
- Uses embedded Google ML Kit as the default engine (Latin, Devanagari, accents) with **zero blocking model downloads required**.
- Supports optional on-device deep neural OCR engines (PaddleOCR) for air-gapped environments.

### 5. Multi-Page Document Scanner & Swipable Previews
- Multi-page camera capture with stacked controls (`Capture Page X` on top, live page counter and `Done` button on bottom).
- **Home Screen Swiping**: Swipe through multi-page documents directly from the home screen tile without opening detail view.
- **Edge-to-Edge Framing**: Previews fill the container completely with zero white letterbox lines.
- **Smart Issuer Detection**: Automatically extracts issuing authorities and provides 1-tap suggested attribute chips (*Document No*, *Date of Issue*, *Expiry Date*, *Holder Name*).

### 6. Dual-Sided Card Capture & High-Fidelity Photo/File Viewer
- Card detail screen features two dedicated tabs: **Digital card** and **Photo / File**.
- Preserves full camera capture resolution with native aspect ratio rendering (portrait and landscape).
- In-app zoomable full-screen viewer for photos and multi-page PDFs.

### 7. Universal Barcode & QR Code Engine
- Instant auto-scanning and crisp SVG/Canvas rendering across 8 symbologies:
  $$\text{QR Code} \quad \text{Code 128} \quad \text{Code 39} \quad \text{EAN-13} \quad \text{EAN-8} \quad \text{UPC-A} \quad \text{PDF-417} \quad \text{Aztec}$$
- Includes screen brightness auto-boost for instant optical scanning at checkout terminals and airport gates.

### 8. Self-Custody Encrypted Backups
- Export complete wallet vaults as encrypted `.cardamom` backup files.
- Protected by **PBKDF2-SHA256 (200,000 rounds)** key derivation and **AES-256-GCM** encryption using your custom passphrase.
- Complete data portability and sovereignty with zero vendor lock-in.

---

## 30-Second Video Script

**Target Duration:** ~30 seconds  
**Tone:** Fast-paced, punchy, modern, tech-forward  
**Music:** Minimalist energetic electronic beat with crisp tactile sound effects  

---

| Timestamp | Visual on Screen | Spoken Voiceover / Audio |
| :--- | :--- | :--- |
| **0:00 – 0:04** | Fast cut: Messy physical wallet overflowing with cards, followed by phone screen cold-starting Cardamom into a clean Material 3 dark-mode wallet. | *"Tired of carrying a bloated wallet or trusting cloud apps with your sensitive IDs?"* |
| **0:04 – 0:10** | Camera snaps a student ID badge. On-device OCR instantly recognizes Name, USN, Course, and Validity. Card tile appears in wallet. | *"Meet Cardamom. Point your camera, and on-device AI instantly scans your cards and barcodes—offline, in milliseconds."* |
| **0:10 – 0:17** | Finger taps card. Card smoothly flips 180° in 3D perspective to reveal the back with Address, DOB, and Mobile. Camera switches to document scan, capturing 3 pages and swiping across them on the home screen. | *"Digital cards that swipe and flip just like real ones. Multi-page document scanning with full PDF export and zero cloud delays."* |
| **0:17 – 0:24** | Close-up of Android Keystore shield icon + biometric fingerprint unlock flashing green. Screen security indicator shows local AES-256-GCM. | *"Your data never touches a server. Hardware-encrypted with AES-256, biometric app lock, and zero analytics."* |
| **0:24 – 0:30** | Cardamom app icon animates into place with GitHub logo, "Open Source · 100% Offline · Apache 2.0", and download button. | *"Private. Secure. 100% open source. Download Cardamom today and take back your privacy."* |

---

### Production Notes
- **SFX**: Include subtle tactile card-flip whoosh at `0:11`, haptic click at `0:07` on scan detection, and a soft lock click at `0:18` on biometric verification.
- **Visuals**: Emphasize the tactile raised corner on the digital card and the smooth 3D flip interaction.
