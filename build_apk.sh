#!/bin/bash

# Build script for NapCatAndroid
# This script prepares the project for APK generation

set -e  # Exit on any error

echo "NapCatAndroid Build Script"
echo "==========================="

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "Error: Neither ANDROID_HOME nor ANDROID_SDK_ROOT is set."
    echo "Please set one of these environment variables to your Android SDK path."
    exit 1
fi

# Use ANDROID_HOME if set, otherwise use ANDROID_SDK_ROOT
ANDROID_SDK_PATH="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

echo "Using Android SDK at: $ANDROID_SDK_PATH"

# Check for required tools
if ! command -v adb &> /dev/null; then
    echo "Error: adb command not found. Please make sure Android SDK tools are in your PATH."
    exit 1
fi

if ! command -v zipalign &> /dev/null; then
    echo "Error: zipalign command not found. Please make sure Android build tools are in your PATH."
    exit 1
fi

echo "All required tools found."

# Prepare NapCat assets (in a real implementation, we would copy NapCat files here)
echo "Preparing NapCat assets..."
mkdir -p app/src/main/assets/napcat

# Create a placeholder file to simulate NapCat core files
cat > app/src/main/assets/napcat/README.md << EOF
# NapCat Core Files

This directory would contain the actual NapCat core files in a real deployment:
- Node.js runtime files
- NapCat JavaScript files
- Dependencies
- Configuration files
EOF

# In a real implementation, we would copy the actual NapCat files here
echo "NapCat assets prepared."

# Build debug APK
echo "Building debug APK..."
if [ -f "gradlew" ]; then
    ./gradlew assembleDebug
else
    echo "Error: gradlew not found. Please ensure you're in the project root directory."
    exit 1
fi

echo "Build completed successfully!"
echo ""
echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To install on device, run:"
echo "  adb install app/build/outputs/apk/debug/app-debug.apk"