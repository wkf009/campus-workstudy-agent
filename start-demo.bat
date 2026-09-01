@echo off
cd /d "%~dp0"

echo ============================================================
echo  Campus Work-Study Platform - One-Click Start
echo  Backend :8080   Frontend:3000   Auto login 3 roles
echo  (start-demo.ps1 will check MySQL, start services, login)
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-demo.ps1"

echo.
echo  Services started. Frontend: http://localhost:3000
echo  To stop: double-click stop-demo.bat
echo.
pause >nul