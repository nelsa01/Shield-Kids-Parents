@echo off
echo ===================================================
echo Shield Kids - Build and Test Script
echo ===================================================

echo.
echo [1/4] Cleaning previous builds...
call gradlew clean
if %ERRORLEVEL% neq 0 (
    echo ERROR: Clean failed
    pause
    exit /b 1
)

echo.
echo [2/4] Building debug APK...
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    pause
    exit /b 1
)

echo.
echo [3/4] Running lint checks...
call gradlew lintDebug
if %ERRORLEVEL% neq 0 (
    echo WARNING: Lint issues found (continuing anyway)
)

echo.
echo [4/4] Build completed successfully!
echo.
echo Debug APK location:
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on connected device:
echo adb install app\build\outputs\apk\debug\app-debug.apk
echo.
echo To test features:
echo 1. Install and open the app
echo 2. Register/Login as parent
echo 3. Long-press Settings button for System Test
echo.

pause