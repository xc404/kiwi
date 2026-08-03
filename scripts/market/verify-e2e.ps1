#Requires -Version 5.1
<#
.SYNOPSIS
  End-to-end verification: build fixtures, publish to Nexus, call Kiwi remote-market API.

  Prerequisites:
  - Nexus running (docker compose -f docker/nexus/docker-compose.yml up -d)
  - Kiwi backend running with remote-market enabled in application-local.yml
  - NEXUS_PASSWORD, KIWI_TOKEN (Bearer) environment variables
#>
param(
    [string]$NexusUrl = $(if ($env:NEXUS_URL) { $env:NEXUS_URL } else { 'http://localhost:8081' }),
    [string]$NexusPassword = $env:NEXUS_PASSWORD,
    [string]$KiwiBase = $(if ($env:KIWI_BASE) { $env:KIWI_BASE } else { 'http://localhost:8000' }),
    [string]$KiwiToken = $env:KIWI_TOKEN
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixtures = Join-Path $repoRoot 'scripts\market\fixtures'
$exampleJar = Join-Path $repoRoot 'kiwi-bpmn\kiwi-bpmn-component-example\target\kiwi-bpmn-component-example-1.0.0-SNAPSHOT.jar'
$templatePack = Join-Path $fixtures 'demo-hello-1.0.0.kiwi-template-pack'

if (-not $NexusPassword) { throw 'Set NEXUS_PASSWORD' }
if (-not $KiwiToken) { throw 'Set KIWI_TOKEN (login and copy Bearer token)' }

Write-Host '== Build example plugin JAR =='
if (-not (Test-Path $exampleJar)) {
    Push-Location $repoRoot
    mvn -pl kiwi-bpmn/kiwi-bpmn-component-example -am package -DskipTests -q
    Pop-Location
}

Write-Host '== Build demo template pack =='
& (Join-Path $fixtures 'build-demo-template-pack.ps1')

Write-Host '== Publish to Nexus =='
$env:NEXUS_URL = $NexusUrl
$env:NEXUS_PASSWORD = $NexusPassword
& (Join-Path $PSScriptRoot 'publish.ps1') template demo-hello 1.0.0 $templatePack -Name 'Hello Demo' -Summary 'E2E demo template'
& (Join-Path $PSScriptRoot 'publish.ps1') plugin kiwi-bpmn-component-example 1.0.0-SNAPSHOT $exampleJar -ComponentKeys demoGreeting

$headers = @{ Authorization = "Bearer $KiwiToken" }

Write-Host '== Sync remote market =='
Invoke-RestMethod -Uri "$KiwiBase/bpm/remote-market/sync" -Method Post -Headers $headers | ConvertTo-Json

Write-Host '== List items =='
$list = Invoke-RestMethod -Uri "$KiwiBase/bpm/remote-market" -Headers $headers
$list | ConvertTo-Json -Depth 5
if ($list.Count -lt 2) { throw 'Expected at least 2 remote market items' }

Write-Host '== Install plugin =='
Invoke-RestMethod -Uri "$KiwiBase/bpm/remote-market/plugins/kiwi-bpmn-component-example/versions/1.0.0-SNAPSHOT/install" -Method Post -Headers $headers | ConvertTo-Json

Write-Host '== Install template =='
$body = @{ projectName = 'E2E Remote Hello' } | ConvertTo-Json
Invoke-RestMethod -Uri "$KiwiBase/bpm/remote-market/templates/demo-hello/versions/1.0.0/install" -Method Post -Headers $headers -Body $body -ContentType 'application/json' | ConvertTo-Json

Write-Host 'E2E verification completed successfully.'
