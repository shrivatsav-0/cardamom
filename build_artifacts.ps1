<#
.SYNOPSIS
    Build and packaging script for Cardamom Android artifacts (PowerShell).

.DESCRIPTION
    Runs root tests, manages semantic versioning and versionCode updates in app/build.gradle.kts,
    generates and validates Play Store & GitHub release notes, builds two distribution variants
    (Play Store single APK + AAB, and GitHub architecture split APKs), computes SHA-256 checksums,
    and stages artifacts in dist/.

.PARAMETER BuildType
    Target build type: 'release' (default) or 'debug'.
    Release builds increment versionCode and require signing.

.PARAMETER Variant
    Target distribution variant: 'play', 'github', or 'all' (default).
    Aliased as 'Platform'.
    - play:   Google Play Store (signed AAB + release APK)
    - github: Architecture split APKs for GitHub Releases (arm64-v8a, armeabi-v7a, x86_64)
    - all:    Both variants in a single build cycle

.PARAMETER Bump
    Version component to increment: 'patch' (default for release), 'minor', 'major', or 'none'.
    patch: 1.0.0 -> 1.0.1
    minor: 1.0.0 -> 1.1.0
    major: 1.0.0 -> 2.0.0
    none:  keeps versionName unchanged

.PARAMETER SkipTests
    Skip running root project unit tests before building.

.PARAMETER GenerateNotes
    Generate or re-generate default playstore_release_notes.txt template.

.PARAMETER PrintNotesTemplate
    Print Play Store release notes formatting rules and exit.

.PARAMETER Publish
    Optionally tag and publish artifacts via GitHub CLI ('gh release create').
#>

