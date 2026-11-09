# Cardamom Licensing API (Cloudflare Worker)

Zero-knowledge, privacy-preserving trial and license backend for Cardamom.

### Features
* **Zero PII**: Stores only `SHA256(ANDROID_ID + salt)` and timestamps. Zero user data, zero documents, zero cards, zero names or emails.
* **Tamper-Proof**: Tracks the 7-day expiration server-side so clearing app data, reinstalling the app, or rolling back device clocks cannot reset the trial.
* **Global & Free**: Deploys instantly to Cloudflare Workers with global sub-10ms latency (100,000 free requests/day).

---

### 2-Minute Deployment Guide

1. **Install Wrangler (if not already installed)**:
   ```bash
   npm install -g wrangler
   ```

2. **Login to Cloudflare**:
   ```bash
   npx wrangler login
   ```

3. **Create the Cloudflare KV Namespace**:
   ```bash
   npx wrangler kv:namespace create CARDAMOM_KV
   ```
   Copy the `id` from the output into `backend/worker/wrangler.toml` under `[[kv_namespaces]]`.

4. **Deploy**:
   ```bash
   npx wrangler deploy
   ```
   Wrangler will print your live worker URL (e.g. `https://cardamom-licensing-api.<your-subdomain>.workers.dev`).
   Put this endpoint in Cardamom's `LicenseConfig.WORKER_ENDPOINT`.
