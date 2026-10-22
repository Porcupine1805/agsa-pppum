$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if (Test-Path build) { Remove-Item -Recurse -Force build }
New-Item -ItemType Directory -Force -Path build/classes | Out-Null
$prefix = $Root.TrimEnd('\') + '\'
$sources = Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object {
    $_.FullName.Substring($prefix.Length).Replace('\', '/')
}
if (-not $sources) { throw "No Java sources under src/main/java" }
$sources | Set-Content -Encoding ascii build/sources.txt
& javac --release 17 -encoding UTF-8 -d build/classes "@build/sources.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& jar --create --file build/agsa-pppum.jar --main-class org.ppum.agsa.cli.Main -C build/classes .
Write-Host "Built build/agsa-pppum.jar"
