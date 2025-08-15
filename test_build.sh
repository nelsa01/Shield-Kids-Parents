#!/bin/bash

echo "==================================================="
echo "Shield Kids - Build and Test Script"
echo "==================================================="

echo ""
echo "[1/4] Cleaning previous builds..."
./gradlew clean
if [ $? -ne 0 ]; then
    echo "ERROR: Clean failed"
    exit 1
fi

echo ""
echo "[2/4] Building debug APK..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "ERROR: Build failed"
    exit 1
fi

echo ""
echo "[3/4] Running lint checks..."
./gradlew lintDebug
if [ $? -ne 0 ]; then
    echo "WARNING: Lint issues found (continuing anyway)"
fi

echo ""
echo "[4/4] Build completed successfully!"
echo ""
echo "Debug APK location:"
echo "app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To install on connected device:"
echo "adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To test features:"
echo "1. Install and open the app"
echo "2. Register/Login as parent"
echo "3. Long-press Settings button for System Test"
echo ""