[CmdletBinding()]
param(
    [int]$Cases = 200,
    [long]$Seed = 20260930,
    [string]$Out = 'datasets/adversarial_200',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if (-not $SkipBuild) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
& java -Xmx4g -jar build/agsa-pppum.jar adversarial-suite --out $Out --cases $Cases --seed $Seed
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "See $Out/summary.txt and $Out/failures.csv"
