#!/usr/bin/env bash
set -euo pipefail

export LC_ALL=C.UTF-8
export LANG=C.UTF-8

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
GRADLE_PROPS="$ROOT_DIR/app/build.gradle.kts"
NOTES_SRC="$ROOT_DIR/playstore_release_notes.txt"

if command -v cygpath >/dev/null 2>&1; then
  GRADLE_ROOT="$(cygpath -m "$ROOT_DIR")"
else
  GRADLE_ROOT="$ROOT_DIR"
fi

BUILD_TYPE="release"
VARIANT="all"
BUMP="patch"
SKIP_TESTS=false
GEN_NOTES=false
PUBLISH=false
UPLOAD_PLAYSTORE=false
PLAYSTORE_KEY=""
PLAY_TRACK="internal"
PLAY_STATUS="completed"
PLAYSTORE_MAX_CHARS=500

usage() {
  cat <<'USAGE'
Usage: build_artifacts.sh [release|debug] [--variant play|github|all] [--bump none|patch|minor|major] [--skip-tests] [--generate-notes] [--publish] [--upload-playstore] [--playstore-key <path>] [--track <internal|alpha|beta|production>]

  --variant <target>   Target distribution channel (alias: --platform). Default: 'all'.
                       play    Google Play Store (signed AAB + release APK)
                       github  Split APKs for GitHub Releases (arm64-v8a, armeabi-v7a, x86_64, x86, universal)
                       all     Both variants

  --bump <component>   How to change versionName before building. Default: 'patch'.
                       patch   bump 3rd component: 1.0.0 -> 1.0.1
                       minor   bump 2nd component: 1.0.0 -> 1.1.0
                       major   bump 1st component: 1.0.0 -> 2.0.0
                       none    keep versionName unchanged

  --skip-tests         Skip running root project unit tests before building.
  --generate-notes     Create or refresh default playstore_release_notes.txt.
  --print-notes-template Print release notes guidelines and template.
  --publish            Publish GitHub release via gh CLI.
  --upload-playstore   Upload Play Store AAB automatically via Android Publisher API.
  --playstore-key <f>  Path to Google Cloud Service Account JSON key.
  --track <track>      Target Google Play track (default: internal).
USAGE
}

print_notes_template() {
  cat <<'TEMPLATE'
=== PLAY STORE RELEASE NOTES TEMPLATE ===
Limit: 500 characters total (Google Play rejects at upload time).

  <Feature summary>            <- line 1, plain text, NO bullet, <= 70 chars
                               <- line 2, blank
- <one user-facing change>     <- 3 to 5 bullets, each prefixed "- "
- <one user-facing change>
- <one user-facing change>

Rules:
  - Plain text only. No markdown headers, bold or italics.
  - Write for end users, not developers: no class or API names.
  - One change per bullet, past tense, <= 100 characters per bullet.
  - Lead with the most visible change.
  - 500 characters is the hard cap.
=== END TEMPLATE ===
TEMPLATE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    release|debug) BUILD_TYPE="$1" ;;
    --variant|--platform) shift; VARIANT="${1:-all}" ;;
    --variant=*|--platform=*) VARIANT="${1#*=}" ;;
    --bump) shift; BUMP="${1:-patch}" ;;
    --bump=*) BUMP="${1#*=}" ;;
    --skip-tests) SKIP_TESTS=true ;;
    --generate-notes) GEN_NOTES=true ;;
    --print-notes-template) print_notes_template; exit 0 ;;
    --publish) PUBLISH=true ;;
    --upload-playstore) UPLOAD_PLAYSTORE=true ;;
    --playstore-key) shift; PLAYSTORE_KEY="${1:-}" ;;
    --playstore-key=*) PLAYSTORE_KEY="${1#*=}" ;;
    --track) shift; PLAY_TRACK="${1:-internal}" ;;
    --track=*) PLAY_TRACK="${1#*=}" ;;
    --status) shift; PLAY_STATUS="${1:-completed}" ;;
    --status=*) PLAY_STATUS="${1#*=}" ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Error: unknown argument '$1'" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

case "$BUILD_TYPE" in
  release) BUILD_CAP="Release" ;;
  debug) BUILD_CAP="Debug" ;;
  *) BUILD_CAP="Release" ;;
esac

