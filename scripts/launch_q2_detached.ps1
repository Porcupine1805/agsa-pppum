[CmdletBinding()]
param(
    [ValidateSet('0','1','2','3','4','all')]
    [string]$Wave = 'all',
    [string]$TaskName = 'agsa-q2-local',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$Script = Join-Path $PSScriptRoot 'run_q2_local.ps1'
$Root = Split-Path -Parent $PSScriptRoot
$Workspace = Split-Path -Parent (Split-Path -Parent $Root)
$Archive = Join-Path $Workspace 'shared\experiment-runs\2026-10-02-local-q2'
New-Item -ItemType Directory -Force -Path $Archive | Out-Null
$Transcript = Join-Path $Archive "q2_wave${Wave}_console.log"
$safe = ($TaskName -replace '[^a-zA-Z0-9\-]', '_')
$Wrapper = Join-Path $env:TEMP "run_${safe}.ps1"
$Task = $TaskName
$skip = if ($SkipBuild) { ' -SkipBuild' } else { '' }

@(
    "Set-Location -LiteralPath '$Root'"
    "Start-Transcript -Path '$Transcript' -Append"
    "& '$Script' -Wave $Wave$skip"
    '$code = $LASTEXITCODE'
    'Stop-Transcript'
    'exit $code'
) | Set-Content -Path $Wrapper -Encoding utf8

$tr = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$Wrapper`""
schtasks /Create /TN $Task /TR $tr /SC ONCE /ST 00:01 /F /RL LIMITED /IT | Out-Host
schtasks /Run /TN $Task | Out-Host
Set-Content -Path (Join-Path $Archive "q2_task_${Wave}.txt") -Value "task=$Task wrapper=$Wrapper wave=$Wave" -Encoding ascii
Write-Host "scheduled task=$Task wave=$Wave transcript=$Transcript wrapper=$Wrapper"
