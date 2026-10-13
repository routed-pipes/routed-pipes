#!/bin/sh
# Writes the play pack in packwiz form into the given directory, the repository launchers and the server sync from.
# Usage: scripts/export-packwiz.sh <directory>
set -eu
target=$(realpath "$1")
cd "$(dirname "$0")/.."
./gradlew --console=plain -q build -x check copyPackCheckMods
python3 -I scripts/export_packwiz.py "$target"