[CmdletBinding()]
param (
    [Parameter(Position = 0)]
    [ValidateSet("release", "debug")]
    [string]$BuildType = "release",

    [Parameter()]
    [Alias("Platform")]
    [ValidateSet("play", "github", "all")]
    [string]$Variant = "all",

    [Parameter()]
    [ValidateSet("none", "patch", "minor", "major")]
    [string]$Bump = "patch",

    [Parameter()]
    [switch]$SkipTests,

    [Parameter()]
    [switch]$GenerateNotes,

    [Parameter()]
    [switch]$PrintNotesTemplate,

    [Parameter()]
    [switch]$Publish,

    [Parameter()]
    [switch]$UploadPlayStore,

    [Parameter()]
    [string]$PlayStoreKey = "",

    [Parameter()]
    [ValidateSet("internal", "alpha", "beta", "production")]
    [string]$PlayTrack = "internal",

    [Parameter()]
    [ValidateSet("completed", "draft", "inProgress")]
    [string]$PlayStatus = "completed"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# --- Configuration & Paths ---------------------------------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = Get-Location }
$RootDir = $ScriptDir
$GradleProps = Join-Path $RootDir "app\build.gradle.kts"
$NotesPath = Join-Path $RootDir "playstore_release_notes.txt"
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$Gradlew = Join-Path $RootDir "gradlew.bat"
if (-not (Test-Path $Gradlew)) {
    $Gradlew = Join-Path $RootDir "gradlew"
}

$PlayStoreMaxChars = 500

function Show-NotesTemplate {
@"
=== PLAY STORE RELEASE NOTES TEMPLATE ===
Limit: 500 characters total (Google Play rejects at upload time).

  <Feature summary>            <- line 1, plain text, NO bullet, <= 70 chars
                               <- line 2, blank
- <one user-facing change>     <- 3 to 5 bullets, each prefixed "- "
- <one user-facing change>
- <one user-facing change>

Rules:
  - Plain text only. No markdown headers, bold or italics.
  - Write for end users, not developers: no internal class or API names.
  - One change per bullet, past tense, <= 100 characters per bullet.
  - Lead with the most visible change.
  - 500 characters is the hard cap.
=== END TEMPLATE ===
"@
}

if ($PrintNotesTemplate) {
    Show-NotesTemplate
    exit 0
}

# --- Helper Functions --------------------------------------------------------

function Get-GradleVersionName {
    $content = Get-Content -Path $GradleProps -Raw
    if ($content -match 'versionName\s*=\s*"([^"]+)"') {
        return $matches[1].Trim()
    }
    throw "Could not determine versionName from $GradleProps"
}

function Get-GradleVersionCode {
    $content = Get-Content -Path $GradleProps -Raw
    if ($content -match 'versionCode\s*=\s*(\d+)') {
        return [int]$matches[1]
    }
    throw "Could not determine versionCode from $GradleProps"
}

function Get-GradleMinSdk {
    $content = Get-Content -Path $GradleProps -Raw
    if ($content -match 'minSdk\s*=\s*(\d+)') {
        return [int]$matches[1]
    }
    return 30
}

function Compute-NextVersion([string]$current, [string]$bumpType) {
    $parts = $current.Split('.')
    $major = 1
    $minor = 0
    $patch = 0

    if ($parts.Length -ge 1) { [int]::TryParse($parts[0], [ref]$major) | Out-Null }
    if ($parts.Length -ge 2) { [int]::TryParse($parts[1], [ref]$minor) | Out-Null }
    if ($parts.Length -ge 3) { [int]::TryParse($parts[2], [ref]$patch) | Out-Null }

    switch ($bumpType) {
        "patch" { return "$major.$minor.$($patch + 1)" }
        "minor" { return "$major.$($minor + 1).0" }
        "major" { return "$($major + 1).0.0" }
        "none"  { return "$major.$minor.$patch" }
        default { return "$major.$minor.$patch" }
    }
}

function Validate-ReleaseNotes([string]$path, [int]$limit) {
    if (-not (Test-Path $path)) {
        return @{ Valid = $false; Errors = @("Release notes file not found: $path"); Warnings = @(); Chars = 0 }
    }

    $rawText = [System.IO.File]::ReadAllText($path, $Utf8NoBom)
    $normalized = $rawText -replace "\r\n", "`n"
    $trimmed = $normalized.TrimEnd("`r", "`n")
    $charCount = $trimmed.Length

    $errors = [System.Collections.Generic.List[string]]::new()
    $warnings = [System.Collections.Generic.List[string]]::new()

    if ($charCount -gt $limit) {
        $errors.Add("$charCount characters exceeds the Google Play limit of $limit")
    }

    $lines = $trimmed -split "`n"
    $nonBlankLines = @($lines | Where-Object { $_.Trim().Length -gt 0 })

    if ($nonBlankLines.Count -eq 0) {
        $errors.Add("Release notes file is empty")
    } else {
        $title = $nonBlankLines[0].Trim()
        if ($title -match '^[-*#]') {
            $errors.Add("Line 1 must be a plain summary line, not a bullet or heading")
        }
        if ($title.Length -gt 70) {
            $warnings.Add("Summary line is $($title.Length) characters; recommended budget is <= 70")
        }

        if ($lines.Length -gt 1) {
            if ($lines[1].Trim().Length -gt 0) {
                $errors.Add("Line 2 must be blank, separating summary from bullets")
            }
        } else {
            $errors.Add("Line 2 must be blank, separating summary from bullets")
        }

        $bullets = @($nonBlankLines[1..($nonBlankLines.Count - 1)])
        foreach ($b in $bullets) {
            if (-not ($b -match '^\s*-\s+')) {
                $errors.Add("Every bullet must start with '- ' (offending: '$b')")
                break
            }
            if ($b.Length -gt 102) {
                $warnings.Add("Bullet is $($b.Length) characters; keep bullets under 100")
            }
        }

        if ($bullets.Count -lt 3 -or $bullets.Count -gt 6) {
            $warnings.Add("$($bullets.Count) bullets found; template recommends 3 to 5")
        }
    }

    return @{
        Valid = ($errors.Count -eq 0)
        Errors = $errors
        Warnings = $warnings
        Chars = $charCount
        Lines = $lines
    }
}

function Generate-DefaultReleaseNotes([string]$path, [string]$version) {
    $notes = @"
Cardamom v${version}: Private offline wallet with 3D cards and fast scanner

- Added 3D flippable cards with front and back interactive perspectives
- Added on-device OCR and instant universal barcode scanner
- Added multi-page document camera scanner with perspective correction
- Reduced app download size with optimized architecture split packages
- Added AES-256-GCM encrypted self-custody vault backups
"@
    [System.IO.File]::WriteAllText($path, $notes, $Utf8NoBom)
    Write-Host "Generated release notes template at: $path" -ForegroundColor Cyan
}

function Get-Sha256Hex([string]$filePath) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $stream = [System.IO.File]::OpenRead($filePath)
    try {
        $bytes = $sha256.ComputeHash($stream)
        return -join ($bytes | ForEach-Object { $_.ToString("x2") })
    } finally {
        $stream.Close()
        $sha256.Dispose()
    }
}

