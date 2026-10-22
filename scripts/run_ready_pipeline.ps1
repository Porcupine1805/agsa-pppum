[CmdletBinding()]
param(
    [ValidateSet('smoke','paper','scaling')][string]$Profile = 'smoke',
    [int]$BudgetGB = 24,
    [int]$SoloHeapGB = 16,
    [switch]$SkipBuild,
    [switch]$Fresh,
    [string[]]$RerunConfig = @()
)
$ErrorActionPreference = 'Stop'
if ($Fresh -and $RerunConfig.Count -gt 0) {
    throw '-Fresh and -RerunConfig cannot be used together.'
}
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if (-not $SkipBuild) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -jar build/agsa-pppum.jar selftest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
& java -jar build/agsa-pppum.jar env --out results/run_environment.txt
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& python scripts/validate_dataset_layout.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& python scripts/generate_ready_experiments.py --profile $Profile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$Config = "experiments/ready_$Profile.csv"
$Result = "results/ready_${Profile}_runs.csv"
$RunnerArgs = @('scripts/run_publication_parallel.py','--config',$Config,'--out',$Result,'--budget-gb',$BudgetGB,'--solo-heap-gb',$SoloHeapGB)
if ($Fresh) { $RunnerArgs += '--fresh' }
foreach ($ConfigId in $RerunConfig) { $RunnerArgs += @('--rerun-config', $ConfigId) }
& python @RunnerArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& python scripts/validate_results.py $Result --allow-infeasible
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& python scripts/plot_experiment_results.py $Result --out "results/figures/$Profile"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Complete: $Result and results/figures/$Profile"
