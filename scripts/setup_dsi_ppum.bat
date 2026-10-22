@echo off
set DEST=%~1
if "%DEST%"=="" set DEST=external\DSI-Lab1-PPUM
if exist "%DEST%\.git" goto done
git clone https://github.com/DSI-Lab1/PPUM.git "%DEST%"
:done
echo Set PPUM_ROOT to the absolute path of %DEST% before running experiments.
