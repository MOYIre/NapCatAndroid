#!/bin/bash

# Script to validate NapCatAndroid project structure
echo "Validating NapCatAndroid project structure..."

# Check if required files exist
required_files=(
    "settings.gradle"
    "build.gradle"
    "gradle.properties"
    "gradlew"
    "gradlew.bat"
    "app/build.gradle"
    "app/src/main/AndroidManifest.xml"
    "app/src/main/java/MainActivity.java"
    "app/src/main/java/NapCatService.java"
    "app/src/main/res/values/strings.xml"
    "app/src/main/res/layout/activity_main.xml"
)

missing_files=()
for file in "${required_files[@]}"; do
    if [ ! -f "$file" ]; then
        missing_files+=("$file")
    fi
done

if [ ${#missing_files[@]} -eq 0 ]; then
    echo "✓ All required files exist"
else
    echo "✗ Missing files:"
    for file in "${missing_files[@]}"; do
        echo "  - $file"
    done
    exit 1
fi

# Check if required directories exist
required_dirs=(
    "app/src/main/java"
    "app/src/main/res"
    "app/src/main/res/values"
    "app/src/main/res/layout"
    "app/src/main/res/xml"
    "app/src/main/res/mipmap-hdpi"
    "app/src/main/res/mipmap-mdpi"
    "app/src/main/res/mipmap-xhdpi"
    "app/src/main/res/mipmap-xxhdpi"
    "app/src/main/res/mipmap-xxxhdpi"
    "gradle/wrapper"
)

missing_dirs=()
for dir in "${required_dirs[@]}"; do
    if [ ! -d "$dir" ]; then
        missing_dirs+=("$dir")
    fi
done

if [ ${#missing_dirs[@]} -eq 0 ]; then
    echo "✓ All required directories exist"
else
    echo "✗ Missing directories:"
    for dir in "${missing_dirs[@]}"; do
        echo "  - $dir"
    done
    exit 1
fi

echo "✓ Project structure validation completed successfully"