#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the authorized physical Android device}"
case "$ANDROID_SERIAL" in
  emulator-*) echo 'A physical Android device is required.' >&2; exit 1 ;;
esac

scanner_root=$(cd "$(dirname "$0")/.." && pwd)
scanner_apk=${1:-"$scanner_root/android/build/outputs/apk/androidTest/debug/PamNativeScannerContracts-debug-androidTest.apk"}
test -f "$scanner_apk"
test "$(adb -s "$ANDROID_SERIAL" get-state)" = device
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != 1
adb -s "$ANDROID_SERIAL" install -r "$scanner_apk"
scanner_output=$(adb -s "$ANDROID_SERIAL" shell am instrument -w dev.pam.scanner.test/dev.pam.scanner.ScannerImageInstrumentation)
printf '%s\n' "$scanner_output"
[[ "$scanner_output" == *'PASS scanner image contracts:'* ]]
[[ "$scanner_output" == *'INSTRUMENTATION_CODE: -1'* ]]
