#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# compare-outputs.sh — Two-way parity verification
#
# Verifies that Java CLI and Node.js API produce identical outputs
# for all 5 test inputs.
#
# Prerequisites:
#   - Java CLI jar built: java-order-system/target/*.jar
#   - Node.js API running: http://localhost:3000
#   - jq installed: brew install jq
#
# Usage:
#   ./compare-outputs.sh
#   ./compare-outputs.sh test-inputs/order1_happy_path.json  # single file
# ---------------------------------------------------------------------------

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POC_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

JAVA_JAR="$POC_DIR/java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar"
NODE_API="http://localhost:3000/api/orders"
TEST_INPUTS="$POC_DIR/java-order-system/test-inputs"

PASS=0
FAIL=0

if [ -t 1 ]; then
    GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
else
    GREEN=''; RED=''; YELLOW=''; NC=''
fi

pass() { echo -e "${GREEN}  PASS${NC}: $*"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}  FAIL${NC}: $*"; FAIL=$((FAIL+1)); }

# Fields to compare (exclude processedAt which is timestamp-based)
COMPARE_FILTER='del(.processedAt) | {
  status,
  grandTotal,
  subtotal,
  totalTax,
  promoDiscount,
  shippingCost,
  promoCode,
  fraudFlagCount: (.fraudFlags | length),
  fraudFlagNames: [.fraudFlags[].flag] | sort,
  errorCount: (.errors | length),
  errorMessages: (.errors | sort)
}'

compare_file() {
    local input="$1"
    local label
    label=$(basename "$input")

    echo ""
    echo -e "${YELLOW}--- $label ---${NC}"

    # Generate CLI output
    local cli_out node_api_out

    if ! cli_out=$(java -jar "$JAVA_JAR" "$input" 2>/dev/null | jq "$COMPARE_FILTER"); then
        fail "$label: Java CLI failed to produce output"
        return
    fi

    if ! node_api_out=$(curl -s -X POST "$NODE_API" \
        -H "Content-Type: application/json" -d @"$input" 2>/dev/null | jq "$COMPARE_FILTER" 2>/dev/null); then
        fail "$label: Node.js API failed (is it running on port 3000?)"
        return
    fi

    # Compare CLI vs Node.js API
    if echo "$cli_out" "$node_api_out" | jq -s '.[0] == .[1]' | grep -q true; then
        pass "$label: CLI == Node.js API"
    else
        fail "$label: CLI != Node.js API"
        echo "  CLI output:"
        echo "$cli_out" | jq . | sed 's/^/    /'
        echo "  Node.js API output:"
        echo "$node_api_out" | jq . | sed 's/^/    /'
        echo "  Diff:"
        diff <(echo "$cli_out" | jq -S .) <(echo "$node_api_out" | jq -S .) | sed 's/^/    /'
    fi
}

echo ""
echo "==========================================="
echo " Two-Way Parity Verification"
echo "==========================================="
echo " Java CLI:    $JAVA_JAR"
echo " Node.js API: $NODE_API"
echo "==========================================="

# Run single file or all test inputs
if [ -n "${1:-}" ]; then
    compare_file "$1"
else
    for f in "$TEST_INPUTS"/order*.json; do
        compare_file "$f"
    done
fi

echo ""
echo "==========================================="
echo -e " Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
echo "==========================================="
echo ""

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
