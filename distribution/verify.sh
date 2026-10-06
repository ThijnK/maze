#!/bin/sh
# Run in a fresh Java 21 container with only the archive and this check mounted.
set -eu
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "Usage: sh distribution/verify.sh path/to/maze-linux-architecture.tar.gz" >&2
    exit 2
fi
archive=$(realpath "$1")
check_source=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/ReleaseCheck.java
work=$(mktemp -d '/tmp/maze release check.XXXXXX')
trap 'rm -rf "$work"' EXIT HUP INT TERM
cd "$work"
unset LD_LIBRARY_PATH
if [ -e /opt/z3 ]; then
    echo "Use a fresh Java container without the development image's Z3 installation." >&2
    exit 1
fi
tar -xzf "$archive"
set -- "$work"/maze-*
if [ "$#" -ne 1 ] || [ ! -x "$1/maze" ]; then
    echo "Expected one MAZE distribution in the archive." >&2
    exit 1
fi
home_dir=$1
"$home_dir/maze" --help > /dev/null
package_name=$(basename "$home_dir")
expected_version=${package_name#maze-}
expected_version=${expected_version%-linux-*}
actual_version=$("$home_dir/maze" --version)
if [ "$actual_version" != "maze $expected_version" ]; then
    echo "Package version mismatch: expected maze $expected_version, got $actual_version" >&2
    exit 1
fi
javac -cp "$home_dir/maze.jar" -d . "$check_source"
java -cp "$home_dir/maze.jar:." ReleaseCheck "$home_dir"
