@echo off
REM PRD 054 - one-shot .cmd wrapper around build.ps1.
REM Useful if PowerShell ExecutionPolicy blocks running .ps1 directly,
REM or if you want to double-click the build from File Explorer.
REM All args are forwarded to build.ps1; e.g. build.cmd -Sign
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
