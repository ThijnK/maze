#!/bin/sh
set -eu

# The existing MAZE tests write generated files here without creating the directory.
mkdir -p /workspace/tmp

exec /usr/local/bin/mvn-entrypoint.sh "$@"
