@echo off
:: ════════════════════════════════════════════════════════════════
:: Orabi Bread Counting – Board URL Discovery Launcher
:: أداة اكتشاف عنوان لوحة عدّ عيش عرابي
::
:: Double-click this file to run the discovery script.
:: It calls PowerShell with ExecutionPolicy Bypass so the script
:: runs without any manual configuration on any Windows machine.
:: ════════════════════════════════════════════════════════════════
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OrabiBreadCountingDiscovery.ps1"
