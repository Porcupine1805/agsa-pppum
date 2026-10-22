[CmdletBinding()]
param(
    [ValidateSet('0','1','2','3','4','all')]
    [string]$Wave = '1',
    [int]$BudgetGB = 14,
    [int]$SoloHeapGB = 12,
    [switch]$SkipBuild,
    [switch]$Fresh
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$Py = 'C:\Users\tranc\AppData\Local\Programs\Python\Python312\python.exe'
if (-not (Test-Path $Py)) { $Py = 'python' }

$Java = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\java.exe'
if (-not (Test-Path $Java)) { $Java = 'java' }

$Workspace = Split-Path -Parent (Split-Path -Parent $Root)
$Archive = Join-Path $Workspace 'shared\experiment-runs\2026-10-02-local-q2'
New-Item -ItemType Directory -Force -Path $Archive, (Join-Path $Root 'results\q2') | Out-Null
$Log = Join-Path $Archive 'q2_local.log'

function Write-Log([string]$Message) {
    $line = '{0:u} {1}' -f (Get-Date).ToUniversalTime(), $Message
    Add-Content -Path $Log -Value $line -Encoding UTF8
    Write-Host $line
}

function Invoke-Generate {
    & $Py scripts\generate_q2_experiments.py --profile all
    if ($LASTEXITCODE -ne 0) { throw "generate_q2_experiments.py failed: $LASTEXITCODE" }
}

function Invoke-Wave([string]$Name, [string]$Config, [string]$Out, [int]$TimeoutSec) {
    $waveBudget = 24
    $waveSolo = 12
    if ($Name -eq '4-seeds10') { $waveSolo = 4 }
    $pack = @('--max-jvms', '12', '--pack-profile', 'auto')
    Write-Log "START wave=$Name config=$Config timeout=${TimeoutSec}s budget=${waveBudget}g solo=${waveSolo}g pack=12jvm"
    $runner = @(
        'scripts\run_publication_parallel.py',
        '--config', $Config,
        '--out', $Out,
        '--budget-gb', "$waveBudget",
        '--solo-heap-gb', "$waveSolo",
        '--timeout-sec', "$TimeoutSec"
    )
    $runner += $pack
    if ($Fresh) { $runner += '--fresh' }
    & $Py @runner
    $code = $LASTEXITCODE
    if (Test-Path $Out) {
        Copy-Item -Force $Out (Join-Path $Archive (Split-Path $Out -Leaf))
    }
    $parts = Join-Path $Root ("results\parts\" + [IO.Path]::GetFileNameWithoutExtension($Config))
    if (Test-Path $parts) {
        $destParts = Join-Path $Archive ("parts-" + [IO.Path]::GetFileNameWithoutExtension($Config))
        New-Item -ItemType Directory -Force -Path $destParts | Out-Null
        Copy-Item -Force (Join-Path $parts '*.csv') $destParts -ErrorAction SilentlyContinue
        Copy-Item -Force (Join-Path $parts '*.timeout') $destParts -ErrorAction SilentlyContinue
        Copy-Item -Force (Join-Path $parts '*.log') $destParts -ErrorAction SilentlyContinue
    }
    Write-Log "END wave=$Name exit=$code"
    if ($code -ne 0) { throw "wave $Name failed with exit $code" }
}

Write-Log "cwd=$Root wave=$Wave budget=${BudgetGB}g solo=${SoloHeapGB}g"

if (-not $SkipBuild) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
    if ($LASTEXITCODE -ne 0) { throw "build.ps1 failed: $LASTEXITCODE" }
    & $Java -jar build\agsa-pppum.jar selftest
    if ($LASTEXITCODE -ne 0) { throw "selftest failed: $LASTEXITCODE" }
}

Invoke-Generate
& $Java -jar build\agsa-pppum.jar env --out results\q2\run_environment.txt
Copy-Item -Force results\q2\run_environment.txt (Join-Path $Archive 'run_environment.txt') -ErrorAction SilentlyContinue

$waves = switch ($Wave) {
    '0' { @() }
    '1' { @('1') }
    '2' { @('2') }
    '3' { @('3') }
    '4' { @('4') }
    'all' { @('1','2','3','4') }
}

foreach ($w in $waves) {
    switch ($w) {
        '1' { Invoke-Wave '1-mumip' 'experiments\q2_mumip.csv' 'results\q2\q2_mumip_runs.csv' 1800 }
        '2' { Invoke-Wave '2-liu-ref' 'experiments\q2_liu_ref.csv' 'results\q2\q2_liu_ref_runs.csv' 1800 }
        '3' { Invoke-Wave '3-retail-to' 'experiments\q2_retail_to.csv' 'results\q2\q2_retail_to_runs.csv' 10800 }
        '4' { Invoke-Wave '4-seeds10' 'experiments\q2_seeds10_small.csv' 'results\q2\q2_seeds10_small_runs.csv' 3600 }
    }
}

Write-Log "ALL_DONE wave=$Wave"
Write-Host "Archive: $Archive"
