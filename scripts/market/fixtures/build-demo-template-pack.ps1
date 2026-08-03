#Requires -Version 5.1
$out = Join-Path $PSScriptRoot 'demo-hello-1.0.0.kiwi-template-pack'
$manifest = '{"name":"Hello Demo","version":"1.0.0","slug":"demo-hello"}'
$envVars = '[]'
$bpmn = @'
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="hello" name="Hello" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start"/>
  </bpmn:process>
</bpmn:definitions>
'@

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $out) { Remove-Item $out -Force }
$zip = [System.IO.Compression.ZipFile]::Open($out, [System.IO.Compression.ZipArchiveMode]::Create)
function Add-ZipEntry([string]$name, [string]$text) {
    $e = $zip.CreateEntry($name)
    $w = New-Object System.IO.StreamWriter($e.Open())
    $w.Write($text)
    $w.Close()
}
Add-ZipEntry 'manifest.json' $manifest
Add-ZipEntry 'env-vars.json' $envVars
Add-ZipEntry 'processes/hello.bpmn' $bpmn
$zip.Dispose()
Write-Host "Created $out"
