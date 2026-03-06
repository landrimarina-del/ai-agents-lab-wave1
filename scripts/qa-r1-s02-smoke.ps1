Param(
    [string]$BaseUrl = "http://localhost:8080",
    [Alias("AdminUser")]
    [string]$AdminEmail = "admin@rise.local",
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute("PSAvoidUsingPlainTextForPassword", "")]
    [string]$AdminPassword = "Admin123!",
    [int]$UserId = 0
)

$ErrorActionPreference = "Stop"
$allPassed = $true
$token = $null
$selectedBusinessUnitId = $null
$targetUserId = $null

function Write-Pass($message) {
    Write-Host "[PASS] $message" -ForegroundColor Green
}

function Write-Fail($message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
}

function Test-Step($ok, $passMessage, $failMessage) {
    if ($ok) {
        Write-Pass $passMessage
        return $true
    }

    Write-Fail $failMessage
    return $false
}

function Get-StatusCodeFromException($ex) {
    if ($null -ne $ex.Exception.Response -and $null -ne $ex.Exception.Response.StatusCode) {
        return [int]$ex.Exception.Response.StatusCode
    }

    if ($null -ne $ex.Exception.Response -and $null -ne $ex.Exception.Response.Status) {
        return [int]$ex.Exception.Response.Status
    }

    return $null
}

function Get-ServerMessage($ex) {
    if ($null -ne $ex.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($ex.ErrorDetails.Message)) {
        try {
            $parsed = $ex.ErrorDetails.Message | ConvertFrom-Json
            if ($null -ne $parsed.message -and -not [string]::IsNullOrWhiteSpace("$($parsed.message)")) {
                return "$($parsed.message)"
            }
        } catch {
            return $ex.ErrorDetails.Message
        }
        return $ex.ErrorDetails.Message
    }

    if ($null -ne $ex.Exception -and -not [string]::IsNullOrWhiteSpace($ex.Exception.Message)) {
        return $ex.Exception.Message
    }

    return "unknown error"
}

function Resolve-CountryManagerUserId($baseUrl, $headers, $fallbackPassword) {
    $createUserUrl = "$baseUrl/api/users"
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
        $createBody = @{
            email = "qa.cm.$suffix@rise.local"
            fullName = "QA Country Manager $suffix"
            password = $fallbackPassword
            role = "COUNTRY_MANAGER"
            countryScope = @("IT")
        } | ConvertTo-Json

        try {
            $created = Invoke-RestMethod -Method Post -Uri $createUserUrl -Headers $headers -ContentType "application/json" -Body $createBody

            if ($null -ne $created.id) {
                return [int]$created.id
            }

            if ($null -ne $created.data -and $null -ne $created.data.id) {
                return [int]$created.data.id
            }
        } catch {
            if ($attempt -eq 3) {
                $statusCode = Get-StatusCodeFromException $_
                $serverMessage = Get-ServerMessage $_
                throw "Create COUNTRY_MANAGER failed (HTTP $statusCode): $serverMessage"
            }
        }
    }

    throw "Create COUNTRY_MANAGER response without id"
}

function Resolve-BusinessUnitId($baseUrl, $headers) {
    $buUrl = "$baseUrl/api/business-units"
    $buResponse = Invoke-RestMethod -Method Get -Uri $buUrl -Headers $headers

    if ($buResponse -is [System.Array] -and $buResponse.Count -gt 0 -and $null -ne $buResponse[0].id) {
        return [int]$buResponse[0].id
    }

    if ($null -ne $buResponse.id) {
        return [int]$buResponse.id
    }

    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 6).ToUpperInvariant()
    $createBuBody = @{
        code = "QABU$suffix"
        name = "QA Business Unit $suffix"
        countryCode = "IT"
        region = "IT"
        isActive = $true
    } | ConvertTo-Json

    $createdBu = Invoke-RestMethod -Method Post -Uri $buUrl -Headers $headers -ContentType "application/json" -Body $createBuBody
    if ($null -ne $createdBu.id) {
        return [int]$createdBu.id
    }

    throw "Create BUSINESS_UNIT response without id"
}

