Param(
    [string]$BaseUrl = "http://localhost:8080",
    [Alias("AdminUser")]
    [string]$AdminEmail = "admin@rise.local",
    [SecureString]$AdminPassword,
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute("PSAvoidUsingPlainTextForPassword", "")]
    [string]$AdminPasswordPlain,
    [int]$BusinessUnitId = 1
)

$ErrorActionPreference = "Stop"
$allPassed = $true
$token = $null
$shopId = $null
$employeeId = $null
$countryScopeId = $null
$effectiveBusinessUnitId = $null

function Write-Pass($message) { Write-Host "[PASS] $message" -ForegroundColor Green }
function Write-Fail($message) { Write-Host "[FAIL] $message" -ForegroundColor Red }

function Get-ServerMessage($err) {
    if ($null -ne $err.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($err.ErrorDetails.Message)) {
        try {
            $payload = $err.ErrorDetails.Message | ConvertFrom-Json
            if ($null -ne $payload.message) { return "$($payload.message)" }
        } catch {
            return $err.ErrorDetails.Message
        }
        return $err.ErrorDetails.Message
    }
    return $err.Exception.Message
}

try {
    $health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/health"
    if ($health.status -match "(?i)^up$") { Write-Pass "GET /api/health" } else { Write-Fail "GET /api/health not UP"; $allPassed = $false }
} catch {
    Write-Fail "GET /api/health failed: $(Get-ServerMessage $_)"
    $allPassed = $false
}

try {
    $resolvedPassword = $null
    if ($null -ne $AdminPassword) {
        $resolvedPassword = [System.Net.NetworkCredential]::new("", $AdminPassword).Password
    } elseif (-not [string]::IsNullOrWhiteSpace($AdminPasswordPlain)) {
        $resolvedPassword = $AdminPasswordPlain
    } elseif (-not [string]::IsNullOrWhiteSpace($env:ADMIN_PASSWORD)) {
        $resolvedPassword = $env:ADMIN_PASSWORD
    } else {
        throw "Admin password non fornita. Usa -AdminPassword (SecureString), -AdminPasswordPlain o variabile ambiente ADMIN_PASSWORD."
    }

    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body (@{ email = $AdminEmail; password = $resolvedPassword } | ConvertTo-Json)
    if ($null -ne $login.token) { $token = "$($login.token)" }
    elseif ($null -ne $login.accessToken) { $token = "$($login.accessToken)" }
    elseif ($null -ne $login.data -and $null -ne $login.data.token) { $token = "$($login.data.token)" }

    if ([string]::IsNullOrWhiteSpace($token)) { throw "Token assente" }
    Write-Pass "POST /api/auth/login"
} catch {
    Write-Fail "POST /api/auth/login failed: $(Get-ServerMessage $_)"
    $allPassed = $false
}

