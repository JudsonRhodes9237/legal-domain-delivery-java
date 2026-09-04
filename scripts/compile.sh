#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD="$ROOT/build"
mkdir -p "$BUILD/main"
find "$ROOT/src/main/java" -name '*.java' -print | sort | xargs javac -d "$BUILD/main"
echo "compiled main sources"
