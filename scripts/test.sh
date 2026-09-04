#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
"$ROOT/scripts/compile.sh"
mkdir -p "$ROOT/build/test"
find "$ROOT/src/test/java" -name '*.java' -print | sort | xargs javac -cp "$ROOT/build/main" -d "$ROOT/build/test"
java -cp "$ROOT/build/main:$ROOT/build/test" legalmail.service.LegalDeliveryDecisionTest
