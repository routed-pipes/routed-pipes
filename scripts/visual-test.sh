#!/bin/sh
# Runs the scripted visual test client in an off-screen compositor.
# Nothing shows up on the desktop, screenshots end up in build/visual-test/screenshots.
# Afterwards the screenshots are checked for the item staying visible and the screens opening,
# then the client is started a second time on the same world to check that orders survive a restart.
# With the argument "compat" the client runs once with the other mods installed, output goes to build/visual-test-compat.
# With "pack" it runs once with every mod of the play pack, output goes to build/visual-test-pack.
set -eu
cd "$(dirname "$0")/.."
mkdir -p build

# runs one gradle task inside a headless compositor, log goes to the given file
headless() {
    config=$(mktemp)
    cat > "$config" <<CONFIG
output HEADLESS-1 resolution 1280x720
exec sh -c './gradlew --no-daemon $1 --console=plain > $2 2>&1; swaymsg exit'
CONFIG
    # without these the client could still find the real session
    env -u DISPLAY -u WAYLAND_DISPLAY -u HYPRLAND_INSTANCE_SIGNATURE -u SWAYSOCK \
        WLR_BACKENDS=headless WLR_LIBINPUT_NO_DEVICES=1 timeout 1200 sway -c "$config" > "$2.sway" 2>&1 || true
    rm -f "$config"
}

if [ "${1:-}" = compat ]; then
    headless runVisualTestCompat build/visual-test-compat.log
    python3 scripts/check_visual.py build/visual-test-compat build/visual-test-compat.log
    exit
fi

if [ "${1:-}" = pack ]; then
    headless runVisualTestPack build/visual-test-pack.log
    python3 scripts/check_visual.py build/visual-test-pack build/visual-test-pack.log
    exit
fi

headless runVisualTest build/visual-test.log
python3 scripts/check_visual.py build/visual-test build/visual-test.log
grep -q "orders before restart placed" build/visual-test.log || { echo "FAIL the orders for the restart check were refused"; exit 1; }

headless runVisualTestResume build/visual-test-resume.log
if grep -q "restart check passed" build/visual-test-resume.log; then
    echo "restart check passed"
else
    grep "restart check failed" build/visual-test-resume.log || echo "FAIL the restarted client never reported back"
    exit 1
fi