run_gradle() {
  if [[ -x "$ROOT_DIR/gradlew" ]]; then
    "$ROOT_DIR/gradlew" -p "$GRADLE_ROOT" "$@"
  elif [[ -f "$ROOT_DIR/gradlew.bat" && "$OSTYPE" == "msys" ]]; then
    cmd.exe //c "gradlew.bat -p \"$GRADLE_ROOT\" $*"
  else
    "$ROOT_DIR/gradlew" -p "$GRADLE_ROOT" "$@"
  fi
}

read_version() {
  sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE_PROPS" | tr -d '\r' | head -n 1
}

read_version_code() {
  sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE_PROPS" | tr -d '\r' | head -n 1
}

read_min_sdk() {
  sed -n 's/.*minSdk = \([0-9]*\).*/\1/p' "$GRADLE_PROPS" | tr -d '\r' | head -n 1
}

compute_next_version() {
  local comp="$1" curr="$2" x y z
  if [[ "$curr" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    x="${BASH_REMATCH[1]}"
    y="${BASH_REMATCH[2]}"
    z="${BASH_REMATCH[3]}"
  elif [[ "$curr" =~ ^([0-9]+)\.([0-9]+)$ ]]; then
    x="${BASH_REMATCH[1]}"
    y="${BASH_REMATCH[2]}"
    z="0"
  else
    echo "$curr"
    return 0
  fi
  case "$comp" in
    patch) echo "$x.$y.$(( z + 1 ))" ;;
    minor) echo "$x.$(( y + 1 )).0" ;;
    major) echo "$(( x + 1 )).0.0" ;;
    none)  echo "$curr" ;;
  esac
}

generate_notes_template() {
  local target_ver="$1"
  cat > "$NOTES_SRC" <<EOF
Cardamom v$target_ver: Private offline wallet with 3D cards and fast scanner

- Added 3D flippable cards with front and back interactive perspectives
- Added on-device OCR and instant universal barcode scanner
- Added multi-page document camera scanner with perspective correction
- Reduced app download size with optimized architecture split packages
- Added AES-256-GCM encrypted self-custody vault backups
EOF
  echo "Generated release notes template at $NOTES_SRC"
}

validate_notes() {
  local path="$1" limit="$2"
  if [[ ! -f "$path" ]]; then
    echo "Error: $path not found." >&2
    return 1
  fi
  local text
  text="$(tr -d '\r' < "$path" | sed -e :a -e '/^\n*$/{$d;N;};/\n$/ba')"
  local chars="${#text}"
  if (( chars > limit )); then
    echo "Error: $chars characters exceeds Google Play limit ($limit)." >&2
    return 1
  fi
  echo "  [OK] Release notes validated ($chars/$limit characters)."
}

get_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    powershell.exe -NoProfile -Command "(Get-FileHash -Path '$1' -Algorithm SHA256).Hash.ToLowerInvariant()" | tr -d '\r'
  fi
}

echo "=========================================================="
echo "           CARDAMOM BUILD & RELEASE PIPELINE             "
echo "=========================================================="
echo "Target Build Type: $BUILD_TYPE"
echo "Target Variant:    $VARIANT"
echo "Version Bump:      $BUMP"
echo ""

CURR_VER="$(read_version)"
if [[ "$BUMP" != "none" ]]; then
  NEXT_VER="$(compute_next_version "$BUMP" "$CURR_VER")"
else
  NEXT_VER="$CURR_VER"
fi

if [[ "$GEN_NOTES" == "true" || ! -f "$NOTES_SRC" ]]; then
  generate_notes_template "$NEXT_VER"
fi

# Step 1: Root Tests
if [[ "$SKIP_TESTS" != "true" ]]; then
  echo "=== Testing Project Root Folder (app:test) ==="
  run_gradle test
  echo "[OK] All unit tests passed cleanly."
  echo ""
else
  echo "Notice: skipping unit tests (--skip-tests)."
fi

# Step 2: Version Bumping
PREV_CODE="$(read_version_code)"
if [[ "$BUMP" == "none" ]]; then
  echo "Notice: Current version (v$CURR_VER, code $PREV_CODE) is already latest; skipping version update."
  NEXT_VER="$CURR_VER"
  NEXT_CODE="$PREV_CODE"
