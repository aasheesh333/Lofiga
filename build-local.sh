#!/bin/bash

# Local Build Script for Lofiga
# This script builds APK and AAB locally without needing CI/CD

set -e

echo "🚀 Building Lofiga Android App Locally"
echo "======================================="

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo "❌ npm is not installed. Please install Node.js first."
    exit 1
fi

echo ""
echo "📦 Step 1: Installing dependencies..."
npm install

echo ""
echo "🔧 Step 2: Running Expo prebuild for Android..."
npx expo prebuild --platform android --clean

echo ""
echo "🔑 Step 3: Generating release keystore..."
cd android/app
if [ -f "release.keystore" ]; then
    echo "⚠️  Keystore already exists, skipping..."
else
    keytool -genkeypair -v -keystore release.keystore \
        -alias lofiga-release \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass password123 \
        -keypass password123 \
        -dname "CN=Lofiga, OU=Engineering, O=Lofiga, L=City, S=State, C=US"
fi
cd ../..

echo ""
echo "📝 Step 4: Configuring Gradle signing..."
cat >> android/gradle.properties << EOF

# Release signing config
MYAPP_RELEASE_STORE_FILE=release.keystore
MYAPP_RELEASE_KEY_ALIAS=lofiga-release
MYAPP_RELEASE_STORE_PASSWORD=password123
MYAPP_RELEASE_KEY_PASSWORD=password123
EOF

echo ""
echo "🏗️  Step 5: Building APK and AAB..."
cd android
chmod +x gradlew
./gradlew clean
./gradlew assembleRelease bundleRelease \
    --no-daemon \
    --max-workers=2 \
    -x lint \
    -x lintVitalRelease

cd ..

echo ""
echo "✅ BUILD COMPLETE!"
echo "=================="
echo ""
echo "📱 Your build artifacts are ready:"
echo ""
echo "APK: $(pwd)/android/app/build/outputs/apk/release/app-release.apk"
echo "AAB: $(pwd)/android/app/build/outputs/bundle/release/app-release.aab"
echo ""
echo "🎉 You can now install the APK on your Android device or upload AAB to Play Store!"
