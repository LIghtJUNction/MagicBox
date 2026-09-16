#!/usr/bin/env bash
# Keep control flow in one shell: the emulator action runs script lines separately.
set -euo pipefail
mkdir -p visual/light visual/dark
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
done
adb shell pm list packages | grep -E '^package:com.github.lightjunction.magicbox(\.ui)?$' > visual/coinstalled-packages.txt
test "$(wc -l < visual/coinstalled-packages.txt)" -eq 2
for theme in light dark; do
  if [ "$theme" = light ]; then adb shell cmd uimode night no; else adb shell cmd uimode night yes; fi
  for flavor in universal controller; do
    package=com.github.lightjunction.magicbox
    if [ "$flavor" = controller ]; then package=$package.ui; fi
    # Only emulator fixture data; each theme starts without reduced-motion prefs.
    adb shell pm clear "$package"
    timeout 180 adb shell am instrument -w -e class com.github.lightjunction.magicbox.CloudVisualTest \
      "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "visual/$theme/$flavor-instrumentation.txt"
    adb pull "/sdcard/Android/data/$package/files/." "visual/$theme/"
    grep -E 'OK \([1-9][0-9]* tests?\)' "visual/$theme/$flavor-instrumentation.txt"
  done
done
adb shell cmd uimode night no
timeout 120 adb shell am instrument -w -e class com.github.lightjunction.magicbox.NativeRuntimeIntegrationTest \
  com.github.lightjunction.magicbox.test/androidx.test.runner.AndroidJUnitRunner | tee visual/native-runtime-instrumentation.txt
adb pull /sdcard/Android/data/com.github.lightjunction.magicbox/files/. visual/
grep -E 'OK \([1-9][0-9]* tests?\)' visual/native-runtime-instrumentation.txt
# Raw emulator counters are retained, not advertised as physical-device FPS.
adb shell am start -W -n com.github.lightjunction.magicbox/.reboot.CloudActivity
sleep 3
adb shell dumpsys gfxinfo com.github.lightjunction.magicbox reset >/dev/null
sleep 5
adb shell dumpsys gfxinfo com.github.lightjunction.magicbox > visual/emulator-idle-gfxinfo.txt
