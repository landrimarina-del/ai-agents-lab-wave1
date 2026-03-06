$ErrorActionPreference = 'Stop'

param(
    [string]$DbContainer = 'rise-db',
    [string]$DbUser = 'rise',
    [string]$BackendUrl = 'http://localhost:8080'
)

$healthUrl = "$BackendUrl/api/health"
$versionUrl = "$BackendUrl/api/version"
$pass = 0
$fail = 0

function Pass([string]$msg) {
    Write-Host "[PASS] $msg"
    $script:pass++
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg"
    $script:fail++
}

Write-Host "=== Sprint 0 QA Smoke Test (PowerShell) ==="
Write-Host "DB Container: $DbContainer"
Write-Host "Backend: $BackendUrl"
Write-Host "Health: $healthUrl"
Write-Host "Version: $versionUrl"
Write-Host ""

try {
    docker exec $DbContainer pg_isready -U $DbUser | Out-Null
    if ($LASTEXITCODE -eq 0) { Pass "DB reachable via pg_isready" } else { Fail "DB not reachable" }
} catch {
    Fail "DB check error: $($_.Exception.Message)"
}

try {
    $h = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 10
    if ($h.status -eq 'UP' -and $h.database -eq 'UP') {
        Pass "/api/health -> status=UP, database=UP"
    } else {
        Fail "/api/health unexpected payload: status=$($h.status), database=$($h.database)"
    }
} catch {
    Fail "/api/health call failed: $($_.Exception.Message)"
}

try {
    $v = Invoke-RestMethod -Uri $versionUrl -Method Get -TimeoutSec 10
    if ($null -ne $v.application -and $null -ne $v.version -and "$($v.application)" -ne '' -and "$($v.version)" -ne '') {
        Pass "/api/version -> keys application/version present"
    } else {
        Fail "/api/version missing keys application/version"
    }
} catch {
    Fail "/api/version call failed: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "=== Summary ==="
Write-Host "PASS: $pass"
Write-Host "FAIL: $fail"

if ($fail -gt 0) {
    exit 1
}

exit 0
