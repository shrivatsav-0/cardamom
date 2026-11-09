#!/usr/bin/env python3
"""
Uploads a signed Android App Bundle (.aab) to Google Play Console via the Android Publisher API.
Works with standard Python 3 (using standard urllib, json, base64, time, ssl, or google-api-python-client).
"""

import argparse
import base64
import json
import os
import sys
import time
import urllib.request
import urllib.parse

def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).decode("utf-8").rstrip("=")

def make_jwt(service_account: dict) -> str:
    import hmac
    import hashlib
    # Try cryptography library if available, otherwise check openssl subprocess
    client_email = service_account.get("client_email")
    private_key_pem = service_account.get("private_key")
    if not client_email or not private_key_pem:
        raise ValueError("service account missing client_email or private_key")
    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT"}
    payload = {
        "iss": client_email,
        "scope": "https://www.googleapis.com/auth/androidpublisher",
        "aud": "https://oauth2.googleapis.com/token",
        "exp": now + 3600,
        "iat": now,
    }
    encoded_header = b64url(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    encoded_payload = b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    to_sign = f"{encoded_header}.{encoded_payload}".encode("utf-8")

    try:
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.asymmetric import padding
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        private_key = load_pem_private_key(private_key_pem.encode("utf-8"), password=None)
        sig = private_key.sign(to_sign, padding.PKCS1v15(), hashes.SHA256())
        return f"{encoded_header}.{encoded_payload}.{b64url(sig)}"
    except ImportError:
        # Fallback to openssl cli. Never write the private key to a predictable
        # CWD file: pipe it via stdin using a temp file with 0600 perms in the
        # system temp dir, and always remove it.
        import subprocess
        import tempfile
        fd, key_path = tempfile.mkstemp(suffix="_cardamom_key.pem")
        try:
            os.chmod(key_path, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                f.write(private_key_pem)
            cmd = ["openssl", "dgst", "-sha256", "-sign", key_path]
            res = subprocess.run(cmd, input=to_sign, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
            sig = res.stdout
            return f"{encoded_header}.{encoded_payload}.{b64url(sig)}"
        finally:
            try:
                os.remove(key_path)
            except OSError:
                pass

def get_access_token(service_account: dict) -> str:
    assertion = make_jwt(service_account)
    data = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion
    }).encode("utf-8")
    req = urllib.request.Request("https://oauth2.googleapis.com/token", data=data)
    with urllib.request.urlopen(req) as resp:
        res_json = json.loads(resp.read().decode("utf-8"))
        return res_json["access_token"]

def upload_bundle(package_name: str, aab_path: str, track: str, status: str, service_account: dict, release_notes: str = ""):
    token = get_access_token(service_account)
    auth_headers = {"Authorization": f"Bearer {token}"}

    # 1. Create edit
    edit_req = urllib.request.Request(
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package_name}/edits",
        headers={**auth_headers, "Content-Type": "application/json"},
        data=b"{}",
        method="POST"
    )
    with urllib.request.urlopen(edit_req) as resp:
        edit_id = json.loads(resp.read().decode("utf-8"))["id"]
        print(f"[OK] Edit created: {edit_id}")

    try:
        # 2. Upload bundle
        with open(aab_path, "rb") as f:
            bundle_data = f.read()

        upload_req = urllib.request.Request(
            f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{package_name}/edits/{edit_id}/bundles?uploadType=media",
            headers={**auth_headers, "Content-Type": "application/octet-stream"},
            data=bundle_data,
            method="POST"
        )
        print(f"Uploading AAB bundle ({len(bundle_data)/(1024*1024):.2f} MB)...")
        with urllib.request.urlopen(upload_req) as resp:
            upload_res = json.loads(resp.read().decode("utf-8"))
            version_code = upload_res["versionCode"]
            print(f"[OK] AAB uploaded. Version Code: {version_code}")

        # 3. Update track
        release_obj = {
            "versionCodes": [str(version_code)],
            "status": status,
        }
        if release_notes:
            release_obj["releaseNotes"] = [{"language": "en-US", "text": release_notes[:500]}]

        track_payload = json.dumps({"track": track, "releases": [release_obj]}).encode("utf-8")
        track_req = urllib.request.Request(
            f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package_name}/edits/{edit_id}/tracks/{track}",
            headers={**auth_headers, "Content-Type": "application/json"},
            data=track_payload,
            method="PUT"
        )
        with urllib.request.urlopen(track_req) as resp:
            print(f"[OK] Track '{track}' updated with versionCode {version_code}")

        # 4. Commit edit
        commit_req = urllib.request.Request(
            f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package_name}/edits/{edit_id}:commit",
            headers=auth_headers,
            data=b"",
            method="POST"
        )
        with urllib.request.urlopen(commit_req) as resp:
            print("[OK] Google Play release committed successfully!")

    except Exception as e:
        print(f"[ERROR] Failed during Play Store upload: {e}", file=sys.stderr)
        # Abort edit
        try:
            abort_req = urllib.request.Request(
                f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package_name}/edits/{edit_id}",
                headers=auth_headers,
                method="DELETE"
            )
            urllib.request.urlopen(abort_req)
        except Exception:
            pass
        raise

def main():
    parser = argparse.ArgumentParser(description="Upload AAB to Google Play Console")
    parser.add_argument("--aab", required=True, help="Path to .aab file")
    parser.add_argument("--package", default="com.shrivatsav.cardamom", help="App package name")
    parser.add_argument("--key", help="Path to Service Account JSON key file")
    parser.add_argument("--track", default="internal", choices=["internal", "alpha", "beta", "production"])
    parser.add_argument("--status", default="completed", choices=["completed", "draft", "inProgress"])
    parser.add_argument("--notes", help="Path to release notes text file")
    args = parser.parse_args()

    key_json = None
    if args.key and os.path.exists(args.key):
        with open(args.key, "r", encoding="utf-8") as f:
            key_json = json.load(f)
    elif os.environ.get("PLAY_STORE_JSON_KEY"):
        key_json = json.loads(os.environ["PLAY_STORE_JSON_KEY"])
    elif os.environ.get("GOOGLE_PLAY_KEY_FILE") and os.path.exists(os.environ["GOOGLE_PLAY_KEY_FILE"]):
        with open(os.environ["GOOGLE_PLAY_KEY_FILE"], "r", encoding="utf-8") as f:
            key_json = json.load(f)
    else:
        print("Error: No Google Play Service Account JSON key provided.", file=sys.stderr)
        sys.exit(1)

    notes_text = ""
    if args.notes and os.path.exists(args.notes):
        with open(args.notes, "r", encoding="utf-8") as f:
            notes_text = f.read().strip()

    upload_bundle(args.package, args.aab, args.track, args.status, key_json, notes_text)

if __name__ == "__main__":
    main()
