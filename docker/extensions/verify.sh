#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "Usage: sh docker/extensions/verify.sh path/to/maze-jar-with-dependencies.jar" >&2
    exit 2
fi
# Only packaged MAZE, example sources/config, and validation sources enter this directory.
check_dir=$(mktemp -d /tmp/maze-extension-check.XXXXXX)
cp "$1" "$check_dir/maze.jar"
cp -R examples/search-extensions/. "$check_dir/"
cp docker/extensions/*Check.java "$check_dir/"
cp docker/smoke/pom.xml "$check_dir/pom.xml"
cd "$check_dir"
mkdir -p extension-classes target/classes
javac -cp maze.jar -d extension-classes src/research/*.java
jar --create --file research.jar -C extension-classes .
javac -d target/classes subject/example/*.java
javac -cp maze.jar *Check.java
java -cp maze.jar:. ArtifactCheck
java -cp maze.jar:. CliArtifactCheck
# Run, not merely compile, every generated suite from successful experiments.
for suite in target/generated-tests/*; do
    mvn -B -ntp -Dsmoke.mode="${suite##*/}" test
done
echo "Independent extension artifact check passed: $check_dir"
