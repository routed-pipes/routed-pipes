#!/bin/sh
# Measures frames per second of the client looking at the base of the benchmark, in an off-screen compositor.
# Usage: scripts/benchmark-client.sh [rows x chests per row x inputs ...]
# The numbers depend on what draws the picture off screen, the result names it. Compare the three lines with each other.
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/benchmark
for size in "${@:-20x20x4}"; do
    rm -f build/benchmark/client-result.txt
    config=$(mktemp)
    cat > "$config" <<CONFIG
output HEADLESS-1 resolution 1280x720
exec sh -c './gradlew --no-daemon runClientBenchmark -PbenchmarkSize=$size --console=plain > build/benchmark/client-$size.log 2>&1; swaymsg exit'
CONFIG
    env -u DISPLAY -u WAYLAND_DISPLAY -u HYPRLAND_INSTANCE_SIGNATURE -u SWAYSOCK \
        WLR_BACKENDS=headless WLR_LIBINPUT_NO_DEVICES=1 timeout 1200 sway -c "$config" > "build/benchmark/client-$size.sway" 2>&1 || true
    rm -f "$config"
    if [ -f build/benchmark/client-result.txt ]; then
        cp build/benchmark/client-result.txt "build/benchmark/client-$size.txt"
        cat build/benchmark/client-result.txt
    else
        echo "FAIL $size: no result, see build/benchmark/client-$size.log"
    fi
done
