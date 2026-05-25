#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# validate.sh — run tests and validate all expected outputs
#
# Checks:
#   1. Maven unit tests pass
#   2. Each test-input produces output matching the key fields in expected-outputs/
#
# Exit code: 0 = all passed, 1 = failures detected
# ---------------------------------------------------------------------------

set -euo pipefail

JAR="target/order-processing-system-1.0.0-jar-with-dependencies.jar"
PASS=0
FAIL=0

# Color helpers (disabled if no TTY)
if [ -t 1 ]; then
    GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
else
    GREEN=''; RED=''; YELLOW=''; NC=''
fi

pass() { echo -e "${GREEN}  PASS${NC}: $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}  FAIL${NC}: $1"; FAIL=$((FAIL+1)); }

echo ""
echo "==========================================="
echo " Validation Suite — Order Processing System"
echo "==========================================="

# -----------------------------------------------------------------------
# Step 1: Maven tests
# -----------------------------------------------------------------------
echo ""
echo -e "${YELLOW}[1/2] Running Maven unit tests...${NC}"
if mvn test -q 2>&1; then
    pass "Maven unit tests"
else
    fail "Maven unit tests — check output above"
fi

# -----------------------------------------------------------------------
# Step 2: Build
# -----------------------------------------------------------------------
if [ ! -f "$JAR" ]; then
    echo ""
    echo -e "${YELLOW}Building JAR...${NC}"
    mvn package -q
fi

# -----------------------------------------------------------------------
# Step 3: CLI integration checks
# -----------------------------------------------------------------------
echo ""
echo -e "${YELLOW}[2/2] Running CLI integration checks...${NC}"

check_field() {
    local label="$1"
    local input="$2"
    local field="$3"
    local expected="$4"

    local actual
    actual=$(java -jar "$JAR" "$input" 2>/dev/null | python3 -c "
import json, sys
data = json.load(sys.stdin)
keys = '$field'.split('.')
val = data
for k in keys:
    val = val[k]
print(val)
" 2>/dev/null || echo "ERROR")

    if [ "$actual" = "$expected" ]; then
        pass "$label: $field = $expected"
    else
        fail "$label: $field expected='$expected' actual='$actual'"
    fi
}

check_contains() {
    local label="$1"
    local input="$2"
    local substring="$3"

    local output
    output=$(java -jar "$JAR" "$input" 2>/dev/null)

    if echo "$output" | grep -q "$substring"; then
        pass "$label: output contains '$substring'"
    else
        fail "$label: output does NOT contain '$substring'"
    fi
}

# Order 1 — Happy path
echo ""
echo "  Order 1: Happy path (Gold + SAVE10)"
check_field "ORD-001" "test-inputs/order1_happy_path.json"     "status"        "PROCESSED"
check_field "ORD-001" "test-inputs/order1_happy_path.json"     "grandTotal"    "1218.14"
check_field "ORD-001" "test-inputs/order1_happy_path.json"     "subtotal"      "1120.5"
check_field "ORD-001" "test-inputs/order1_happy_path.json"     "promoDiscount" "112.05"
check_field "ORD-001" "test-inputs/order1_happy_path.json"     "shippingCost"  "8.0"

# Order 2 — Fraud rejection
echo ""
echo "  Order 2: Fraud rejection (HIGH_VALUE_NEW_CUSTOMER)"
check_field "ORD-002" "test-inputs/order2_fraud_rejection.json" "status"       "REJECTED"
check_contains "ORD-002" "test-inputs/order2_fraud_rejection.json" "HIGH_VALUE_NEW_CUSTOMER"

# Order 3 — Inventory shortage
echo ""
echo "  Order 3: Inventory shortage"
check_field "ORD-003" "test-inputs/order3_inventory_shortage.json" "status"    "REJECTED"
check_contains "ORD-003" "test-inputs/order3_inventory_shortage.json" "P007"

# Order 4 — Platinum + FREESHIP
echo ""
echo "  Order 4: Platinum + FREESHIP"
check_field "ORD-004" "test-inputs/order4_platinum_freeship.json" "status"     "PROCESSED"
check_field "ORD-004" "test-inputs/order4_platinum_freeship.json" "shippingCost" "0.0"
check_field "ORD-004" "test-inputs/order4_platinum_freeship.json" "grandTotal"  "819.42"

# Order 5 — Multiple fraud warnings
echo ""
echo "  Order 5: Multiple fraud warnings (PROCESSED)"
check_field "ORD-005" "test-inputs/order5_fraud_warnings.json" "status"        "PROCESSED"
check_field "ORD-005" "test-inputs/order5_fraud_warnings.json" "grandTotal"    "672.27"
check_contains "ORD-005" "test-inputs/order5_fraud_warnings.json" "PROMO_ABUSE"
check_contains "ORD-005" "test-inputs/order5_fraud_warnings.json" "SUSPICIOUS_ZONE"
check_contains "ORD-005" "test-inputs/order5_fraud_warnings.json" "BULK_PURCHASE"

# -----------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------
echo ""
echo "==========================================="
echo -e " Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
echo "==========================================="
echo ""

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
