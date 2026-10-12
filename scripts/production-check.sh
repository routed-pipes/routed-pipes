#!/bin/sh
# Checks the built jar the way a pack runs it: on a plain NeoForge server, outside the development environment,
# together with the mods of the compatibility run. Scenes are built and checked with console commands.
# Needs Java 21, set JAVA_HOME if the default java is another version.
# The server lives in build/production-server. This writes eula=true there, so only run it if you accept the Minecraft EULA.
set -eu
cd "$(dirname "$0")/.."
if [ -n "${JAVA_HOME:-}" ]; then
    PATH="$JAVA_HOME/bin:$PATH"
fi
neo_version=$(sed -n 's/^neo_version=//p' gradle.properties)
mod_version=$(sed -n 's/^mod_version=//p' gradle.properties)
server=build/production-server
./gradlew --console=plain -q build -x check copyCompatMods
mkdir -p "$server/mods"
if [ ! -f "$server/run.sh" ]; then
    curl -sSf -o "$server/installer.jar" \
        "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neo_version/neoforge-$neo_version-installer.jar"
    (cd "$server" && java -jar installer.jar --installServer . > install.log 2>&1)
fi
rm -rf "$server/world" "$server"/mods/*.jar
cp "build/libs/logisticspipes-$mod_version.jar" run-compat/mods/*.jar "$server/mods/"
echo "eula=true" > "$server/eula.txt"
printf 'server-port=25599\nonline-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n' > "$server/server.properties"
echo "-Xmx3G" > "$server/user_jvm_args.txt"
cd "$server"
python3 -I ../../scripts/production_check.py
