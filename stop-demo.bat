@echo off
cd /d "%~dp0"

echo ============================================================
echo  Campus Work-Study Platform - Stop Services
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-demo.ps1" -Stop

echo.
echo  Stopped. Press any key to close...
pause >nul