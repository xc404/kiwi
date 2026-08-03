#Requires -Version 5.1
<#
.SYNOPSIS
  Publish template pack or plugin JAR to Nexus kiwi-market-raw and update market/index.json

.EXAMPLE
  .\publish.ps1 template demo-hello 1.0.0 .\demo-hello-1.0.0.kiwi-template-pack -Name "Hello Demo"

.EXAMPLE
  .\publish.ps1 plugin kiwi-bpmn-component-example 1.0.0-SNAPSHOT .\kiwi-bpmn-component-example-1.0.0-SNAPSHOT.jar -ComponentKeys demoGreeting
#>
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet('template', 'plugin', 'upload-index')]
    [string]$Command,

    [Parameter(Position = 1)]
    [string]$Slug,

    [Parameter(Position = 2)]
    [string]$Version,

    [Parameter(Position = 3)]
    [string]$FilePath,

    [string]$NexusUrl = $(if ($env:NEXUS_URL) { $env:NEXUS_URL } else { 'http://localhost:8081' }),
    [string]$NexusUser = $(if ($env:NEXUS_USER) { $env:NEXUS_USER } else { 'admin' }),
    [string]$NexusPassword = $env:NEXUS_PASSWORD,
    [string]$RawRepo = 'kiwi-market-raw',
    [string]$Name,
    [string]$Summary,
    [string]$GroupId = 'com.kiwi',
    [string]$ComponentKeys
)

$ErrorActionPreference = 'Stop'
if (-not $NexusPassword) {
    throw 'Set NEXUS_PASSWORD environment variable'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$indexLocal = Join-Path $repoRoot '.market-index.local.json'
$base = "$NexusUrl/repository/$RawRepo"
$indexPath = 'market/index.json'
$pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${NexusUser}:${NexusPassword}"))
$headers = @{ Authorization = "Basic $pair" }

function Get-Sha256Hex([string]$path) {
    return (Get-FileHash -Algorithm SHA256 -Path $path).Hash.ToLower()
}

function Invoke-Upload([string]$dest, [string]$localFile) {
    Invoke-RestMethod -Uri "$base/$dest" -Method Put -Headers $headers -InFile $localFile | Out-Null
    Write-Host "Uploaded $dest"
}

function Get-IndexLocal {
    try {
        Invoke-WebRequest -Uri "$base/$indexPath" -Headers $headers -OutFile $indexLocal | Out-Null
    } catch {
        @{ schemaVersion = 1; generatedAt = ''; items = @() } | ConvertTo-Json -Depth 10 | Set-Content -Path $indexLocal -Encoding UTF8
    }
    $data = Get-Content $indexLocal -Raw | ConvertFrom-Json
    if (-not $data.items) { $data | Add-Member -NotePropertyName items -NotePropertyValue @() }
    $data.schemaVersion = 1
    $data.generatedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $data | ConvertTo-Json -Depth 20 | Set-Content -Path $indexLocal -Encoding UTF8
    return $data
}

function Save-IndexItem($item) {
    $data = Get-IndexLocal
    $filtered = @($data.items | Where-Object { -not ($_.slug -eq $item.slug -and $_.version -eq $item.version -and $_.type -eq $item.type) })
    $filtered += $item
    $data.items = $filtered
    $data.generatedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $data | ConvertTo-Json -Depth 20 | Set-Content -Path $indexLocal -Encoding UTF8
    Invoke-Upload $indexPath $indexLocal
}

switch ($Command) {
    'upload-index' {
        Get-IndexLocal | Out-Null
        Invoke-Upload $indexPath $indexLocal
    }
    'template' {
        if (-not (Test-Path $FilePath)) { throw "File not found: $FilePath" }
        $displayName = if ($Name) { $Name } else { $Slug }
        $hash = Get-Sha256Hex $FilePath
        $dest = "templates/$Slug/$Version/$Slug-$Version.kiwi-template-pack"
        $manifestDest = "templates/$Slug/$Version/manifest.json"
        Invoke-Upload $dest $FilePath
        $manifestTmp = [System.IO.Path]::GetTempFileName()
        @{ slug = $Slug; version = $Version; name = $displayName } | ConvertTo-Json | Set-Content $manifestTmp
        Invoke-Upload $manifestDest $manifestTmp
        Remove-Item $manifestTmp -Force
        $item = [ordered]@{
            type = 'template'
            slug = $Slug
            name = $displayName
            version = $Version
            summary = $Summary
            downloadUrl = $dest
            sha256 = $hash
            manifestUrl = $manifestDest
        }
        Save-IndexItem $item
        Write-Host "Published template ${Slug}@${Version} sha256=$hash"
    }
    'plugin' {
        if (-not (Test-Path $FilePath)) { throw "File not found: $FilePath" }
        $artifact = $Slug
        $groupPath = ($GroupId -replace '\.', '/')
        $hash = Get-Sha256Hex $FilePath
        $dest = "plugins/$groupPath/$artifact/$Version/$artifact-$Version.jar"
        $manifestDest = "plugins/$groupPath/$artifact/$Version/manifest.json"
        Invoke-Upload $dest $FilePath
        $manifestTmp = [System.IO.Path]::GetTempFileName()
        @{ groupId = $GroupId; artifactId = $artifact; version = $Version } | ConvertTo-Json | Set-Content $manifestTmp
        Invoke-Upload $manifestDest $manifestTmp
        Remove-Item $manifestTmp -Force
        $keys = @()
        if ($ComponentKeys) { $keys = $ComponentKeys.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ } }
        $item = [ordered]@{
            type = 'plugin'
            slug = $artifact
            name = $artifact
            version = $Version
            downloadUrl = $dest
            sha256 = $hash
            manifestUrl = $manifestDest
            componentKeys = $keys
            mavenCoordinate = @{ groupId = $GroupId; artifactId = $artifact; version = $Version }
        }
        Save-IndexItem $item
        Write-Host "Published plugin ${artifact}@${Version} sha256=$hash"
    }
}
