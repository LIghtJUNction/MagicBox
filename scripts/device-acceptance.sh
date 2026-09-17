#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence/device
collect_evidence() {
  for edition in universal ui; do
    package=com.github.lightjunction.magicbox
    if [[ "$edition" == ui ]]; then package+=.ui; fi
    adb pull "/sdcard/Android/data/$package/files/evidence" "evidence/device/$edition" >/dev/null 2>&1 || true
    adb shell dumpsys meminfo "$package" > "evidence/device/$edition-memory.txt" || true
    adb shell dumpsys gfxinfo "$package" framestats > "evidence/device/$edition-frames.txt" || true
  done
  adb logcat -d -b crash > evidence/device/crashes.txt || true
}
trap collect_evidence EXIT
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
  adb shell am instrument -w -r "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "evidence/device/$edition-tests.txt"
  if ! grep -Eq 'OK \([0-9]+ tests?\)' "evidence/device/$edition-tests.txt" ||
     grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "evidence/device/$edition-tests.txt"; then
    failed=1
  fi
  adb uninstall "$package.test"
done
collect_evidence
! grep -q 'Process: com.github.lightjunction.magicbox' evidence/device/crashes.txt || failed=1
exit "$failed"