else
  NEXT_VER="$(compute_next_version "$CURR_VER" "$BUMP")"
  NEXT_CODE="$PREV_CODE"
  if [[ "$BUILD_TYPE" == "release" ]]; then
    NEXT_CODE=$(( PREV_CODE + 1 ))
  fi

  echo "=== Version Configuration ==="
  echo "versionName: $CURR_VER -> $NEXT_VER"
  if [[ "$BUILD_TYPE" == "release" ]]; then
    echo "versionCode: $PREV_CODE -> $NEXT_CODE"
  else
    echo "versionCode: $PREV_CODE (unchanged for debug build)"
  fi
  echo ""

  sed -i.bak \
    -e "s/versionName = \"$CURR_VER\"/versionName = \"$NEXT_VER\"/" \
    -e "s/versionCode = $PREV_CODE/versionCode = $NEXT_CODE/" \
    "$GRADLE_PROPS"
  rm -f "$GRADLE_PROPS.bak"
fi
# Step 3: Validate Notes
echo "=== Validating Release Notes ==="
validate_notes "$NOTES_SRC" "$PLAYSTORE_MAX_CHARS"
echo ""

# Step 4: Gradle Builds
if [[ "$VARIANT" == "play" || "$VARIANT" == "all" ]]; then
  echo "=== Building Play Store Variant ==="
  if [[ "$BUILD_TYPE" == "release" ]]; then
    run_gradle assemblePlayRelease bundlePlayRelease -PsplitApks=false -x lintVitalAnalyzePlayRelease -x lintVitalReportPlayRelease
  else
    run_gradle assemblePlayDebug -PsplitApks=false
  fi
  echo "[OK] Play Store variant built successfully."
  echo ""
fi

if [[ "$VARIANT" == "github" || "$VARIANT" == "all" ]]; then
  echo "=== Building GitHub Split APK Variant ==="
  if [[ "$BUILD_TYPE" == "release" ]]; then
    run_gradle assembleGithubRelease -PsplitApks=true -x lintVitalAnalyzeGithubRelease -x lintVitalReportGithubRelease
  else
    run_gradle assembleGithubDebug -PsplitApks=true
  fi
  echo "[OK] GitHub split APK variant built successfully."
  echo ""
fi

# Step 5: Artifact Assembly
OUT_ROOT="$ROOT_DIR/dist/$BUILD_CAP/v$NEXT_VER"
mkdir -p "$OUT_ROOT"

if [[ "$VARIANT" == "play" || "$VARIANT" == "all" ]]; then
  PLAY_OUT="$OUT_ROOT/play"
  mkdir -p "$PLAY_OUT"
  if [[ "$BUILD_TYPE" == "release" ]]; then
    SRC_APK="$ROOT_DIR/app/build/outputs/apk/play/release/app-play-release.apk"
    SRC_AAB="$ROOT_DIR/app/build/outputs/bundle/playRelease/app-play-release.aab"
    DST_APK="$PLAY_OUT/Cardamom-v$NEXT_VER-play.apk"
    DST_AAB="$PLAY_OUT/Cardamom-v$NEXT_VER-play.aab"
    cp "$SRC_APK" "$DST_APK"
    cp "$SRC_AAB" "$DST_AAB"
    cp "$NOTES_SRC" "$PLAY_OUT/playstore_release_notes.txt"
    APK_SHA="$(get_sha256 "$DST_APK")"
    AAB_SHA="$(get_sha256 "$DST_AAB")"
    echo "$APK_SHA  Cardamom-v$NEXT_VER-play.apk" > "$DST_APK.sha256"
    echo "$AAB_SHA  Cardamom-v$NEXT_VER-play.aab" > "$DST_AAB.sha256"
  else
    SRC_APK="$ROOT_DIR/app/build/outputs/apk/play/debug/app-play-debug.apk"
    cp "$SRC_APK" "$PLAY_OUT/Cardamom-v$NEXT_VER-debug-play.apk"
  fi
fi

