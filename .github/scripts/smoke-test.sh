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
[ "$API" -ge 33 ] && tap resource-id 'permission_allow_button$' && shot notification-permission-allowed

# A new install starts on 4chan's /g/
failed=0
shot default-board 5
if shows_threads; then
    log "the default board shows threads"
else
    log "FAIL: the default board shows no threads"
    failed=1
fi

# Add all boards of 4chan
setup_4chan site-exists
shot browse 5

# Board and thread, opened by link
open_url "https://boards.4chan.org/g/"
shot board 6
if ! shows_threads; then
    log "FAIL: the board shows no threads"
    failed=1
fi
THREAD=$(first_thread g)
if [ -n "$THREAD" ]; then
    open_url "https://boards.4chan.org/g/thread/$THREAD"
    shot thread 6

    # Reply box with the keyboard, the text is typed but never posted
    # On a narrow screen the board's button is still in the layout, under the thread
    if tap resource-id ':id/reply_button$' last; then
        shot reply-open 3
        # The posts' text views have the id "comment" too, the reply box has the only text field
        type_into class 'EditText$' smoke
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
    if tap resource-id ':id/thumbnail_view$'; then
        shot video-playing 10
        # Tapping the video shows its controls, by default in a bar at the bottom. Only the
        # screenshot shows them: a UI dump waits for the screen to be idle, which it isn't while a
        # video plays, and the controls have hidden by then.
        adb shell input tap $((WIDTH / 2)) $((HEIGHT / 2))
        shot video-controls 1
        sleep 4

        # Double-tapping the right side skips forward. Each "input" takes a while to start, so the
        # first one starts in the background and the second 100 ms later, the taps land about
        # 100 ms apart. The app logs the skip.
        x=$((WIDTH * 3 / 4)); y=$((HEIGHT / 2))
        adb shell "input tap $x $y & sleep 0.1; input tap $x $y; wait"
        log "double tap $x $y"
        shot video-skipped 0
        if adb logcat -d | grep -q "Double tap, skipped forward"; then
            log "the double tap skipped forward"
        else
            log "NOT FOUND the skip after double-tapping the video"
        fi
    fi
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

check_crashes "$PKG" && [ "$failed" = 0 ] && log "PASS: no crashes"
