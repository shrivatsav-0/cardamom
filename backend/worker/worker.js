// Cardamom licensing worker — SECURITY NOTES
// - /v1/purchase/verify now FAILS CLOSED: it only marks paid after verifying
//   the purchase with the Google Play Developer API (Android Publisher API).
//   Configure these secrets/vars before deploying (wrangler secret put):
//     PLAY_SERVICE_ACCOUNT_JSON  - Google service-account JSON (androidpublisher scope)
//     PLAY_PACKAGE_NAME          - e.g. com.shrivatsav.cardamom (defaults to that)
//   If the service account is not configured, verification returns 503 and
//   NO record is marked paid. Never fall back to trusting client-supplied tokens.
// - device_hash is an anonymous identifier, NOT authentication. Trials are
//   therefore best-effort per-device-hash; a determined attacker can mint new
//   hashes. It is validated as strict lowercase hex SHA-256 to prevent KV abuse.

const TRIAL_DURATION_MS = 7 * 24 * 60 * 60 * 1000;
const PRODUCT_ID = "cardamom_lifetime_pro";
const DEFAULT_PACKAGE = "com.shrivatsav.cardamom";
const HEX64 = /^[a-f0-9]{64}$/;

function jsonResponse(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function b64urlDecode(str) {
  const normalized = str.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
}
function b64urlEncode(bytes) {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };
  const enc = (o) => b64urlEncode(new TextEncoder().encode(JSON.stringify(o)));
  const signingInput = `${enc(header)}.${enc(payload)}`;
  const pemBody = String(serviceAccount.private_key || "")
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const keyBytes = b64urlDecode(pemBody);
  const privateKey = await crypto.subtle.importKey(
    "pkcs8", keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false, ["sign"]
  );
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", privateKey,
    new TextEncoder().encode(signingInput)
  );
  const assertion = `${signingInput}.${b64urlEncode(new Uint8Array(sig))}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error("oauth exchange failed");
  const data = await res.json();
  if (!data.access_token) throw new Error("no access token");
  return data.access_token;
}

async function verifyWithPlay(env, purchaseToken, productId) {
  if (!env.PLAY_SERVICE_ACCOUNT_JSON) return { configured: false };
  let sa;
  try { sa = JSON.parse(env.PLAY_SERVICE_ACCOUNT_JSON); } catch { return { configured: false }; }
  const packageName = env.PLAY_PACKAGE_NAME || DEFAULT_PACKAGE;
  const token = await getAccessToken(sa);
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${encodeURIComponent(packageName)}/purchases/products/` +
    `${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (res.status === 404 || res.status === 410) return { configured: true, valid: false };
  if (!res.ok) throw new Error("play api error");
  const data = await res.json();
  return { configured: true, valid: data.purchaseState === 0 };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204 });
    if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);
    try {
      if (url.pathname === "/v1/trial/status") {
        const body = await request.json().catch(() => ({}));
        const deviceHash = body.device_hash;
        if (typeof deviceHash !== "string" || !HEX64.test(deviceHash)) {
          return jsonResponse({ error: "Valid device_hash required" }, 400);
        }
        const now = Date.now();
        let record = await env.CARDAMOM_KV.get(deviceHash, { type: "json" });
        if (!record) {
          record = {
            device_hash: deviceHash,
            first_activated: now,
            expires_at: now + TRIAL_DURATION_MS,
            is_paid: false,
            created_at: new Date(now).toISOString(),
          };
          await env.CARDAMOM_KV.put(deviceHash, JSON.stringify(record));
        }
        const isPaid = Boolean(record.is_paid);
        const isExpired = !isPaid && now >= record.expires_at;
        const msRemaining = Math.max(0, record.expires_at - now);
        const daysLeft = isPaid ? 0 : Math.max(0, Math.ceil(msRemaining / (24 * 60 * 60 * 1000)));
        const status = isPaid ? "PAID" : isExpired ? "EXPIRED" : "ACTIVE";
        return jsonResponse({
          status, days_left: daysLeft, server_time: now,
          first_activated: record.first_activated,
          expires_at: record.expires_at, is_paid: isPaid,
        });
      }
      if (url.pathname === "/v1/purchase/verify") {
        const body = await request.json().catch(() => ({}));
        const deviceHash = body.device_hash;
        const purchaseToken = body.purchase_token;
        const productId = body.product_id || PRODUCT_ID;
        if (typeof deviceHash !== "string" || !HEX64.test(deviceHash) || !purchaseToken || typeof purchaseToken !== "string") {
          return jsonResponse({ error: "device_hash and purchase_token required" }, 400);
        }
        if (productId !== PRODUCT_ID) return jsonResponse({ error: "Unknown product" }, 400);
        const verification = await verifyWithPlay(env, purchaseToken, productId);
        if (!verification.configured) return jsonResponse({ error: "Purchase verification not configured" }, 503);
        if (!verification.valid) return jsonResponse({ error: "Purchase not valid" }, 403);
        const now = Date.now();
        let record = await env.CARDAMOM_KV.get(deviceHash, { type: "json" });
        if (!record) {
          record = { device_hash: deviceHash, first_activated: now, expires_at: now, is_paid: true, purchased_at: now };
        } else {
          record.is_paid = true;
          record.purchased_at = now;
        }
        const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(purchaseToken));
        record.purchase_token_hash = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
        delete record.purchase_token;
        await env.CARDAMOM_KV.put(deviceHash, JSON.stringify(record));
        return jsonResponse({ status: "PAID", is_paid: true, server_time: now });
      }
      return jsonResponse({ error: "Endpoint not found" }, 404);
    } catch (err) {
      return jsonResponse({ error: "Internal server error" }, 500);
    }
  },
};
