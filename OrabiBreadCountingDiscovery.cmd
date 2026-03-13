@echo off
setlocal
:: ════════════════════════════════════════════════════════════════
:: Orabi Bread Counting – Board URL Discovery Launcher
:: أداة اكتشاف عنوان لوحة عدّ عيش عرابي
::
:: Double-click this file to run the discovery script.
:: It calls PowerShell with ExecutionPolicy Bypass so the script
:: runs without any manual configuration on any Windows machine.
:: ════════════════════════════════════════════════════════════════

set "SCRIPT_DIR=%~dp0"
set "PS1=%SCRIPT_DIR%DiscoverBoard.ps1"

if not exist "%PS1%" (
    echo DiscoverBoard.ps1 was not found.
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS1%"

if errorlevel 1 (
    echo.
    echo Discovery finished with an error. Press any key to close.
    pause >nul
)