# a) GET /api/health => status UP
try {
    $healthUrl = "$BaseUrl/api/health"
    $health = Invoke-RestMethod -Method Get -Uri $healthUrl

    $isUp = $null -ne $health.status -and "$($health.status)" -match "(?i)^up$"
    if (-not (Test-Step $isUp "GET /api/health -> status UP" "GET /api/health -> expected status UP")) {
        $allPassed = $false
    }
}
catch {
    Write-Fail "GET /api/health failed: $($_.Exception.Message)"
    $allPassed = $false
}

# b) POST /api/auth/login => token presente
try {
    $loginUrl = "$BaseUrl/api/auth/login"
    $loginBody = @{
        email = $AdminEmail
        password = $AdminPassword
    } | ConvertTo-Json

    $login = Invoke-RestMethod -Method Post -Uri $loginUrl -ContentType "application/json" -Body $loginBody

    if ($null -ne $login.token) { $token = "$($login.token)" }
    elseif ($null -ne $login.accessToken) { $token = "$($login.accessToken)" }
    elseif ($null -ne $login.data -and $null -ne $login.data.token) { $token = "$($login.data.token)" }

    $hasToken = -not [string]::IsNullOrWhiteSpace($token)
    if (-not (Test-Step $hasToken "POST /api/auth/login -> token presente" "POST /api/auth/login -> token assente")) {
        $allPassed = $false
    }
}
catch {
    Write-Fail "POST /api/auth/login failed: $($_.Exception.Message)"
    $allPassed = $false
}

# c) GET /api/business-units con bearer => 200 e payload valido
try {
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Token non disponibile dal login"
    }

    $buUrl = "$BaseUrl/api/business-units"
    $headers = @{ Authorization = "Bearer $token" }
    $buResponse = Invoke-RestMethod -Method Get -Uri $buUrl -Headers $headers

    $validPayload = ($buResponse -is [System.Array]) -or ($buResponse -is [psobject])
    if (-not (Test-Step $validPayload "GET /api/business-units -> 200 e payload valido" "GET /api/business-units -> payload non valido")) {
        $allPassed = $false
    }

    $selectedBusinessUnitId = Resolve-BusinessUnitId -baseUrl $BaseUrl -headers $headers
}
catch {
    $statusCode = Get-StatusCodeFromException $_
    $serverMessage = Get-ServerMessage $_
    if ($null -ne $statusCode) {
        Write-Fail "GET /api/business-units failed (HTTP $statusCode): $serverMessage"
    } else {
        Write-Fail "GET /api/business-units failed: $serverMessage"
    }
    $allPassed = $false
}

# d) PATCH /api/users/{id}/business-units => 200/204
try {
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Token non disponibile dal login"
    }

    $headers = @{ Authorization = "Bearer $token" }
    if ($UserId -gt 0) {
        $targetUserId = $UserId
    } else {
        $targetUserId = Resolve-CountryManagerUserId -baseUrl $BaseUrl -headers $headers -fallbackPassword $AdminPassword
    }

    $patchUrl = "$BaseUrl/api/users/$targetUserId/business-units"
    $patchBody = @{ businessUnitIds = @($selectedBusinessUnitId) } | ConvertTo-Json

    $response = Invoke-WebRequest -Method Patch -Uri $patchUrl -Headers $headers -ContentType "application/json" -Body $patchBody
    $okStatus = ($response.StatusCode -eq 200 -or $response.StatusCode -eq 204)

    if (-not (Test-Step $okStatus "PATCH /api/users/$targetUserId/business-units -> status $($response.StatusCode)" "PATCH /api/users/$targetUserId/business-units -> expected 200/204, got $($response.StatusCode)")) {
        $allPassed = $false
    }
}
catch {
    $statusCode = Get-StatusCodeFromException $_
    $serverMessage = Get-ServerMessage $_
    if ($null -ne $statusCode) {
        $okStatus = ($statusCode -eq 200 -or $statusCode -eq 204)
        if ($okStatus) {
            Write-Pass "PATCH /api/users/$targetUserId/business-units -> status $statusCode"
        } else {
            Write-Fail "PATCH /api/users/$targetUserId/business-units failed (HTTP $statusCode): $serverMessage"
            $allPassed = $false
        }
    } else {
        Write-Fail "PATCH /api/users/$targetUserId/business-units failed: $serverMessage"
        $allPassed = $false
    }
}

# e) exit code
if ($allPassed) {
    Write-Host "QA R1-S02 smoke: PASS" -ForegroundColor Green
    exit 0
}

Write-Host "QA R1-S02 smoke: FAIL" -ForegroundColor Red
exit 1
