#Requires -Version 5.1
param(
    [string]$NexusUrl = $(if ($env:NEXUS_URL) { $env:NEXUS_URL } else { 'http://localhost:8081' }),
    [string]$NexusUser = $(if ($env:NEXUS_USER) { $env:NEXUS_USER } else { 'admin' }),
    [Parameter(Mandatory = $true)]
    [string]$NexusPassword = $env:NEXUS_PASSWORD
)

$ErrorActionPreference = 'Stop'
$pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${NexusUser}:${NexusPassword}"))
$headers = @{ Authorization = "Basic $pair"; 'Content-Type' = 'application/json' }
$api = "$NexusUrl/service/rest/v1/repositories"

function Wait-Nexus {
    for ($i = 0; $i -lt 60; $i++) {
        try {
            Invoke-RestMethod -Uri "$NexusUrl/service/rest/v1/status" -Method Get | Out-Null
            return
        } catch {
            Start-Sleep -Seconds 5
        }
    }
    throw "Nexus not ready at $NexusUrl"
}

function New-RepoIfMissing($name, $body, $endpoint) {
  try {
    Invoke-RestMethod -Uri "$api/$name" -Headers $headers -Method Get | Out-Null
    Write-Host "Repository $name already exists"
  } catch {
    Invoke-RestMethod -Uri "$api/hosted/$endpoint" -Headers $headers -Method Post -Body $body | Out-Null
    Write-Host "Created repository: $name"
  }
}

Wait-Nexus

$rawBody = @{
  name = 'kiwi-market-raw'
  online = $true
  storage = @{
    blobStoreName = 'default'
    strictContentTypeValidation = $false
    writePolicy = 'ALLOW'
  }
  raw = @{ contentDisposition = 'ATTACHMENT' }
} | ConvertTo-Json -Depth 5

$mavenBody = @{
  name = 'kiwi-market-plugins'
  online = $true
  storage = @{
    blobStoreName = 'default'
    strictContentTypeValidation = $true
    writePolicy = 'ALLOW'
  }
  maven = @{
    versionPolicy = 'RELEASE'
    layoutPolicy = 'STRICT'
  }
} | ConvertTo-Json -Depth 5

New-RepoIfMissing 'kiwi-market-raw' $rawBody 'raw'
New-RepoIfMissing 'kiwi-market-plugins' $mavenBody 'maven'
Write-Host "Done. Raw base: $NexusUrl/repository/kiwi-market-raw/"
