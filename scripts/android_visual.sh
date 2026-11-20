#!/usr/bin/env bash
# Exercise the installed Compose UI and native runtime from the same validated APKs.
set -euo pipefail
evidence=evidence/device
mkdir -p "$evidence"
apk_path() {
  python3 - "$1" <<'PYAPK'
from pathlib import Path
import sys
matches = list(Path('validated').rglob(sys.argv[1]))
if len(matches) != 1:
    raise SystemExit(f'Expected one validated APK {sys.argv[1]}, found {len(matches)}')
print(matches[0])
PYAPK
}
assert_passed() {
  grep -Eq 'OK \([1-9][0-9]* tests?\)' "$1"
  if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "$1"; then
    return 1
  fi
}
cleanup() {
  adb logcat -d -s AndroidRuntime:E TestRunner:I > "$evidence/android-test-logcat.txt" || true
}
trap cleanup EXIT
for edition in universal ui; do
  adb install "$(apk_path "app-$edition-debug.apk")"
  adb install "$(apk_path "app-$edition-debug-androidTest.apk")"
  : > "$evidence/$edition-tests.txt"
done
adb shell pm list packages | tr -d '\r' | grep -E '^package:com.github.lightjunction.magicbox(\.ui)?$' > "$evidence/coinstalled-packages.txt"
test "$(wc -l < "$evidence/coinstalled-packages.txt")" -eq 2
for theme in light dark; do
  if [ "$theme" = light ]; then adb shell cmd uimode night no; else adb shell cmd uimode night yes; fi
  for edition in universal ui; do
    package=com.github.lightjunction.magicbox
    if [ "$edition" = ui ]; then package=$package.ui; fi
    # Clear only this emulator fixture so each theme tests default motion preferences.
    adb shell pm clear "$package"
    log="$evidence/$edition-$theme-tests.txt"
    timeout 180 adb shell am instrument -w -e class com.github.lightjunction.magicbox.CloudVisualTest \
      "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$log"
    assert_passed "$log"
    cat "$log" >> "$evidence/$edition-tests.txt"
    directory="$evidence/$edition/$theme"
    mkdir -p "$directory"
    adb pull "/sdcard/Android/data/$package/files/." "$directory/"
    # Preserve the actual installed-app capture under the promotion filename.
    cp "$directory/$edition-home.png" "$directory/home-$theme.png"
  done
done
adb shell cmd uimode night no
timeout 120 adb shell am instrument -w -e class com.github.lightjunction.magicbox.NativeRuntimeIntegrationTest \
  com.github.lightjunction.magicbox.test/androidx.test.runner.AndroidJUnitRunner | tee "$evidence/native-runtime-tests.txt"
assert_passed "$evidence/native-runtime-tests.txt"
adb pull /sdcard/Android/data/com.github.lightjunction.magicbox/files/native-routing-proof.txt "$evidence/native-routing-proof.txt"
grep -Fq 'PASS: installed Android parser' "$evidence/native-routing-proof.txt"
# Raw emulator counters do not establish physical-device frame rates.
adb shell am start -W -n com.github.lightjunction.magicbox/.reboot.CloudActivity
sleep 3
adb shell dumpsys gfxinfo com.github.lightjunction.magicbox reset >/dev/null
sleep 5
adb shell dumpsys gfxinfo com.github.lightjunction.magicbox > "$evidence/emulator-idle-gfxinfo.txt"
