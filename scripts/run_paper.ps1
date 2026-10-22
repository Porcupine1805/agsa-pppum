$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
& powershell -File (Join-Path $PSScriptRoot "build.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -jar build/agsa-pppum.jar selftest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -jar build/agsa-pppum.jar directions --out results/three_directions
& java -jar build/agsa-pppum.jar compaction --out results/compaction
if (-not $env:PPUM_ROOT) {
    Write-Host "Toy checks done. Set PPUM_ROOT and re-run to execute experiments/tdp_paper.csv."
    exit 0
}
& java -jar build/agsa-pppum.jar batch --config experiments/tdp_paper.csv --out results/tdp_paper_runs.csv --continueOnError true
Write-Host "Wrote results/tdp_paper_runs.csv"
