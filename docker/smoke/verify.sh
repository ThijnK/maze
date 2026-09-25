#!/bin/sh
set -eu

cd "$(dirname "$0")/../.."

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "Usage: sh docker/smoke/verify.sh path/to/maze-jar-with-dependencies.jar" >&2
    exit 2
fi
maze_jar=$1
smoke_project=docker/smoke

# Cleaning the fixture prevents stale generated tests from satisfying this check.
mvn --batch-mode --no-transfer-progress -f "$smoke_project/pom.xml" clean compile

for mode in symbolic concrete; do
    concrete=false
    if [ "$mode" = concrete ]; then
        concrete=true
    fi

    mkdir -p "$smoke_project/target/generated-tests/$mode"
    java -jar "$maze_jar" \
        --classpath "$smoke_project/target/classes" \
        --class-name example.SmokeSubject \
        --output-path "$smoke_project/target/generated-tests/$mode" \
        --strategy BFS --time-budget 10 --concrete-driven="$concrete" \
        --minimization=true

    mvn --batch-mode --no-transfer-progress -f "$smoke_project/pom.xml" \
        -Dsmoke.mode="$mode" test
done