if [[ "$VARIANT" == "github" || "$VARIANT" == "all" ]]; then
  GITHUB_OUT="$OUT_ROOT/github"
  mkdir -p "$GITHUB_OUT"
  if [[ "$BUILD_TYPE" == "release" ]]; then
    CHECKSUMS=()
    ARTIFACT_JSONS=()
    TABLE_ROWS=""
    for abi in arm64-v8a armeabi-v7a x86_64 x86 universal; do
      SRC_SPLIT="$ROOT_DIR/app/build/outputs/apk/github/release/app-github-$abi-release.apk"
      DST_NAME="Cardamom-v$NEXT_VER-$abi.apk"
      DST_SPLIT="$GITHUB_OUT/$DST_NAME"
      if [[ -f "$SRC_SPLIT" ]]; then
        cp "$SRC_SPLIT" "$DST_SPLIT"
        SHA="$(get_sha256 "$DST_SPLIT")"
        echo "$SHA  $DST_NAME" > "$DST_SPLIT.sha256"
        CHECKSUMS+=("$SHA  $DST_NAME")
        SIZE_HUMAN="$(du -h "$DST_SPLIT" | awk '{print $1}')"
        TABLE_ROWS+=$'\n'"| \`$abi\` | $DST_NAME | $SIZE_HUMAN | \`$SHA\` |"
        ARTIFACT_JSONS+=("    { \"abi\": \"$abi\", \"file\": \"$DST_NAME\", \"sha256\": \"$SHA\" }")
      fi
    done
    printf "%s\n" "${CHECKSUMS[@]}" > "$GITHUB_OUT/SHA256SUMS.txt"
    
    # Generate github_release_notes.md
    cat > "$GITHUB_OUT/github_release_notes.md" <<MD
## Cardamom v$NEXT_VER

**$(head -n 1 "$NOTES_SRC")**

$(tail -n +3 "$NOTES_SRC")

### Downloads & Checksums

| Architecture | APK Package | Size | SHA-256 |
| :--- | :--- | :--- | :--- |$TABLE_ROWS

> **Note:** Most modern Android devices (2018+) require \`arm64-v8a\`. Emulators typically use \`x86_64\`. For other devices or general usage, download \`universal\`.

---
*100% Offline-First | Free & Open Source | AES-256-GCM Vault Encryption | Apache 2.0*
MD

    # Generate update.json
    NOW_UTC="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    JOINED_ARTIFACTS=$(IFS=$',\n'; echo "${ARTIFACT_JSONS[*]}")
    cat > "$GITHUB_OUT/update.json" <<JSON
{
  "versionName": "$NEXT_VER",
  "versionCode": $NEXT_CODE,
  "minSdk": $(read_min_sdk),
  "publishedAt": "$NOW_UTC",
  "artifacts": [
$JOINED_ARTIFACTS
  ]
}
JSON
  fi
fi

echo "=========================================================="
echo "                STAGE COMPLETED SUCCESSFULLY              "
echo "=========================================================="
echo "Output Directory: $OUT_ROOT"
ls -lh "$OUT_ROOT"/*

echo ""
echo "=== REQUIRED GIT COMMIT & RELEASE STEPS ==="
echo "1. Review and commit the version update:"
echo "   git add app/build.gradle.kts playstore_release_notes.txt"
echo "   git commit -m \"chore(release): v$NEXT_VER, versionCode $NEXT_CODE\""
echo ""
echo "2. Create git release tag:"
echo "   git tag v$NEXT_VER"
echo "   git push origin main --tags"
echo ""

if [[ "$UPLOAD_PLAYSTORE" == "true" || -n "$PLAYSTORE_KEY" || -n "${PLAY_STORE_JSON_KEY:-}" ]]; then
  if [[ "$VARIANT" == "play" || "$VARIANT" == "all" ]]; then
    echo "=== Uploading Play Store AAB Bundle ==="
    AAB_FILE="$OUT_ROOT/play/Cardamom-v$NEXT_VER-play.aab"
    NOTES_FILE="$OUT_ROOT/play/playstore_release_notes.txt"
    if command -v python3 >/dev/null 2>&1; then
      python3 "$ROOT_DIR/scripts/upload_playstore.py" --aab "$AAB_FILE" --track "$PLAY_TRACK" --status "$PLAY_STATUS" --notes "$NOTES_FILE" ${PLAYSTORE_KEY:+--key "$PLAYSTORE_KEY"}
    elif command -v pwsh >/dev/null 2>&1; then
      pwsh -File "$ROOT_DIR/scripts/upload_playstore.ps1" -AabPath "$AAB_FILE" -Track "$PLAY_TRACK" -Status "$PLAY_STATUS" -ReleaseNotesPath "$NOTES_FILE" ${PLAYSTORE_KEY:+-ServiceAccountKeyPath "$PLAYSTORE_KEY"}
    else
      echo "::warning ::Neither python3 nor pwsh found for direct Play Store upload. Use GitHub Actions with upload-google-play action or run scripts/upload_playstore.ps1 manually."
    fi
  fi
fi
