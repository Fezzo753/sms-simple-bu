@echo off
echo.
echo ============================================================
echo   SMS Backup to Offline Viewer Generator
echo ============================================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH.
    echo.
    echo Please install Python from: https://www.python.org/downloads/
    echo Make sure to check "Add Python to PATH" during installation.
    echo.
    pause
    exit /b 1
)

REM Check if sms.xml exists in current folder
if not exist "sms.xml" (
    echo ERROR: sms.xml not found in this folder.
    echo.
    echo Please place your SMS backup XML file in this folder
    echo and rename it to "sms.xml"
    echo.
    pause
    exit /b 1
)

echo Found sms.xml - Processing...
echo.

REM Run the processor
python sms_processor.py sms.xml sms_viewer_output

echo.
if exist "sms_viewer_output\SMS_Viewer.html" (
    echo SUCCESS! Opening viewer...
    start "" "sms_viewer_output\SMS_Viewer.html"
) else (
    echo Something went wrong. Check the error messages above.
)

echo.
pause
