#!/bin/bash
set -e

ADB="$HOME/Android/Sdk/platform-tools/adb"
DEVICE="RZCY7038XGN"

echo "Waiting for device authorization..."
$ADB -s "$DEVICE" reconnect 2>/dev/null || true
while true; do
    STATUS=$($ADB devices | grep "$DEVICE" | awk '{print $2}')
    if [ "$STATUS" = "device" ]; then
        echo "Device authorized."
        break
    elif [ "$STATUS" = "unauthorized" ]; then
        echo "  Device unauthorized — accept the prompt on your phone..."
        sleep 2
    elif [ -z "$STATUS" ]; then
        echo "  Device not found — plug in your phone..."
        sleep 2
    else
        echo "  Device status: $STATUS — waiting..."
        sleep 2
    fi
done

echo "Building debug APK..."
./gradlew assembleDebug -q

echo "Installing to device..."
$ADB -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk

echo "Launching app..."
$ADB -s "$DEVICE" shell am start -n com.perfecttranscribe/.MainActivity

echo "Done."
