<#
.SYNOPSIS
    Uploads a signed Android App Bundle (.aab) to Google Play Console via the Android Publisher API.

.DESCRIPTION
    Uses a Google Cloud Service Account JSON key to generate a signed OAuth2 JWT assertion,
    obtains a bearer token, uploads the AAB, assigns it to the requested track with release notes,
    and commits the edit transaction. Works in standalone PowerShell without Python or Node dependencies.
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory = $true)]
    [string]$AabPath,

    [Parameter()]
    [string]$PackageName = "com.shrivatsav.cardamom",

    [Parameter()]
    [string]$ServiceAccountKeyPath = "",

    [Parameter()]
    [string]$ServiceAccountKeyJson = "",

    [Parameter()]
    [ValidateSet("internal", "alpha", "beta", "production")]
    [string]$Track = "internal",

    [Parameter()]
    [ValidateSet("completed", "draft", "inProgress")]
    [string]$Status = "completed",

    [Parameter()]
    [string]$ReleaseNotesPath = "",

    [Parameter()]
    [string]$ReleaseNotesText = ""
)

$ErrorActionPreference = "Stop"

function To-Base64Url([byte[]]$bytes) {
    return [System.Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

# 1. Resolve Service Account JSON
$jsonText = ""
if ($ServiceAccountKeyJson) {
    $jsonText = $ServiceAccountKeyJson
} elseif ($ServiceAccountKeyPath -and (Test-Path $ServiceAccountKeyPath)) {
    $jsonText = [System.IO.File]::ReadAllText($ServiceAccountKeyPath)
} elseif ($env:PLAY_STORE_JSON_KEY) {
    $jsonText = $env:PLAY_STORE_JSON_KEY
} elseif ($env:GOOGLE_PLAY_KEY_FILE -and (Test-Path $env:GOOGLE_PLAY_KEY_FILE)) {
    $jsonText = [System.IO.File]::ReadAllText($env:GOOGLE_PLAY_KEY_FILE)
} else {
    throw "Google Play Service Account JSON is required. Provide -ServiceAccountKeyPath, -ServiceAccountKeyJson, or set PLAY_STORE_JSON_KEY env var."
}

$sa = $jsonText | ConvertFrom-Json
$clientEmail = $sa.client_email
$privateKeyPem = $sa.private_key

if (-not $clientEmail -or -not $privateKeyPem) {
    throw "Invalid Service Account JSON: client_email or private_key missing."
}

if (-not (Test-Path $AabPath)) {
    throw "AAB bundle not found at path: $AabPath"
}

# 2. Resolve Release Notes
if (-not $ReleaseNotesText -and $ReleaseNotesPath -and (Test-Path $ReleaseNotesPath)) {
    $ReleaseNotesText = [System.IO.File]::ReadAllText($ReleaseNotesPath).Trim()
}
if ($ReleaseNotesText.Length -gt 500) {
    $ReleaseNotesText = $ReleaseNotesText.Substring(0, 500)
}

Write-Host "=== Google Play Publisher Client ===" -ForegroundColor Cyan
Write-Host "Package Name: $PackageName"
Write-Host "Bundle Path:  $AabPath"
Write-Host "Target Track: $Track"
Write-Host "Status:       $Status"
Write-Host "Service Acct: $clientEmail"

# 3. Generate Signed JWT Assertion
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$exp = $now + 3600

$jwtHeader = @{ alg = "RS256"; typ = "JWT" } | ConvertTo-Json -Compress
$jwtClaim = @{
    iss   = $clientEmail
    scope = "https://www.googleapis.com/auth/androidpublisher"
    aud   = "https://oauth2.googleapis.com/token"
    exp   = $exp
    iat   = $now
} | ConvertTo-Json -Compress

$headerB64 = To-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($jwtHeader))
$claimB64 = To-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($jwtClaim))
$payloadToSign = "$headerB64.$claimB64"

