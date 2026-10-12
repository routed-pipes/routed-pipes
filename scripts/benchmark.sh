#!/bin/sh
# Builds a large sorting base on a development server and measures tick times, idle and under load.
# Usage: scripts/benchmark.sh [rows x chests per row x inputs ...], for example scripts/benchmark.sh 20x20x4 40x40x8
# Results are printed and kept in build/benchmark. With PROFILE=1 a flight recording is written next to them.
# The server runs in run-benchmark, this writes eula=true there, so only run it if you accept the Minecraft EULA.
set -eu
cd "$(dirname "$0")/.."
mkdir -p run-benchmark build/benchmark
echo "eula=true" > run-benchmark/eula.txt
printf 'server-port=25598\nonline-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n' > run-benchmark/server.properties
for size in "${@:-20x20x4}"; do
    rm -rf run-benchmark/world build/benchmark/result.txt
    ./gradlew --console=plain -q runBenchmark -PbenchmarkSize="$size" ${PROFILE:+-PbenchmarkProfile} > "build/benchmark/$size.log" 2>&1 || true
    if [ -f build/benchmark/result.txt ]; then
        cp build/benchmark/result.txt "build/benchmark/$size.txt"
        cat build/benchmark/result.txt
    else
        echo "FAIL $size: no result, see build/benchmark/$size.log"
    fi
done