if (-not [string]::IsNullOrWhiteSpace($token)) {
    $headers = @{ Authorization = "Bearer $token" }
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 6).ToUpperInvariant()

    try {
        $businessUnits = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/business-units" -Headers $headers
        $businessUnitItems = @()
        if ($businessUnits -is [System.Array]) {
            $businessUnitItems = $businessUnits
        } elseif ($null -ne $businessUnits) {
            $businessUnitItems = @($businessUnits)
        }

        $selected = $businessUnitItems | Where-Object { $_.id -eq $BusinessUnitId } | Select-Object -First 1
        if ($null -ne $selected) {
            $effectiveBusinessUnitId = [int]$selected.id
            Write-Pass "GET /api/business-units (uso BusinessUnitId=$effectiveBusinessUnitId)"
        } elseif ($businessUnitItems.Count -gt 0) {
            $effectiveBusinessUnitId = [int]$businessUnitItems[0].id
            Write-Pass "GET /api/business-units (BusinessUnitId $BusinessUnitId non trovato, uso fallback=$effectiveBusinessUnitId)"
        } else {
            $newBuPayload = @{
                code = "QABU$suffix"
                name = "QA BU $suffix"
                countryCode = "IT"
                region = "IT"
                isActive = $true
            } | ConvertTo-Json

            $newBu = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/business-units" -Headers $headers -ContentType "application/json" -Body $newBuPayload
            if ($null -eq $newBu -or $null -eq $newBu.id) { throw "Business unit create senza id" }
            $effectiveBusinessUnitId = [int]$newBu.id
            Write-Pass "POST /api/business-units (created id=$effectiveBusinessUnitId)"
        }
    } catch {
        Write-Fail "Business unit setup failed: $(Get-ServerMessage $_)"
        $allPassed = $false
    }

    try {
        $countryPayload = @{
            code = "Q$suffix".Substring(0, 2)
            name = "QA Country Scope $suffix"
        } | ConvertTo-Json

        $countryCreated = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/country-scopes" -Headers $headers -ContentType "application/json" -Body $countryPayload
        $countryScopeId = $countryCreated.id
        if ($null -eq $countryScopeId) { throw "countryScopeId assente" }
        Write-Pass "POST /api/country-scopes"
    } catch {
        Write-Fail "POST /api/country-scopes failed: $(Get-ServerMessage $_)"
        $allPassed = $false
    }

    if ($null -ne $countryScopeId) {
        try {
            $countryUpdatePayload = @{
                code = "Q$suffix".Substring(0, 2)
                name = "QA Country Scope Updated $suffix"
                isActive = $true
            } | ConvertTo-Json

            Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/country-scopes/$countryScopeId" -Headers $headers -ContentType "application/json" -Body $countryUpdatePayload | Out-Null
            Write-Pass "PUT /api/country-scopes/{id}"
        } catch {
            Write-Fail "PUT /api/country-scopes/{id} failed: $(Get-ServerMessage $_)"
            $allPassed = $false
        }
    }

    try {
        $countryList = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/country-scopes?includeInactive=true" -Headers $headers
        if ($null -eq $countryList) { throw "Lista country scopes vuota" }
        Write-Pass "GET /api/country-scopes"
    } catch {
        Write-Fail "GET /api/country-scopes failed: $(Get-ServerMessage $_)"
        $allPassed = $false
    }

    try {
        if ($null -eq $effectiveBusinessUnitId) {
            throw "BusinessUnitId non risolta"
        }

        $shopPayload = @{
            shopCode = "QASHOP$suffix"
            name = "QA Shop $suffix"
            countryCode = "IT"
            region = "IT"
            businessUnitId = $effectiveBusinessUnitId
        } | ConvertTo-Json

        $shop = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/shops" -Headers $headers -ContentType "application/json" -Body $shopPayload
        $shopId = $shop.id
        if ($null -eq $shopId) { throw "shopId assente" }
        Write-Pass "POST /api/shops"
    } catch {
        Write-Fail "POST /api/shops failed: $(Get-ServerMessage $_)"
        $allPassed = $false
    }

    if ($null -ne $shopId) {
        try {
            $employeePayload = @{
                employeeId = "QAEMP$suffix"
                fullName = "QA Employee $suffix"
                email = "qa.employee.$suffix@rise.local"
                shopId = [int]$shopId
            } | ConvertTo-Json

            $employee = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/employees" -Headers $headers -ContentType "application/json" -Body $employeePayload
            $employeeId = $employee.id
            if ($null -eq $employeeId) { throw "employeeId assente" }
            Write-Pass "POST /api/employees"
        } catch {
            Write-Fail "POST /api/employees failed: $(Get-ServerMessage $_)"
            $allPassed = $false
        }
    }

    if ($null -ne $employeeId) {
        try {
            Invoke-WebRequest -Method Patch -Uri "$BaseUrl/api/employees/$employeeId/deactivate" -Headers $headers -ContentType "application/json" -Body "{}" -UseBasicParsing | Out-Null
            Write-Pass "PATCH /api/employees/{id}/deactivate"
        } catch {
            Write-Fail "PATCH /api/employees/{id}/deactivate failed: $(Get-ServerMessage $_)"
            $allPassed = $false
        }
    }

    if ($null -ne $countryScopeId) {
        try {
            Invoke-WebRequest -Method Patch -Uri "$BaseUrl/api/country-scopes/$countryScopeId/deactivate" -Headers $headers -ContentType "application/json" -Body "{}" -UseBasicParsing | Out-Null
            Write-Pass "PATCH /api/country-scopes/{id}/deactivate"
        } catch {
            Write-Fail "PATCH /api/country-scopes/{id}/deactivate failed: $(Get-ServerMessage $_)"
            $allPassed = $false
        }
    }
}

if ($allPassed) {
    Write-Host "QA R1-S03 smoke: PASS" -ForegroundColor Green
    exit 0
}

Write-Host "QA R1-S03 smoke: FAIL" -ForegroundColor Red
exit 1