# Import RSA Private Key
$keyClean = $privateKeyPem -replace "-----BEGIN PRIVATE KEY-----", "" -replace "-----END PRIVATE KEY-----", "" -replace "\s+", ""
$keyBytes = [System.Convert]::FromBase64String($keyClean)

$rsa = [System.Security.Cryptography.RSA]::Create()
try {
    $rsa.ImportPkcs8PrivateKey($keyBytes, [ref]0)
} catch {
    # Fallback for older .NET
    throw "Failed to import RSA private key: $($_.Exception.Message)"
}

$dataBytes = [System.Text.Encoding]::UTF8.GetBytes($payloadToSign)
$signature = $rsa.SignData($dataBytes, [System.Security.Cryptography.HashAlgorithmName]::SHA256, [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
$sigB64 = To-Base64Url $signature
$assertion = "$payloadToSign.$sigB64"

# 4. Exchange JWT for OAuth2 Access Token
Write-Host "Authenticating with Google OAuth2..." -ForegroundColor Yellow
$tokenBody = @{
    grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    assertion  = $assertion
}
$tokenResponse = Invoke-RestMethod -Uri "https://oauth2.googleapis.com/token" -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
$accessToken = $tokenResponse.access_token
Write-Host "[OK] Authenticated successfully." -ForegroundColor Green

$headers = @{
    "Authorization" = "Bearer $accessToken"
}

# 5. Insert New Edit Session
Write-Host "Creating new Google Play edit transaction for $PackageName..." -ForegroundColor Yellow
$editUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits"
$editRes = Invoke-RestMethod -Uri $editUrl -Method Post -Headers $headers -ContentType "application/json"
$editId = $editRes.id
Write-Host "[OK] Edit transaction active: $editId" -ForegroundColor Green

try {
    # 6. Upload AAB Bundle
    Write-Host "Uploading AAB bundle ($([math]::Round((Get-Item $AabPath).Length / 1MB, 2)) MB)..." -ForegroundColor Yellow
    $uploadUri = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PackageName/edits/$editId/bundles?uploadType=media"
    
    # Read file bytes
    $bundleBytes = [System.IO.File]::ReadAllBytes($AabPath)
    $uploadRes = Invoke-RestMethod -Uri $uploadUri -Method Post -Headers $headers -ContentType "application/octet-stream" -Body $bundleBytes
    $versionCode = $uploadRes.versionCode
    Write-Host "[OK] AAB uploaded. Version Code: $versionCode" -ForegroundColor Green

    # 7. Update Target Track
    Write-Host "Assigning versionCode $versionCode to track '$Track' (status: $Status)..." -ForegroundColor Yellow
    $trackUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$editId/tracks/$Track"
    
    $releaseObj = @{
        versionCodes = @([string]$versionCode)
        status       = $Status
    }
    if ($ReleaseNotesText) {
        $releaseObj["releaseNotes"] = @(
            @{
                language = "en-US"
                text     = $ReleaseNotesText
            }
        )
    }

    $trackPayload = @{
        track    = $Track
        releases = @($releaseObj)
    } | ConvertTo-Json -Depth 5

    $trackRes = Invoke-RestMethod -Uri $trackUrl -Method Put -Headers $headers -ContentType "application/json" -Body $trackPayload
    Write-Host "[OK] Track assigned." -ForegroundColor Green

    # 8. Commit Edit Session
    Write-Host "Committing release edit..." -ForegroundColor Yellow
    $commitUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$editId:commit"
    $commitRes = Invoke-RestMethod -Uri $commitUrl -Method Post -Headers $headers
    Write-Host "[OK] Edit committed successfully! Google Play has received the release." -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed during Play Store publication: $($_.Exception.Message)" -ForegroundColor Red
    # Attempt to delete aborted edit
    try {
        Invoke-RestMethod -Uri "$editUrl/$editId" -Method Delete -Headers $headers | Out-Null
    } catch {}
    throw $_
}
