#!/bin/sh
# Run in MAZE's pinned development container after mvn clean verify.
set -eu
cd "$(dirname "$0")/.."
case "$(uname -s):$(uname -m)" in
    Linux:aarch64|Linux:arm64) architecture=arm64 ;;
    Linux:x86_64) architecture=amd64 ;;
    *) echo "Build packages in the Linux development container." >&2; exit 1 ;;
esac
set -- target/maze-*-jar-with-dependencies.jar
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "Expected one packaged MAZE JAR; run mvn clean verify first." >&2
    exit 1
fi
maze_jar=$1
name=$(basename "$maze_jar" -jar-with-dependencies.jar)-linux-$architecture
stage=$(mktemp -d target/maze-distribution.XXXXXX)
trap 'rm -rf "$stage"' EXIT HUP INT TERM
mkdir -p "$stage/$name/lib" "$stage/$name/docs" "$stage/$name/examples"
cp "$maze_jar" "$stage/$name/maze.jar"
cp /opt/z3/bin/libz3.so /opt/z3/bin/libz3java.so "$stage/$name/lib/"
cp /opt/z3/LICENSE.txt "$stage/$name/lib/Z3-LICENSE.txt"
cp LICENSE "$stage/$name/"
cp distribution/maze distribution/README.md "$stage/$name/"
chmod 755 "$stage/$name/maze"
printf '%s\n' "$architecture" > "$stage/$name/architecture"
# Keep source-build links usable when the guide is read outside a checkout.
sed -e 's|(../README.md#|(https://github.com/ThijnK/maze#|g' \
    -e 's|(development-guide.md|(https://github.com/ThijnK/maze/blob/main/docs/development-guide.md|g' \
    docs/search-extensions.md > "$stage/$name/docs/search-extensions.md"
cp -R examples/search-extensions "$stage/$name/examples/"
# Refuse an artifact compiled against a different Z3 binding than the bundled natives.
mkdir "$stage/binding" "$stage/packaged-binding"
(cd "$stage/binding" && jar xf /opt/z3/bin/com.microsoft.z3.jar com/microsoft/z3)
(cd "$stage/packaged-binding" && jar xf "../../$(basename "$maze_jar")" com/microsoft/z3)
diff -qr "$stage/binding" "$stage/packaged-binding"
mkdir -p target/distributions
tar -czf "target/distributions/$name.tar.gz" -C "$stage" "$name"
(cd target/distributions && sha256sum "$name.tar.gz" > "$name.tar.gz.sha256")
printf 'Built target/distributions/%s.tar.gz\n' "$name"
