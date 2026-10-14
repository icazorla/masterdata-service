<#
.SYNOPSIS
    Seeds example brands, categories, Spanish autonomous communities (region) and provinces through the management API (/admin/api/catalogs/{type}),
    then lists them through the query API (/api/catalogs/{type}).

.PARAMETER BaseUrl
    Base URL of the running app. Defaults to http://localhost:8080.

.PARAMETER User
    User with role ADMIN for the management API (HTTP Basic). Defaults to $env:SEED_USER, or the
    dev user "admin" (see app.security.users).

.PARAMETER Password
    Password of -User. Defaults to $env:SEED_PASSWORD, or the dev password "admin".

.EXAMPLE
    ./scripts/seed-catalogs.ps1
    ./scripts/seed-catalogs.ps1 -BaseUrl http://localhost:9090 -User admin -Password admin
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = $(if ($env:SEED_USER) { $env:SEED_USER } else { "admin" }),
    [string]$Password = $(if ($env:SEED_PASSWORD) { $env:SEED_PASSWORD } else { "admin" })
)

# Basic header built by hand so it works the same on Windows PowerShell 5.1 and PowerShell 7
# (whose -Credential refuses plain http unless -AllowUnencryptedAuthentication is passed).
$AuthHeaders = @{
    Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${User}:${Password}"))
}

function Add-Catalog {
    param(
        [Parameter(Mandatory)][string]$Type,
        [Parameter(Mandatory)][string]$Payload
    )

    try {
        $created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/admin/api/catalogs/$Type" `
            -Headers $AuthHeaders -ContentType 'application/json; charset=utf-8' -Body $Payload
        Write-Host "  [ok]      $Type '$($created.translations.en.name)' created (201)"
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $detail = $_.ErrorDetails.Message
        if (-not $detail -and $_.Exception.Response) {
            # ErrorDetails.Message is sometimes empty depending on PowerShell/.NET version —
            # fall back to reading the response body stream directly.
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $stream.Position = 0
                $detail = (New-Object System.IO.StreamReader($stream)).ReadToEnd()
            }
            catch { }
        }
        if ($statusCode -eq 409) {
            Write-Host "  [exists]  $Type payload already present (409)"
        }
        else {
            Write-Host "  [skip]    $Type payload -> HTTP ${statusCode}: $detail"
        }
    }
}

Write-Host "Seeding brands into $BaseUrl/admin/api/catalogs/brand ..."

Add-Catalog -Type 'brand' -Payload @'
{
  "translations": {
    "en": {"name": "Logitech", "description": "Peripheral manufacturer"},
    "es": {"name": "Logitech", "description": "Fabricante de periféricos"}
  }
}
'@

Add-Catalog -Type 'brand' -Payload @'
{
  "translations": {
    "en": {"name": "Samsung", "description": "Electronics manufacturer"},
    "es": {"name": "Samsung", "description": "Fabricante de electrónica"}
  }
}
'@

Add-Catalog -Type 'brand' -Payload @'
{
  "translations": {
    "en": {"name": "Acme", "description": "Generic brand for everything"},
    "es": {"name": "Acme", "description": "Marca genérica para todo"}
  }
}
'@

Write-Host ""
Write-Host "Seeding categories into $BaseUrl/admin/api/catalogs/category ..."

Add-Catalog -Type 'category' -Payload @'
{
  "translations": {
    "en": {"name": "Peripherals", "description": "Keyboards, mice and accessories"},
    "es": {"name": "Periféricos", "description": "Teclados, ratones y accesorios"}
  }
}
'@

Add-Catalog -Type 'category' -Payload @'
{
  "translations": {
    "en": {"name": "Screens", "description": "Monitors and displays"},
    "es": {"name": "Pantallas", "description": "Monitores y displays"}
  }
}
'@

Add-Catalog -Type 'category' -Payload @'
{
  "translations": {
    "en": {"name": "Cables", "description": "Cables and adapters"},
    "es": {"name": "Cables", "description": "Cables y adaptadores"}
  }
}
'@

# Comunidades autónomas y provincias de España: los datos (códigos INE) viven en scripts/data/*.csv,
# compartidos con seed-catalogs.sh.
$DataDir = Join-Path $PSScriptRoot 'data'

Write-Host ""
Write-Host "Seeding Spanish autonomous communities into $BaseUrl/admin/api/catalogs/region ..."
Import-Csv -Path (Join-Path $DataDir 'spain-regions.csv') -Delimiter '|' -Encoding UTF8 | ForEach-Object {
    $payload = @{
        ineCode      = [int]$_.ineCode
        translations = @{
            en = @{ name = $_.nameEn; description = $_.descriptionEn }
            es = @{ name = $_.nameEs; description = $_.descriptionEs }
        }
    } | ConvertTo-Json -Depth 4
    Add-Catalog -Type 'region' -Payload $payload
}

Write-Host ""
Write-Host "Seeding Spanish provinces into $BaseUrl/admin/api/catalogs/province ..."
Import-Csv -Path (Join-Path $DataDir 'spain-provinces.csv') -Delimiter '|' -Encoding UTF8 | ForEach-Object {
    $payload = @{
        ineCode      = [int]$_.ineCode
        regionCode   = [int]$_.regionCode
        translations = @{
            en = @{ name = $_.nameEn; description = $_.descriptionEn }
            es = @{ name = $_.nameEs; description = $_.descriptionEs }
        }
    } | ConvertTo-Json -Depth 4
    Add-Catalog -Type 'province' -Payload $payload
}

Write-Host ""
Write-Host "Current brands:"
(Invoke-RestMethod -Uri "$BaseUrl/api/catalogs/brand") | ForEach-Object { Write-Host "  - $($_.name)" }

Write-Host ""
Write-Host "Current categories:"
(Invoke-RestMethod -Uri "$BaseUrl/api/catalogs/category") | ForEach-Object { Write-Host "  - $($_.name)" }

Write-Host ""
Write-Host "Autonomous communities: $(@(Invoke-RestMethod -Uri "$BaseUrl/api/catalogs/region").Count)"
Write-Host "Provinces: $(@(Invoke-RestMethod -Uri "$BaseUrl/api/catalogs/province").Count)"
