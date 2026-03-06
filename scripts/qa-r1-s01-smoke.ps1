Param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@rise.local",
    [PSCredential]$AdminCredential,
    [SecureString]$AdminPassword
)

$ErrorActionPreference = "Stop"
$allPassed = $true

function Write-Pass($message) {
    Write-Host "[PASS] $message" -ForegroundColor Green
}

function Write-Fail($message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
}

function Assert-True($condition, $successMessage, $failMessage) {
    if ($condition) {
        Write-Pass $successMessage
        return $true
    }

    Write-Fail $failMessage
    return $false
}

try {
    $healthUrl = "$BaseUrl/api/health"
    $healthResponse = Invoke-RestMethod -Method Get -Uri $healthUrl

    $healthUp = ($null -ne $healthResponse.status -and $healthResponse.status -match "(?i)^up$") -and
                ($null -ne $healthResponse.database -and $healthResponse.database -match "(?i)^up$")

    if (-not (Assert-True $healthUp "/api/health is UP (status=UP, database=UP)" "/api/health is not UP (status/database expected UP)")) {
        $allPassed = $false
    }
}
catch {
    Write-Fail "/api/health request failed: $($_.Exception.Message)"
    $allPassed = $false
}

try {
    $versionUrl = "$BaseUrl/api/version"
    $versionResponse = Invoke-RestMethod -Method Get -Uri $versionUrl

    $hasApplication = $null -ne $versionResponse.application -and -not [string]::IsNullOrWhiteSpace("$($versionResponse.application)")
    $hasVersion = $null -ne $versionResponse.version -and -not [string]::IsNullOrWhiteSpace("$($versionResponse.version)")

    if (-not (Assert-True ($hasApplication -and $hasVersion) "/api/version contains keys: application, version" "/api/version missing one or more keys: application, version")) {
        $allPassed = $false
    }
}
catch {
    Write-Fail "/api/version request failed: $($_.Exception.Message)"
    $allPassed = $false
}

try {
    $loginUrl = "$BaseUrl/api/auth/login"

    $resolvedPassword = $null
    if ($null -ne $AdminCredential) {
        $AdminEmail = $AdminCredential.UserName
        $resolvedPassword = [System.Net.NetworkCredential]::new("", $AdminCredential.Password).Password
    } elseif ($null -ne $AdminPassword) {
        $resolvedPassword = [System.Net.NetworkCredential]::new("", $AdminPassword).Password
    } elseif (-not [string]::IsNullOrWhiteSpace($env:ADMIN_PASSWORD)) {
        $resolvedPassword = $env:ADMIN_PASSWORD
    } else {
        throw "Admin password non fornita. Usa -AdminCredential, -AdminPassword (SecureString) oppure variabile ambiente ADMIN_PASSWORD."
    }

    $loginBody = @{
        email = $AdminEmail
        password = $resolvedPassword
    } | ConvertTo-Json

    $loginResponse = Invoke-RestMethod -Method Post -Uri $loginUrl -ContentType "application/json" -Body $loginBody

    $token = $null
    if ($null -ne $loginResponse.token) { $token = $loginResponse.token }
    elseif ($null -ne $loginResponse.accessToken) { $token = $loginResponse.accessToken }
    elseif ($null -ne $loginResponse.data.token) { $token = $loginResponse.data.token }

    if (-not (Assert-True (-not [string]::IsNullOrWhiteSpace($token)) "Admin login returns token" "Admin login did not return a token")) {
        $allPassed = $false
    }
}
catch {
    Write-Fail "Admin login request failed: $($_.Exception.Message)"
    $allPassed = $false
}

if ($allPassed) {
    Write-Host "Smoke checks completed successfully." -ForegroundColor Green
    exit 0
}

Write-Host "Smoke checks failed." -ForegroundColor Red
exit 1
