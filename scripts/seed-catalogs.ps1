<#
.SYNOPSIS
    Seeds example brands and categories through the unified /api/catalogs/{type} API.

.PARAMETER BaseUrl
    Base URL of the running app. Defaults to http://localhost:8080.

.EXAMPLE
    ./scripts/seed-catalogs.ps1
    ./scripts/seed-catalogs.ps1 -BaseUrl http://localhost:9090
#>
param(
    [string]$BaseUrl = "http://localhost:8080"
)

function Add-Catalog {
    param(
        [Parameter(Mandatory)][string]$Type,
        [Parameter(Mandatory)][string]$Payload
    )

    try {
        $created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/catalogs/$Type" `
            -ContentType 'application/json; charset=utf-8' -Body $Payload
        Write-Host "  [ok]      $Type '$($created.name)' created (201)"
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

Write-Host "Seeding brands into $BaseUrl/api/catalogs/brand ..."

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
Write-Host "Seeding categories into $BaseUrl/api/catalogs/category ..."

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

Write-Host ""
Write-Host "Current brands:"
(Invoke-RestMethod -Uri "$BaseUrl/api/catalogs/brand") | ForEach-Object { Write-Host "  - $($_.name)" }

Write-Host ""
Write-Host "Current categories:"
(Invoke-RestMethod -Uri "$BaseUrl/api/catalogs/category") | ForEach-Object { Write-Host "  - $($_.name)" }
