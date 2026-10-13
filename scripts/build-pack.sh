#!/bin/sh
# Builds the play pack as a file a launcher can import: build/pack/routed-pipes-pack-<version>.mrpack
# The other mods are downloaded by the launcher from Modrinth, the jar of this mod is inside the file.
set -eu
cd "$(dirname "$0")/.."
./gradlew --console=plain -q build -x check
python3 -I scripts/build_pack.py