# --- Pre-flight Checks & Testing ---------------------------------------------
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "           CARDAMOM BUILD & RELEASE PIPELINE             " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Target Build Type: $BuildType"
Write-Host "Target Variant:    $Variant"
Write-Host "Version Bump:      $Bump"
Write-Host ""

if ($GenerateNotes -or (-not (Test-Path $NotesPath))) {
    $currVer = Get-GradleVersionName
    $nextVer = if ($Bump -ne "none") { Compute-NextVersion $currVer $Bump } else { $currVer }
    Generate-DefaultReleaseNotes $NotesPath $nextVer
}

# Test project root folder
if (-not $SkipTests) {
    Write-Host "=== Testing Project Root Folder (app:test) ===" -ForegroundColor Yellow
    $testProc = Start-Process -FilePath $Gradlew -ArgumentList "test" -WorkingDirectory $RootDir -NoNewWindow -PassThru -Wait
    if ($testProc.ExitCode -ne 0) {
        Write-Error "Project root tests failed with exit code $($testProc.ExitCode)! Aborting build."
        exit 1
    }
    Write-Host "[OK] All unit tests passed cleanly." -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "Notice: -SkipTests specified; skipping unit test verification." -ForegroundColor DarkYellow
}

# --- Version Bumping ---------------------------------------------------------
$CurrentVersion = Get-GradleVersionName
$CurrentCode = Get-GradleVersionCode
$MinSdk = Get-GradleMinSdk

if ($Bump -eq "none") {
    Write-Host "Notice: Current version (v$CurrentVersion, code $CurrentCode) is already latest; skipping version update." -ForegroundColor Cyan
    $NextVersion = $CurrentVersion
    $NextCode = $CurrentCode
} else {
    $NextVersion = Compute-NextVersion $CurrentVersion $Bump
    $NextCode = if ($BuildType -eq "release") { $CurrentCode + 1 } else { $CurrentCode }

    Write-Host "=== Version Configuration ===" -ForegroundColor Cyan
    Write-Host "versionName: $CurrentVersion -> $NextVersion"
    if ($BuildType -eq "release") {
        Write-Host "versionCode: $CurrentCode -> $NextCode"
    } else {
        Write-Host "versionCode: $CurrentCode (unchanged for debug builds)"
    }

    # Update app/build.gradle.kts
    $gradlePropsContent = [System.IO.File]::ReadAllText($GradleProps, $Utf8NoBom)
    $updatedProps = $gradlePropsContent -replace 'versionCode\s*=\s*\d+', "versionCode = $NextCode"
    $updatedProps = $updatedProps -replace 'versionName\s*=\s*"[^"]+"', "versionName = ""$NextVersion"""
    [System.IO.File]::WriteAllText($GradleProps, $updatedProps, $Utf8NoBom)
}
# Validate Release Notes for Play Store
Write-Host ""
Write-Host "=== Validating Play Store Release Notes ===" -ForegroundColor Cyan
$valResult = Validate-ReleaseNotes $NotesPath $PlayStoreMaxChars
Write-Host "Notes character count: $($valResult.Chars)/$PlayStoreMaxChars"

if ($valResult.Warnings.Count -gt 0) {
    foreach ($w in $valResult.Warnings) {
        Write-Host "  [WARN] $w" -ForegroundColor Yellow
    }
}

if (-not $valResult.Valid) {
    foreach ($e in $valResult.Errors) {
        Write-Host "  [ERROR] $e" -ForegroundColor Red
    }
    Write-Host ""
    Show-NotesTemplate
    Write-Error "Release notes failed validation. Please fix $NotesPath before release."
    exit 1
}
Write-Host "  [OK] Release notes follow Play Store guidelines." -ForegroundColor Green
Write-Host ""

# --- Gradle Build Execution --------------------------------------------------
$BuildCap = if ($BuildType -eq "release") { "Release" } else { "Debug" }

