#!/bin/sh
# Makes the client in the given game directory silent: no sound, no narrator, and not the first-launch screen
# that reads its welcome out loud. A fresh directory has no options yet, and an off-screen client would talk.
# Usage: scripts/quiet-client.sh <game directory>
set -eu
mkdir -p "$1"
options="$1/options.txt"
touch "$options"
grep -v '^onboardAccessibility:\|^narrator:\|^narratorHotkey:\|^soundCategory_master:' "$options" > "$options.tmp" || true
printf 'onboardAccessibility:false\nnarrator:0\nnarratorHotkey:false\nsoundCategory_master:0.0\n' >> "$options.tmp"
mv "$options.tmp" "$options"
