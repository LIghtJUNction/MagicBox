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
  adb logcat -d -b events > evidence/device/events.txt || true
}
trap collect_evidence EXIT
# Stabilize a freshly booted emulator before app tests. Keep boot failures as
# evidence; never dismiss or suppress a MagicBox crash or ANR during acceptance.
adb logcat -d -b events > evidence/device/boot-events.txt
adb shell wm size 720x1600
adb shell wm density 320
adb shell cmd uimode night no
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am force-stop com.google.android.apps.nexuslauncher
sleep 3
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
  # Native runtime and touch tests do not depend on the permission prompt.
  # Grant only notifications, not root, network privileges or runtime bypasses.
  if [[ "$edition" == universal ]]; then adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS; fi
  adb shell am instrument -w -r "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "evidence/device/$edition-tests.txt" || failed=1
  if ! grep -Eq 'OK \([0-9]+ tests?\)' "evidence/device/$edition-tests.txt" ||
     grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "evidence/device/$edition-tests.txt"; then
    failed=1
  fi
  adb uninstall "$package.test"
done
collect_evidence
if grep -q 'Process: com.github.lightjunction.magicbox' evidence/device/crashes.txt ||
   grep -E 'am_anr.*com.github.lightjunction.magicbox' evidence/device/events.txt; then failed=1; fi
exit "$failed"
