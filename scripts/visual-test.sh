#!/bin/sh
# Runs the scripted visual test client in an off-screen compositor.
# Nothing shows up on the desktop, screenshots end up in build/visual-test/screenshots.
# Afterwards the screenshots are checked for the item staying visible and the screens opening.
set -eu
cd "$(dirname "$0")/.."
mkdir -p build
config=$(mktemp)
trap 'rm -f "$config"' EXIT
cat > "$config" <<CONFIG
output HEADLESS-1 resolution 1280x720
exec sh -c './gradlew --no-daemon runVisualTest --console=plain > build/visual-test.log 2>&1; swaymsg exit'
CONFIG
# without these the client could still find the real session
env -u DISPLAY -u WAYLAND_DISPLAY -u HYPRLAND_INSTANCE_SIGNATURE -u SWAYSOCK \
    WLR_BACKENDS=headless WLR_LIBINPUT_NO_DEVICES=1 timeout 900 sway -c "$config" > build/visual-test-sway.log 2>&1
python3 scripts/check_visual.py
