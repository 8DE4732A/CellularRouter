#!/bin/bash

# Script to download frpc binaries for Android
# Based on frp v0.65.0

set -e

FRP_VERSION="0.65.0"
BASE_URL="https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}"

echo "Downloading frpc binaries for Android (version ${FRP_VERSION})..."
echo

# Create directories
mkdir -p app/src/main/jniLibs/arm64-v8a
mkdir -p app/src/main/jniLibs/armeabi-v7a
mkdir -p app/src/main/jniLibs/x86_64
mkdir -p /tmp/frp_download

cd /tmp/frp_download

# Download and extract for arm64-v8a (Android aarch64)
echo "Downloading arm64-v8a..."
curl -L "${BASE_URL}/frp_${FRP_VERSION}_linux_arm64.tar.gz" -o frp_arm64.tar.gz
tar -xzf frp_arm64.tar.gz
mv "frp_${FRP_VERSION}_linux_arm64/frpc" libfrpc.so
cp libfrpc.so "$OLDPWD/app/src/main/jniLibs/arm64-v8a/"
echo "✓ arm64-v8a done"

# Download and extract for armeabi-v7a (Android arm)
echo "Downloading armeabi-v7a..."
curl -L "${BASE_URL}/frp_${FRP_VERSION}_linux_arm.tar.gz" -o frp_arm.tar.gz
tar -xzf frp_arm.tar.gz
mv "frp_${FRP_VERSION}_linux_arm/frpc" libfrpc.so
cp libfrpc.so "$OLDPWD/app/src/main/jniLibs/armeabi-v7a/"
echo "✓ armeabi-v7a done"

# Download and extract for x86_64 (Android x86_64)
echo "Downloading x86_64..."
curl -L "${BASE_URL}/frp_${FRP_VERSION}_linux_amd64.tar.gz" -o frp_amd64.tar.gz
tar -xzf frp_amd64.tar.gz
mv "frp_${FRP_VERSION}_linux_amd64/frpc" libfrpc.so
cp libfrpc.so "$OLDPWD/app/src/main/jniLibs/x86_64/"
echo "✓ x86_64 done"

# Cleanup
cd "$OLDPWD"
rm -rf /tmp/frp_download

echo
echo "All frpc binaries downloaded successfully!"
echo "Files location:"
ls -lh app/src/main/jniLibs/*/libfrpc.so
