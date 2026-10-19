#!/usr/bin/env bash
# Smoke test of the debug APK on a running emulator.
#
# Walks through the main screens and saves a screenshot and UI dump of every step to OUT_DIR.
# Steps that can't find their view are logged and skipped, the test only fails when the app
# crashes or stops responding. It never submits a post.
#
# Usage: smoke-test.sh APK OUT_DIR
set -u

APK=$1
OUT=$2
PKG=io.github.addison110.cloverneo
ACTIVITY=$PKG/org.floens.chan.ui.activity.BoardActivity
HERE=$(cd "$(dirname "$0")" && pwd)

mkdir -p "$OUT"
STEPS="$OUT/steps.txt"
step=0

log() {
    echo "$*" | tee -a "$STEPS"
}

dump() {
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb pull /sdcard/ui.xml "$OUT/current.xml" > /dev/null 2>&1
    dismiss_crash_dialog
}

# A crash dialog blocks every following step, close it. The crash itself fails the test at the end.
dismiss_crash_dialog() {
    local pos
    if python3 "$HERE/ui.py" "$OUT/current.xml" text 'has stopped|keeps stopping|isn.t responding' > /dev/null; then
        adb exec-out screencap -p > "$OUT/crash-dialog-$step.png"
        log "CRASH DIALOG after step $step"
        if pos=$(python3 "$HERE/ui.py" "$OUT/current.xml" text '^(ok|close app)$'); then
            adb shell input tap $pos
            sleep 1
            adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
            adb pull /sdcard/ui.xml "$OUT/current.xml" > /dev/null 2>&1
        fi
    fi
}

# shot NAME [WAIT_SECONDS]
shot() {
    sleep "${2:-2}"
    step=$((step + 1))
    local name
    name=$(printf '%02d-%s' "$step" "$1")
    adb exec-out screencap -p > "$OUT/$name.png"
    dump
    cp "$OUT/current.xml" "$OUT/$name.xml" 2> /dev/null
    log "screenshot $name"
}

# tap ATTRIBUTE REGEX: taps the first matching view, waiting up to 15 seconds for it
tap() {
    local pos
    for _ in $(seq 15); do
        dump
        if pos=$(python3 "$HERE/ui.py" "$OUT/current.xml" "$1" "$2"); then
            adb shell input tap $pos
            log "tap $1=$2"
            return 0
        fi
        sleep 1
    done
    log "NOT FOUND $1=$2"
    return 1
}

# type_keys TEXT: types lowercase letters, digits and dots as key events, for keyboards where
# "input text" doesn't arrive (seen on Android 6.0)
type_keys() {
    local c i
    for ((i = 0; i < ${#1}; i++)); do
        c=${1:i:1}
        case $c in
            [a-z]) adb shell input keyevent "KEYCODE_$(echo "$c" | tr a-z A-Z)" ;;
            [0-9]) adb shell input keyevent "KEYCODE_$c" ;;
            .) adb shell input keyevent KEYCODE_PERIOD ;;
        esac
    done
}

# type_into RESOURCE_ID_REGEX TEXT: types into a text field, retrying when the keyboard ate characters
type_into() {
    tap resource-id "$1" || return 1
    local attempt
    for attempt in 1 2 3; do
        sleep 2
        if [ "$attempt" = 1 ]; then
            adb shell input text "$2"
        else
            type_keys "$2"
        fi
        sleep 1
        dump
        if python3 "$HERE/ui.py" "$OUT/current.xml" text "^$2\$" > /dev/null; then
            log "typed $2"
            return 0
        fi
        # Clear the field and try again
        adb shell input keyevent KEYCODE_MOVE_END
        for _ in $(seq 30); do adb shell input keyevent KEYCODE_DEL; done
    done
    log "NOT TYPED $2"
    return 1
}

back() {
    adb shell input keyevent KEYCODE_BACK
    log "back"
}

open_url() {
    adb shell am start -W -a android.intent.action.VIEW -d "$1" -n "$ACTIVITY" > /dev/null
    log "open $1"
}

# first_thread BOARD: number of the first non-sticky thread with replies, from the 4chan API
first_thread() {
    curl -sf "https://a.4cdn.org/$1/catalog.json" | python3 -c '
import json, sys
for page in json.load(sys.stdin):
    for t in page["threads"]:
        if not t.get("sticky") and t.get("replies", 0) > 5:
            print(t["no"]); sys.exit(0)
' 2> /dev/null
}

alive() {
    adb shell pidof "$PKG" > /dev/null
}

read -r WIDTH HEIGHT < <(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
log "device API $(adb shell getprop ro.build.version.sdk | tr -d '\r'), screen ${WIDTH}x${HEIGHT}"

adb install -r "$APK" > /dev/null || { log "install failed"; exit 1; }
adb logcat -c

# Launch, the notification permission is asked on Android 13+
adb shell am start -W -n "$ACTIVITY" > /dev/null
shot launch 5
tap resource-id 'permission_allow_button$' && shot notification-permission-allowed

# Add 4chan with all boards
adb shell input swipe 2 $((HEIGHT / 2)) $((WIDTH * 3 / 4)) $((HEIGHT / 2)) 300
shot drawer
tap text '^Setup sites$' && shot sites-setup
tap resource-id ':id/add$' && shot add-site-dialog
type_into ':id/url$' 4chan.org
tap text '^add$' && shot site-added 4
tap text '^4chan$' && shot site-configure
tap text '^Setup boards$' && shot board-setup
tap resource-id ':id/add$' && shot add-boards-dialog 4
tap resource-id ':id/select_all$'
tap text '^add$' && shot boards-added 3
back; back; back
shot browse 5

# Board and thread, opened by link
open_url "https://boards.4chan.org/g/"
shot board 6
THREAD=$(first_thread g)
if [ -n "$THREAD" ]; then
    open_url "https://boards.4chan.org/g/thread/$THREAD"
    shot thread 6

    # Reply box with the keyboard, the text is typed but never posted
    if tap resource-id ':id/reply_button$'; then
        shot reply-open 3
        type_into ':id/comment$' smoke
        shot reply-keyboard 3
        back
        back
        shot reply-closed
    fi

    tap resource-id ':id/thumbnail_view$' && shot image-viewer 6
    back
    shot thread-after-image

    # Rotation
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 1
    shot thread-landscape 4
    adb shell settings put system user_rotation 0
    shot thread-portrait 4
else
    log "NOT FOUND a /g/ thread from the 4chan API"
fi

# Video, /wsg/ only has video posts
THREAD=$(first_thread wsg)
if [ -n "$THREAD" ]; then
    open_url "https://boards.4chan.org/wsg/thread/$THREAD"
    shot video-thread 6
    tap resource-id ':id/thumbnail_view$' && shot video-playing 10
    back
    shot video-closed
else
    log "NOT FOUND a /wsg/ thread from the 4chan API"
fi

# Back out of everything, the app should go to the background without crashing
for _ in $(seq 8); do
    alive || break
    back
    sleep 1
done
shot after-back

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt"

failed=0
if grep -q "Process: $PKG" "$OUT/crash.txt"; then
    log "FAIL: the app crashed, see crash.txt"
    failed=1
fi
if grep -q "ANR in $PKG" "$OUT/logcat.txt"; then
    log "FAIL: the app stopped responding, see logcat.txt"
    failed=1
fi
if grep -q '^NOT \(FOUND\|TYPED\)' "$STEPS"; then
    log "WARNING: some steps were skipped, see the screenshots"
fi
[ "$failed" = 0 ] && log "PASS: no crashes"
exit $failed
