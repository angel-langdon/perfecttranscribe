#!/bin/bash
set -euo pipefail

ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
REQUESTED_DEVICE="${1:-${ANDROID_SERIAL:-}}"
DEVICE=""

choose_device() {
    local device_lines=()
    local device_count
    local selection

    mapfile -t device_lines < <("$ADB" devices -l | awk 'NR > 1 && NF { print }')
    device_count="${#device_lines[@]}"

    if [ "$device_count" -eq 0 ]; then
        return 1
    fi

    if [ -n "$REQUESTED_DEVICE" ]; then
        DEVICE="$REQUESTED_DEVICE"
        return 0
    fi

    if [ "$device_count" -eq 1 ]; then
        DEVICE=$(awk '{print $1}' <<< "${device_lines[0]}")
        return 0
    fi

    if [ ! -t 0 ]; then
        echo "Multiple devices connected. Pass a serial as the first argument or set ANDROID_SERIAL."
        printf '%s\n' "${device_lines[@]}"
        exit 1
    fi

    echo "Multiple devices connected. Choose one:"
    for i in "${!device_lines[@]}"; do
        printf '  %d) %s\n' "$((i + 1))" "${device_lines[$i]}"
    done

    while true; do
        read -r -p "Device number: " selection
        if [[ "$selection" =~ ^[0-9]+$ ]] && [ "$selection" -ge 1 ] && [ "$selection" -le "$device_count" ]; then
            DEVICE=$(awk '{print $1}' <<< "${device_lines[$((selection - 1))]}")
            return 0
        fi
        echo "Enter a number between 1 and $device_count."
    done
}

echo "Detecting target device..."
while ! choose_device; do
    echo "  No devices found - plug in your phone..."
    sleep 2
done

echo "Using device: $DEVICE"
echo "Waiting for device authorization..."
while true; do
    STATUS=$("$ADB" devices | awk -v device="$DEVICE" '$1 == device { print $2 }')
    if [ "$STATUS" = "device" ]; then
        echo "Device authorized."
        break
    elif [ "$STATUS" = "unauthorized" ]; then
        echo "  Device unauthorized - accept the prompt on your phone..."
        sleep 2
    elif [ "$STATUS" = "offline" ]; then
        echo "  Device offline - waiting..."
        sleep 2
    elif [ -z "$STATUS" ]; then
        echo "  Device not found - plug in your phone..."
        sleep 2
    else
        echo "  Device status: $STATUS - waiting..."
        sleep 2
    fi
done

echo "Building debug APK..."
./gradlew assembleDebug -q

echo "Installing to device..."
"$ADB" -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk

echo "Launching app..."
"$ADB" -s "$DEVICE" shell am start -n com.perfecttranscribe/.MainActivity

echo "Done."
