# Helpers for driving an app on the emulator from a shell script, sourced by the test scripts.
#
# The sourcing script sets OUT (results directory), PKG (package under test), ACTIVITY (its launch
# activity) and HERE (this directory), then calls init_device.

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

# tap ATTRIBUTE REGEX [last]: taps the first (or last) matching view, waiting up to 15 seconds
# for it
tap() {
    local pos
    for _ in $(seq 15); do
        dump
        if pos=$(python3 "$HERE/ui.py" "$OUT/current.xml" "$1" "$2" ${3:+"$3"}); then
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

# type_into ATTRIBUTE REGEX TEXT: types into a text field, retrying when the keyboard ate characters
type_into() {
    local text=$3
    tap "$1" "$2" || return 1
    local attempt
    for attempt in 1 2 3; do
        sleep 2
        if [ "$attempt" = 1 ]; then
            adb shell input text "$text"
        else
            type_keys "$text"
        fi
        sleep 1
        dump
        # Before Android 8 the Material text field reports its text as "<text>, <label>"
        if python3 "$HERE/ui.py" "$OUT/current.xml" text "^$text(,|\$)" > /dev/null; then
            log "typed $text"
            return 0
        fi
        [ "$attempt" = 3 ] && break
        # Clear the field and try again
        adb shell input keyevent KEYCODE_MOVE_END
        for _ in $(seq 30); do adb shell input keyevent KEYCODE_DEL; done
    done
    log "NOT TYPED $text"
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

# Taps the toolbar's menu button, left of the title. Swiping in from the edge is also the system
# back gesture with gesture navigation, so it only opens the drawer some of the time. Posts have
# a title view too, so it looks for the toolbar's title container first, which older Clover
# versions don't have.
open_drawer() {
    local pos
    dump
    if pos=$(python3 "$HERE/ui.py" "$OUT/current.xml" resource-id ':id/title_container$' left) ||
        pos=$(python3 "$HERE/ui.py" "$OUT/current.xml" resource-id ':id/title$' left); then
        adb shell input tap $pos
        log "tap menu button"
    else
        adb shell input swipe 2 $((HEIGHT / 2)) $((WIDTH * 3 / 4)) $((HEIGHT / 2)) 300
        log "swipe drawer open"
    fi
}

# shows_threads: whether the screen shows posts with thumbnails, waiting up to 20 seconds for
# them to load and retrying a failed load
shows_threads() {
    local pos
    for _ in $(seq 10); do
        dump
        python3 "$HERE/ui.py" "$OUT/current.xml" resource-id ':id/thumbnail(_view)?$' > /dev/null && return 0
        if pos=$(python3 "$HERE/ui.py" "$OUT/current.xml" text '^retry$'); then
            log "load failed, tap retry"
            adb shell input tap $pos
        fi
        sleep 2
    done
    return 1
}

alive() {
    adb shell pidof "$PKG" > /dev/null
}

init_device() {
    read -r WIDTH HEIGHT < <(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
    API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
    log "device API $API, screen ${WIDTH}x${HEIGHT}"

    # The emulator's network can still be connecting right after boot
    local i
    for i in $(seq 60); do
        adb shell dumpsys connectivity 2> /dev/null | grep -q '&VALIDATED' && break
        adb shell ping -c 1 -W 2 a.4cdn.org > /dev/null 2>&1 && break
        sleep 2
    done
    log "network up after $((i * 2)) seconds"
}

# Adds all boards of 4chan, starting from the board screen. Also adds the site itself unless
# called with "site-exists".
setup_4chan() {
    open_drawer
    shot drawer
    tap text '^Setup sites$' && shot sites-setup
    if [ "${1:-}" != site-exists ]; then
        tap resource-id ':id/add$' && shot add-site-dialog
        type_into resource-id ':id/url$' 4chan.org
        tap text '^add$' && shot site-added 4
    fi
    tap text '^4chan$' && shot site-configure
    tap text '^Setup boards$' && shot board-setup
    tap resource-id ':id/add$' && shot add-boards-dialog 4
    tap resource-id ':id/select_all$'
    tap text '^add$' && shot boards-added 3
    back; back; back
}

# check_crashes PACKAGE...: saves the logs and fails when one of the packages crashed or stopped
# responding. Prints PASS or the reasons and returns the result.
check_crashes() {
    adb logcat -d > "$OUT/logcat.txt"
    adb logcat -d -b crash > "$OUT/crash.txt"

    local failed=0 package
    for package in "$@"; do
        if grep -q "Process: $package" "$OUT/crash.txt"; then
            log "FAIL: $package crashed, see crash.txt"
            failed=1
        fi
        if grep -q "ANR in $package" "$OUT/logcat.txt"; then
            log "FAIL: $package stopped responding, see logcat.txt"
            failed=1
        fi
    done
    if grep -q '^NOT \(FOUND\|TYPED\)' "$STEPS"; then
        log "WARNING: some steps were skipped, see the screenshots"
    fi
    return $failed
}
