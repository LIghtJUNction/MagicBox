#!/usr/bin/env bash
# The emulator action executes each script line separately. Keep shell control
# flow here, so loops and failure handling run in one shell.
set -euo pipefail
mkdir -p visual
cleanup() {
  adb logcat -d -s AndroidRuntime:E TestRunner:I > visual/android-test-logcat.txt || true
  for package in com.github.lightjunction.magicbox com.github.lightjunction.magicbox.ui; do
    adb pull "/sdcard/Android/data/$package/files/." visual/ >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT
for flavor in universal controller; do
  adb install "apks/$flavor/debug/app-$flavor-debug.apk"
  adb install "apks/androidTest/$flavor/debug/app-$flavor-debug-androidTest.apk"
  package=com.github.lightjunction.magicbox
  if [ "$flavor" = controller ]; then package=$package.ui; fi
  adb shell am instrument -w -e class com.github.lightjunction.magicbox.CloudVisualTest \
    "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "visual/$flavor-instrumentation.txt"
  adb pull "/sdcard/Android/data/$package/files/." visual/
  grep -E 'OK \([1-9][0-9]* tests?\)' "visual/$flavor-instrumentation.txt"
done
adb shell am instrument -w -e class com.github.lightjunction.magicbox.NativeRuntimeIntegrationTest \
  com.github.lightjunction.magicbox.test/androidx.test.runner.AndroidJUnitRunner | tee visual/native-runtime-instrumentation.txt
adb pull /sdcard/Android/data/com.github.lightjunction.magicbox/files/. visual/
grep -E 'OK \([1-9][0-9]* tests?\)' visual/native-runtime-instrumentation.txt
adb shell dumpsys gfxinfo com.github.lightjunction.magicbox > visual/emulator-gfxinfo.txt
