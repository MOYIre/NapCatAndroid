#!/bin/bash
# Download PRoot and rootfs assets for NapCat Android
# This script downloads the required binary files during build

ASSETS_DIR="app/src/main/assets"
PROOT_DIR="$ASSETS_DIR/proot"
ROOTFS_DIR="$ASSETS_DIR/rootfs"

echo "Downloading PRoot binaries..."

# Create directories
mkdir -p "$PROOT_DIR"
mkdir -p "$ROOTFS_DIR"

# Download PRoot from SealDice release
SEALDICE_URL="https://github.com/sealdice/sealdice-android/releases/download"

# Get latest release URL
LATEST_URL=$(curl -sL "$SEALDICE_URL/latest" | jq -r '.assets[] | select('.browser_download_url' | head -1)
echo "Latest SealDice release: $LATEST_URL"

# Download the APK
TMP_FILE="/tmp/sealdice.apk"
curl -L -o "$TMP_FILE" "$LATEST_URL"

# Extract PRoot and rootfs from APK
unzip -j "$TMP_FILE" -d "$PROOT_DIR" "assets/sealdice/proot/*"
unzip -j "$TMP_FILE" -d "$ROOTFS_DIR" "assets/sealdice/rootfs/*"

# Move files to correct location
mv "$PROOT_DIR/assets/sealdice/proot/"* "$PROOT_DIR/"
mv "$ROOTFS_DIR/assets/sealdice/rootfs/"* "$ROOTFS_DIR/"

# Cleanup
rm -rf "$PROOT_DIR/assets" "$ROOTFS_DIR/assets"
rm "$TMP_FILE"

# Download start.sh
curl -L -o "$ASSETS_DIR/start.sh" "$SEALDICE_URL/download/v4.17.25/start.sh"

echo "Assets downloaded successfully!"
