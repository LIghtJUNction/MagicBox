#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence/device
trap 'adb logcat -d -b crash > evidence/device/crashes.txt 2>/dev/null || true' EXIT
adb logcat -c
for edition in universal ui; do
 apk=$(find validated -name "app-$edition-debug.apk" -print -quit)
 test -n "$apk"
 adb install -r "$apk"
done
adb shell pm list packages | grep 'com.github.lightjunction.magicbox' | tee evidence/device/packages.txt
test "$(grep -c '^package:com.github.lightjunction.magicbox' evidence/device/packages.txt)" = 2
failed=0
for edition in universal ui; do
 package=com.github.lightjunction.magicbox
 if [[ "$edition" == ui ]]; then package+=.ui; fi
 testapk=$(find validated -name "app-$edition-debug-androidTest.apk" -print -quit)
 test -n "$testapk"
 adb install -r "$testapk"
 adb shell am instrument -w -r "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "evidence/device/$edition-tests.txt" || failed=1
 adb pull "/sdcard/Android/data/$package/files/evidence" "evidence/device/$edition" || failed=1
 adb shell dumpsys meminfo "$package" > "evidence/device/$edition-memory.txt"
 adb shell dumpsys gfxinfo "$package" framestats > "evidence/device/$edition-frames.txt"
 adb logcat -d -s AndroidRuntime TestRunner > "evidence/device/$edition-errors.txt"
 if ! grep -Eq 'OK \([0-9]+ tests?\)' "evidence/device/$edition-tests.txt" || grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "evidence/device/$edition-tests.txt"; then failed=1; fi
 adb uninstall "$package.test"
done
adb logcat -d -b crash > evidence/device/crashes.txt
if grep -q 'Process: com.github.lightjunction.magicbox' evidence/device/crashes.txt; then failed=1; fi
exit "$failed"
