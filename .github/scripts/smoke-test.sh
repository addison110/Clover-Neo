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

# shellcheck source=lib.sh
. "$HERE/lib.sh"

init_device

adb install -r "$APK" > /dev/null || { log "install failed"; exit 1; }
adb logcat -c

# Launch, the notification permission is asked on Android 13+
adb shell am start -W -n "$ACTIVITY" > /dev/null
shot launch 5
tap resource-id 'permission_allow_button$' && shot notification-permission-allowed

# Add 4chan with all boards
setup_4chan
shot browse 5

# Board and thread, opened by link
open_url "https://boards.4chan.org/g/"
shot board 6
board_failed=0
if ! python3 "$HERE/ui.py" "$OUT/current.xml" resource-id ':id/thumbnail(_view)?$' > /dev/null; then
    log "FAIL: the board shows no threads"
    board_failed=1
fi
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

check_crashes "$PKG" && [ "$board_failed" = 0 ] && log "PASS: no crashes"