function Invoke-GradleStep([string[]]$tasks, [string[]]$extraArgs, [string]$stepName) {
    Write-Host "=== Building $stepName ===" -ForegroundColor Yellow
    $allArgs = $tasks + $extraArgs
    Write-Host "Executing: gradlew $($allArgs -join ' ')"
    $proc = Start-Process -FilePath $Gradlew -ArgumentList $allArgs -WorkingDirectory $RootDir -NoNewWindow -PassThru -Wait
    if ($proc.ExitCode -ne 0) {
        Write-Error "$stepName failed with exit code $($proc.ExitCode)!"
        exit 1
    }
    Write-Host "[OK] $stepName completed successfully." -ForegroundColor Green
    Write-Host ""
}

if ($Variant -eq "play" -or $Variant -eq "all") {
    $playTasks = if ($BuildType -eq "release") { @("assemblePlayRelease", "bundlePlayRelease") } else { @("assemblePlayDebug") }
    Invoke-GradleStep $playTasks @("-PsplitApks=false", "-x", "lintVitalAnalyzePlayRelease", "-x", "lintVitalReportPlayRelease", "-x", "lintVitalPlayRelease") "Play Store Variant"
}

if ($Variant -eq "github" -or $Variant -eq "all") {
    $githubTasks = if ($BuildType -eq "release") { @("assembleGithubRelease") } else { @("assembleGithubDebug") }
    Invoke-GradleStep $githubTasks @("-PsplitApks=true", "-x", "lintVitalAnalyzeGithubRelease", "-x", "lintVitalReportGithubRelease", "-x", "lintVitalGithubRelease") "GitHub Split APK Variant"
}
# --- Artifact Assembly & Staging ---------------------------------------------
$OutRoot = Join-Path $RootDir "dist\$BuildCap\v$NextVersion"
if (-not (Test-Path $OutRoot)) {
    New-Item -ItemType Directory -Path $OutRoot -Force | Out-Null
}

$ArtifactRecords = [System.Collections.Generic.List[PSCustomObject]]::new()

# Variant 1: Play Store Artifacts
if ($Variant -eq "play" -or $Variant -eq "all") {
    $PlayOutDir = Join-Path $OutRoot "play"
    if (-not (Test-Path $PlayOutDir)) { New-Item -ItemType Directory -Path $PlayOutDir -Force | Out-Null }

    if ($BuildType -eq "release") {
        $SrcPlayApk = Join-Path $RootDir "app\build\outputs\apk\play\release\app-play-release.apk"
        $SrcPlayAab = Join-Path $RootDir "app\build\outputs\bundle\playRelease\app-play-release.aab"

        $DstPlayApk = Join-Path $PlayOutDir "Cardamom-v$NextVersion-play.apk"
        $DstPlayAab = Join-Path $PlayOutDir "Cardamom-v$NextVersion-play.aab"

        Copy-Item -Path $SrcPlayApk -Destination $DstPlayApk -Force
        Copy-Item -Path $SrcPlayAab -Destination $DstPlayAab -Force

        $shaApk = Get-Sha256Hex $DstPlayApk
        $shaAab = Get-Sha256Hex $DstPlayAab

        [System.IO.File]::WriteAllText("$DstPlayApk.sha256", "$shaApk  Cardamom-v$NextVersion-play.apk`n", $Utf8NoBom)
        [System.IO.File]::WriteAllText("$DstPlayAab.sha256", "$shaAab  Cardamom-v$NextVersion-play.aab`n", $Utf8NoBom)

        Copy-Item -Path $NotesPath -Destination (Join-Path $PlayOutDir "playstore_release_notes.txt") -Force

        $ArtifactRecords.Add([PSCustomObject]@{ Variant = "Play"; Name = "Cardamom-v$NextVersion-play.apk"; Type = "Release APK"; SHA256 = $shaApk; SizeMB = [math]::Round((Get-Item $DstPlayApk).Length / 1MB, 2) })
        $ArtifactRecords.Add([PSCustomObject]@{ Variant = "Play"; Name = "Cardamom-v$NextVersion-play.aab"; Type = "Play Bundle (AAB)"; SHA256 = $shaAab; SizeMB = [math]::Round((Get-Item $DstPlayAab).Length / 1MB, 2) })
    } else {
        $SrcPlayApk = Join-Path $RootDir "app\build\outputs\apk\play\debug\app-play-debug.apk"
        $DstPlayApk = Join-Path $PlayOutDir "Cardamom-v$NextVersion-debug-play.apk"
        Copy-Item -Path $SrcPlayApk -Destination $DstPlayApk -Force
        $shaApk = Get-Sha256Hex $DstPlayApk
        $ArtifactRecords.Add([PSCustomObject]@{ Variant = "Play"; Name = "Cardamom-v$NextVersion-debug-play.apk"; Type = "Debug APK"; SHA256 = $shaApk; SizeMB = [math]::Round((Get-Item $DstPlayApk).Length / 1MB, 2) })
    }
}

