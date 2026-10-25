#!/usr/bin/env bash
# The scriptable half of docs/release-checklist.md: checks a signed release APK on one connected
# phone. The other half needs a person: signing in, syncing, reading the screen.
#
# usage: scripts/release-smoke.sh path/to/KharchaPaani-vX.Y.Z.apk
#
# Installs over what's there with `adb install -r`, so the phone's data stays. It never uninstalls:
# if Android refuses because a debug build is installed, uninstall that yourself, knowing it wipes
# the app's data on the phone.
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash on Windows would otherwise rewrite the /sdcard-style paths

PACKAGE=com.vaibhav.kharchapaani
# The release keystore's certificate (docs/distribution.md, step 4b).
RELEASE_SHA1="1F:29:43:41:59:3E:45:87:11:9B:8B:51:BB:69:94:CF:B3:74:70:37"

apk=${1:?usage: $0 path/to/release.apk}
[ -f "$apk" ] || { echo "No such file: $apk" >&2; exit 2; }

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "ok:   $*"; }

# 1. Exactly one phone.
devices=$(adb devices | awk 'NR > 1 && $2 == "device"' | wc -l)
[ "$devices" -eq 1 ] || fail "need exactly one device connected (found $devices)"
ok "one device: $(adb shell getprop ro.product.model | tr -d '\r') on Android $(adb shell getprop ro.build.version.release | tr -d '\r')"

# 2. Signed with the release key. A debug-signed or unsigned APK proves nothing about sign-in.
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$sdk" ] || fail "set ANDROID_HOME so apksigner can be found"
apksigner=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)/apksigner
[ -x "$apksigner" ] || apksigner="$apksigner.bat"
certs=$("$apksigner" verify --print-certs "$apk") || fail "apksigner says the APK isn't validly signed"
echo "$certs" | grep -qi "SHA-1 digest: $(echo "$RELEASE_SHA1" | tr -d ':' | tr 'A-F' 'a-f')" \
  || fail "not signed with the release key; apksigner printed:
$certs"
ok "signed with the release key"

# 3. Install over the existing app, keeping its data.
adb install -r "$apk" > /dev/null || fail "install refused (a debug build signed with another key? see the note at the top)"
version=$(adb shell dumpsys package "$PACKAGE" | grep -m1 -o 'versionName=[^ ]*' | tr -d '\r')
code=$(adb shell dumpsys package "$PACKAGE" | grep -m1 -o 'versionCode=[0-9]*' | tr -d '\r')
ok "installed: $version $code"

crashed() {
  adb logcat -d -b crash | grep -q "$PACKAGE"
}

# 4. Cold launch and stay up.
adb shell am force-stop "$PACKAGE"
adb logcat -b all -c
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
sleep 6
adb shell pidof "$PACKAGE" > /dev/null || fail "the app isn't running 6 s after launch"
crashed && fail "crash on launch: $(adb logcat -d -b crash | grep -A20 "$PACKAGE" | head -25)"
ok "cold launch, still running"

# 5. Shared text, the way a bank SMS arrives: the add sheet should open with it read.
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'Paid Rs 20 to Chai Point via UPI'" -n "$PACKAGE/.MainActivity" > /dev/null
sleep 4
crashed && fail "crash on shared text"
ok "shared text taken in (look: the add sheet should show Chai Point, ₹20)"

# 6. An invite link opens the join preview (the code is made up, so it should say it isn't valid).
adb shell am start -a android.intent.action.VIEW -d "kharchapaani://join/SMOKE1" -n "$PACKAGE/.MainActivity" > /dev/null
sleep 3
crashed && fail "crash on an invite link"
ok "invite link handled"

# 7. Backgrounded: widgets refresh without the app on screen. Background work used to hang until the
#    app was reopened (supabase-kt waits for the session after onStop).
adb shell input keyevent KEYCODE_HOME
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n "$PACKAGE/.widget.SplitFlowWidgetProvider" > /dev/null
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n "$PACKAGE/.widget.QuickAddWidgetProvider" > /dev/null
sleep 5
crashed && fail "crash refreshing the widgets in the background"
ok "widgets refreshed in the background"

echo
echo "Scripted checks passed. Now the manual list in docs/release-checklist.md."
