#!/usr/bin/env bash
# End-to-end test of "Import backup" with a backup of the original Clover app on a running emulator.
#
# Sets up 4chan in the original Clover app, makes an "adb backup" of it, imports that backup in
# Clover Neo and checks that Neo then has the site. Saves a screenshot and UI dump of every step
# to OUT_DIR.
#
# Usage: import-test.sh NEO_APK OUT_DIR
set -u

NEO_APK=$1
OUT=$2
HERE=$(cd "$(dirname "$0")" && pwd)

CLOVER_PKG=org.floens.chan
CLOVER_URL=https://f-droid.org/repo/org.floens.chan_30002.apk
CLOVER_SHA256=4f0ef87c31f70d9c752cf7b23059b395efac7d1f84cd51c790f6f242ab102196
NEO_PKG=io.github.addison110.cloverneo

# use_app PACKAGE: the app the helpers launch and check
use_app() {
    PKG=$1
    ACTIVITY=$PKG/org.floens.chan.ui.activity.BoardActivity
}

use_app "$CLOVER_PKG"
# shellcheck source=lib.sh
. "$HERE/lib.sh"

fail() {
    log "FAIL: $*"
    check_crashes "$NEO_PKG"
    exit 1
}

init_device

# The original Clover 3.0.2 from F-Droid
curl -sfL -o "$OUT/clover.apk" "$CLOVER_URL" || fail "could not download $CLOVER_URL"
echo "$CLOVER_SHA256  $OUT/clover.apk" | sha256sum -c --quiet || fail "the Clover APK has the wrong checksum"
# It targets API 25, newer Android versions block that without the flag
adb install --bypass-low-target-sdk-block "$OUT/clover.apk" > /dev/null 2>&1 ||
    adb install "$OUT/clover.apk" > /dev/null || fail "could not install Clover"
adb logcat -c

# Clover 3.0.2 sometimes crashes on its first start, when its hint popup comes up before the
# window is ready. Start it again when it does.
for attempt in 1 2 3; do
    adb shell am start -W -n "$ACTIVITY" > /dev/null
    shot clover-launch 5
    # "This app was built for an older version of Android"
    tap text '^ok$' && shot clover-old-app-warning
    sleep 5
    alive && break
    log "Clover crashed on start, attempt $attempt"
    dump
done
setup_4chan
shot clover-browse 5

# Back up Clover, the device asks for confirmation. Android skips stopped apps, so it only goes
# to the background, a force stop would leave an empty backup.
adb shell input keyevent KEYCODE_HOME
sleep 2
adb backup -f "$OUT/clover.ab" "$CLOVER_PKG" &
BACKUP=$!
tap resource-id 'button_allow$' || tap text '^back up my data$'
shot backup-confirm
for _ in $(seq 60); do
    kill -0 $BACKUP 2> /dev/null || break
    sleep 1
done
kill $BACKUP 2> /dev/null && fail "adb backup did not finish"
SIZE=$(stat -c %s "$OUT/clover.ab" 2> /dev/null || echo 0)
log "backup is $SIZE bytes"
# The backup is compressed and can be only a few KB, check that Clover's database is in it. An
# empty backup is just the header.
python3 -I - "$OUT/clover.ab" <<'PY' || fail "the backup has no Clover database, see the screenshots"
import io, sys, tarfile, zlib
header = open(sys.argv[1], 'rb').read().split(b'\n', 4)
data = zlib.decompress(header[4]) if header[2] == b'1' else header[4]
names = tarfile.open(fileobj=io.BytesIO(data)).getnames()
sys.exit(0 if 'apps/org.floens.chan/db/ChanDB' in names else 1)
PY

# Put it in Downloads, where the file picker finds it
adb push "$OUT/clover.ab" /sdcard/Download/clover.ab > /dev/null || fail "could not push the backup"
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d file:///sdcard/Download/clover.ab > /dev/null 2>&1

# Clover Neo, freshly installed
use_app "$NEO_PKG"
adb install -r "$NEO_APK" > /dev/null || fail "could not install Clover Neo"
adb shell am start -W -n "$ACTIVITY" > /dev/null
shot neo-launch 5
[ "$API" -ge 33 ] && tap resource-id 'permission_allow_button$' && shot notification-permission-allowed

open_drawer
shot neo-drawer
tap text '^Settings$' && shot settings
tap text '^Import backup$' || fail "no Import backup setting"
shot file-picker 4

# The picker opens on recent files or the last folder, go to Downloads when the file isn't shown
if ! tap text '^clover\.ab$'; then
    tap content-desc '^Show roots$' && shot picker-roots
    tap text '^Downloads?$' && shot picker-downloads 3
    tap text '^clover\.ab$' || fail "clover.ab is not in the file picker"
fi
shot import-confirm 4
tap text '^import$' || fail "no import confirmation, see the screenshots"

# The app restarts with the imported data
sleep 8
alive || adb shell am start -W -n "$ACTIVITY" > /dev/null
shot neo-restarted 5
grep -q "Imported the Clover backup" <(adb logcat -d -s CloverImport) || fail "the import was not applied"
log "the import was applied"

# Neo starts on its default /g/, after the import on the first board saved in Clover
if shows_threads && python3 "$HERE/ui.py" "$OUT/current.xml" text '^/3/' > /dev/null; then
    log "Clover's /3/ board is open and shows threads"
else
    fail "Clover's boards were not imported, /3/ is not open"
fi
open_drawer
shot neo-drawer-after-import

# Only Clover Neo is under test, Clover's own crashes are in crash.txt
check_crashes "$NEO_PKG" && log "PASS: the import works"