# Variant 2: GitHub Split APK Artifacts
if ($Variant -eq "github" -or $Variant -eq "all") {
    $GithubOutDir = Join-Path $OutRoot "github"
    if (-not (Test-Path $GithubOutDir)) { New-Item -ItemType Directory -Path $GithubOutDir -Force | Out-Null }

    if ($BuildType -eq "release") {
        $abis = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")
        $checksumLines = [System.Collections.Generic.List[string]]::new()
        $githubArtifactsList = [System.Collections.Generic.List[hashtable]]::new()

        foreach ($abi in $abis) {
            $srcSplit = Join-Path $RootDir "app\build\outputs\apk\github\release\app-github-$abi-release.apk"
            $dstSplitName = "Cardamom-v$NextVersion-$abi.apk"
            $dstSplit = Join-Path $GithubOutDir $dstSplitName

            if (Test-Path $srcSplit) {
                Copy-Item -Path $srcSplit -Destination $dstSplit -Force
                $sha = Get-Sha256Hex $dstSplit
                [System.IO.File]::WriteAllText("$dstSplit.sha256", "$sha  $dstSplitName`n", $Utf8NoBom)
                $checksumLines.Add("$sha  $dstSplitName")

                $githubArtifactsList.Add(@{
                    abi = $abi
                    file = $dstSplitName
                    sha256 = $sha
                    size = (Get-Item $dstSplit).Length
                })

                $typeLabel = if ($abi -eq "universal") { "Universal APK" } else { "Split APK ($abi)" }
                $ArtifactRecords.Add([PSCustomObject]@{ Variant = "GitHub"; Name = $dstSplitName; Type = $typeLabel; SHA256 = $sha; SizeMB = [math]::Round((Get-Item $dstSplit).Length / 1MB, 2) })
            }
        }

        # SHA256SUMS.txt for GitHub
        [System.IO.File]::WriteAllLines((Join-Path $GithubOutDir "SHA256SUMS.txt"), $checksumLines, $Utf8NoBom)

        # Generate github_release_notes.md
        $notesRaw = if (Test-Path $NotesPath) { [System.IO.File]::ReadAllText($NotesPath, $Utf8NoBom) } else { "" }
        $bulletLines = @($notesRaw -split "`r?`n" | Where-Object { $_ -match '^\s*-\s+' })
        $summaryLine = @($notesRaw -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 -and $_ -notmatch '^[-*#]' })[0]

        $mdBuilder = [System.Text.StringBuilder]::new()
        [void]$mdBuilder.AppendLine("## Cardamom v$NextVersion")
        [void]$mdBuilder.AppendLine("")
        if ($summaryLine) {
            [void]$mdBuilder.AppendLine("**$summaryLine**")
            [void]$mdBuilder.AppendLine("")
        }
        if ($bulletLines.Count -gt 0) {
            foreach ($b in $bulletLines) {
                [void]$mdBuilder.AppendLine($b)
            }
            [void]$mdBuilder.AppendLine("")
        }
        [void]$mdBuilder.AppendLine("### Downloads & Checksums")
        [void]$mdBuilder.AppendLine("")
        [void]$mdBuilder.AppendLine("| Architecture | APK Package | Size | SHA-256 |")
        [void]$mdBuilder.AppendLine("| :--- | :--- | :--- | :--- |")
        foreach ($item in $githubArtifactsList) {
            $mb = [math]::Round($item.size / 1MB, 1)
            $abi = $item.abi
            $file = $item.file
            $sha = $item.sha256
            [void]$mdBuilder.AppendLine("| ``$abi`` | $file | ${mb} MB | ``$sha`` |")
        }
        [void]$mdBuilder.AppendLine("")
        [void]$mdBuilder.AppendLine("> **Note:** Most modern Android devices (2018+) require ``arm64-v8a``. Emulators typically use ``x86_64``.")

        [System.IO.File]::WriteAllText((Join-Path $GithubOutDir "github_release_notes.md"), $mdBuilder.ToString(), $Utf8NoBom)

        # Generate update.json for GitHub / direct updates
        $nowUtc = [System.DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
        $updatePayload = [ordered]@{
            versionName = $NextVersion
            versionCode = $NextCode
            minSdk      = $MinSdk
            publishedAt = $nowUtc
            artifacts   = $githubArtifactsList
            notes       = $notesRaw.Trim()
        }
        $updateJson = ConvertTo-Json -InputObject $updatePayload -Depth 4
        [System.IO.File]::WriteAllText((Join-Path $GithubOutDir "update.json"), $updateJson, $Utf8NoBom)
    } else {
        $srcSplit = Join-Path $RootDir "app\build\outputs\apk\github\debug\app-github-debug.apk"
        $dstSplit = Join-Path $GithubOutDir "Cardamom-v$NextVersion-debug-github.apk"
        if (Test-Path $srcSplit) {
            Copy-Item -Path $srcSplit -Destination $dstSplit -Force
            $sha = Get-Sha256Hex $dstSplit
            $ArtifactRecords.Add([PSCustomObject]@{ Variant = "GitHub"; Name = "Cardamom-v$NextVersion-debug-github.apk"; Type = "Debug APK"; SHA256 = $sha; SizeMB = [math]::Round((Get-Item $dstSplit).Length / 1MB, 2) })
        }
    }
}

