#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run.sh — build and run the Order Processing System
#
# Usage:
#   ./run.sh                          # run all 5 test inputs
#   ./run.sh test-inputs/order1.json  # run a specific input file
#   ./run.sh --build-only             # build without running
# ---------------------------------------------------------------------------

set -euo pipefail

JAR="target/order-processing-system-1.0.0-jar-with-dependencies.jar"

# Build if jar doesn't exist or --build-only requested
if [ ! -f "$JAR" ] || [ "${1:-}" = "--build-only" ]; then
    echo "==> Building..."
    mvn package -q
    echo "==> Build complete: $JAR"
    [ "${1:-}" = "--build-only" ] && exit 0
fi

# Run specific file if provided
if [ -n "${1:-}" ] && [ "$1" != "--build-only" ]; then
    echo "==> Processing: $1"
    java -jar "$JAR" "$1"
    exit 0
fi

# Run all test inputs
echo ""
echo "==========================================="
echo " Order Processing System — All Test Cases"
echo "==========================================="

for f in test-inputs/order*.json; do
    echo ""
    echo "--- $f ---"
    java -jar "$JAR" "$f"
done
