$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
& powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -jar build/agsa-pppum.jar selftest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -Xmx6g -jar build/agsa-pppum.jar adversarial-suite `
  --out datasets/adversarial_2000 --cases 2000 --seed 20261001
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Complete: datasets/adversarial_2000/summary.txt'