# --- Results & Release Instructions ------------------------------------------
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "                STAGE COMPLETED SUCCESSFULLY              " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Output Directory: $OutRoot"
Write-Host ""

$ArtifactRecords | Format-Table -AutoSize -Property Variant, Name, Type, SizeMB, SHA256

Write-Host "=== REQUIRED GIT COMMIT & RELEASE STEPS ===" -ForegroundColor Cyan
Write-Host "1. Review and commit the version update:"
Write-Host "   git add app/build.gradle.kts playstore_release_notes.txt" -ForegroundColor White
Write-Host "   git commit -m `"chore(release): v$NextVersion, versionCode $NextCode`"" -ForegroundColor White
Write-Host ""
Write-Host "2. Create git release tag:"
Write-Host "   git tag v$NextVersion" -ForegroundColor White
Write-Host "   git push origin main --tags" -ForegroundColor White
Write-Host ""

if ($Publish) {
    if ($Variant -eq "github" -or $Variant -eq "all") {
        Write-Host "=== Publishing to GitHub via gh CLI ===" -ForegroundColor Yellow
        $ghNotes = Join-Path $OutRoot "github\github_release_notes.md"
        $filesToUpload = Get-ChildItem -Path (Join-Path $OutRoot "github") -Filter "*.apk" | ForEach-Object { $_.FullName }
        $filesToUpload += (Join-Path $OutRoot "github\SHA256SUMS.txt")
        $filesToUpload += (Join-Path $OutRoot "github\update.json")

        $ghArgs = @("release", "create", "v$NextVersion") + $filesToUpload + @("--title", "Cardamom v$NextVersion", "--notes-file", $ghNotes)
        Write-Host "Executing: gh $($ghArgs -join ' ')"
        & gh @ghArgs
    }
}

if ($UploadPlayStore -or $PlayStoreKey) {
    if ($Variant -eq "play" -or $Variant -eq "all") {
        Write-Host ""
        Write-Host "=== Uploading Play Store AAB Bundle ===" -ForegroundColor Yellow
        $aabFile = Join-Path $OutRoot "play\Cardamom-v$NextVersion-play.aab"
        $notesFile = Join-Path $OutRoot "play\playstore_release_notes.txt"
        $uploadScript = Join-Path $RootDir "scripts\upload_playstore.ps1"
        $uploadParams = @{
            AabPath = $aabFile
            Track = $PlayTrack
            Status = $PlayStatus
            ReleaseNotesPath = $notesFile
        }
        if ($PlayStoreKey) {
            $uploadParams["ServiceAccountKeyPath"] = $PlayStoreKey
        }
        & $uploadScript @uploadParams
    }
}
