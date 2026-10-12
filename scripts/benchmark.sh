#!/bin/sh
# Builds a large base on a development server and measures tick times, idle and under load.
# Usage: scripts/benchmark.sh [scenario:]rows x chests per row x inputs ...
#   scripts/benchmark.sh 20x20x4 mixed:20x20x4 chunks:40x40x8
# Scenarios: sorting (the default) is a sorting hall, mixed adds orders, crafting and suppliers,
# chunks lets half of the hall unload and load again all the time.
# Results are printed and kept in build/benchmark. With PROFILE=1 a flight recording is written next to them.
# The server runs in run-benchmark, this writes eula=true there, so only run it if you accept the Minecraft EULA.
set -eu
cd "$(dirname "$0")/.."
mkdir -p run-benchmark build/benchmark
echo "eula=true" > run-benchmark/eula.txt
printf 'server-port=25598\nonline-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n' > run-benchmark/server.properties
for run in "${@:-20x20x4}"; do
    case "$run" in
        *:*) scenario=${run%%:*}; size=${run#*:} ;;
        *) scenario=sorting; size=$run ;;
    esac
    rm -rf run-benchmark/world build/benchmark/result.txt
    ./gradlew --console=plain -q runBenchmark -PbenchmarkSize="$size" -PbenchmarkScenario="$scenario" \
        ${PROFILE:+-PbenchmarkProfile} > "build/benchmark/$scenario-$size.log" 2>&1 || true
    if [ -f build/benchmark/result.txt ]; then
        cp build/benchmark/result.txt "build/benchmark/$scenario-$size.txt"
        cat build/benchmark/result.txt
    else
        echo "FAIL $run: no result, see build/benchmark/$scenario-$size.log"
    fi
done
