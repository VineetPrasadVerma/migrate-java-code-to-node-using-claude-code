# Order Processing System — Java Legacy Application

A medium-complexity order processing system used as an interview assignment artifact.
Candidates must analyze, document, and migrate this codebase to Node.js using AI-assisted development tools.

---

## Prerequisites

- Java 11+
- Maven 3.6+

```bash
# macOS (Homebrew)
brew install java@11 maven

# Verify
java -version   # should print 11.x
mvn -version    # should print 3.x
```

---

## Project Structure

```
java-order-system/
├── pom.xml
├── run.sh                        # build + run all test cases
├── validate.sh                   # run unit tests + CLI integration checks
├── test-inputs/                  # 5 JSON test scenarios
│   ├── order1_happy_path.json
│   ├── order2_fraud_rejection.json
│   ├── order3_inventory_shortage.json
│   ├── order4_platinum_freeship.json
│   └── order5_fraud_warnings.json
├── expected-outputs/             # expected JSON fields for each test
└── src/
    ├── main/java/com/company/orders/
    │   ├── Main.java                          # CLI entry point
    │   ├── model/                             # domain objects + enums
    │   │   ├── CustomerTier.java              # BRONZE | SILVER | GOLD | PLATINUM
    │   │   ├── ProductCategory.java           # ELECTRONICS | CLOTHING | FOOD | MEDICAL | GENERAL
    │   │   ├── OrderStatus.java               # state machine states
    │   │   ├── ShippingType.java              # STANDARD | EXPRESS | OVERNIGHT
    │   │   ├── FraudFlag.java                 # fraud rule enum with severity
    │   │   ├── Customer.java                  # ⚠ LEGACY: registrationDate as String
    │   │   ├── Product.java                   # ⚠ LEGACY: legacyCategoryCode string field
    │   │   ├── Order.java                     # incoming request
    │   │   ├── OrderItem.java
    │   │   ├── PromoCode.java
    │   │   ├── ProcessedOrderItem.java        # per-line pricing output
    │   │   ├── PromotionResult.java
    │   │   └── OrderResult.java               # complete pipeline output
    │   ├── repository/                        # in-memory data stores
    │   │   ├── CustomerRepository.java        # ⚠ LEGACY: fromLegacyCode() int-to-enum mapper
    │   │   ├── ProductRepository.java         # includes LEGACY_CODE_MAP
    │   │   ├── PromoCodeRepository.java
    │   │   └── OrderRepository.java           # ⚠ LEGACY: synchronized HashMap
    │   ├── service/                           # business logic
    │   │   ├── ValidationService.java         # structural + referential validation
    │   │   ├── PricingEngine.java             # tier + volume discounts + tax
    │   │   ├── PromotionEngine.java           # promo code application
    │   │   ├── ShippingCalculator.java        # zone + weight + tier pricing
    │   │   ├── InventoryService.java          # stock check + reservation
    │   │   ├── FraudDetectionService.java     # ⚠ LEGACY: procedural if-else rules
    │   │   └── OrderProcessingService.java    # pipeline orchestrator
    │   └── util/
    │       ├── MoneyUtils.java                # ⚠ LEGACY: double arithmetic (not BigDecimal)
    │       └── JsonUtils.java
    └── test/java/com/company/orders/
        └── OrderProcessingTest.java           # 8 integration-style tests
```

---

## Build & Run

### Step 1 — Install Java & Maven (macOS)

```bash
brew install openjdk@11 maven

# Add Java 11 to your PATH for the current session
export PATH="/opt/homebrew/opt/openjdk@11/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@11"

# Verify
java -version   # should print openjdk 11.x
mvn -version    # should print Apache Maven 3.x
```

> **Tip**: Add the two `export` lines to your `~/.zshrc` so you don't have to repeat them each session.

---

### Step 2 — Build the JAR

```bash
cd java-order-system

mvn package -q
```

This produces `target/order-processing-system-1.0.0-jar-with-dependencies.jar` (a fat JAR with all dependencies bundled).

---

### Step 3 — Run the App

**Option A — Run a specific test input file:**
```bash
java -jar target/order-processing-system-1.0.0-jar-with-dependencies.jar test-inputs/order1_happy_path.json
```

**Option B — Run all 5 test cases at once:**
```bash
./run.sh
```

**Option C — Pipe JSON directly (stdin):**
```bash
echo '{
  "orderId": "MY-001",
  "customerId": "C002",
  "items": [{"productId": "P001", "quantity": 1}],
  "shippingZone": 1,
  "shippingType": "STANDARD"
}' | java -jar target/order-processing-system-1.0.0-jar-with-dependencies.jar
```

**Option D — Pretty-print output with `jq`:**
```bash
java -jar target/order-processing-system-1.0.0-jar-with-dependencies.jar \
  test-inputs/order4_platinum_freeship.json | jq .
```

---

### Step 4 — Run Tests & Validation

**Unit tests only (JUnit):**
```bash
mvn test
```
Expected: `Tests run: 8, Failures: 0, Errors: 0`

**Full validation suite (unit tests + all 5 CLI checks):**
```bash
./validate.sh
```
Expected: `Results: 18 passed, 0 failed`

> `validate.sh` requires `python3` to be installed (`python3 --version` to check).

---

## Test Data Reference

### Customers

| ID   | Name           | Tier     | Registered   | Notes               |
|------|----------------|----------|--------------|---------------------|
| C001 | Alice Johnson  | PLATINUM | 2023-01-15   | Established         |
| C002 | Bob Smith      | GOLD     | 2023-06-20   | Established         |
| C003 | Carol Davis    | SILVER   | 2024-08-10   | Established         |
| C004 | David Wilson   | BRONZE   | 2025-11-01   | ~200 days old       |
| C005 | Eve Martinez   | BRONZE   | 2026-04-25   | ~25 days (NEW)      |

### Products

| ID   | Name                    | Category    | Price    | Weight  | Stock | Notes          |
|------|-------------------------|-------------|----------|---------|-------|----------------|
| P001 | Laptop Pro              | ELECTRONICS | $1200.00 | 2.5 kg  | 50    |                |
| P002 | USB Cable               | ELECTRONICS | $15.00   | 0.1 kg  | 200   |                |
| P003 | Office Chair            | GENERAL     | $350.00  | 15.0 kg | 10    |                |
| P004 | Blue Jeans              | CLOTHING    | $60.00   | 0.5 kg  | 100   |                |
| P005 | Protein Shake           | FOOD        | $45.00   | 0.8 kg  | 5     | Low stock      |
| P006 | Blood Pressure Monitor  | MEDICAL     | $120.00  | 0.3 kg  | 30    |                |
| P007 | Wireless Headphones     | ELECTRONICS | $200.00  | 0.3 kg  | 0     | OUT OF STOCK   |
| P008 | Running Shoes           | CLOTHING    | $90.00   | 0.7 kg  | 75    |                |
| P009 | Vitamin C Supplements   | MEDICAL     | $25.00   | 0.2 kg  | 150   |                |
| P010 | Lithium Battery Pack    | ELECTRONICS | $180.00  | 1.2 kg  | 20    | DANGEROUS GOOD |

### Promo Codes

| Code     | Type               | Value | Condition         | Restriction         | Expires    |
|----------|--------------------|-------|-------------------|---------------------|------------|
| SAVE10   | PERCENTAGE_SUBTOTAL| 10%   | none              | All tiers           | 2026-12-31 |
| SAVE20   | PERCENTAGE_SUBTOTAL| 20%   | none              | All tiers           | 2026-06-30 |
| FREESHIP | FREE_SHIPPING      | -     | none              | All tiers           | 2026-12-31 |
| BULK15   | PERCENTAGE_IF_ABOVE| 15%   | subtotal >= $200  | All tiers           | 2026-12-31 |
| NEWCUST  | PERCENTAGE_SUBTOTAL| 8%    | account < 30 days | Bronze only         | 2026-12-31 |
| GOLD20   | PERCENTAGE_SUBTOTAL| 20%   | none              | Gold/Platinum only  | 2026-12-31 |
| EXPIRED  | PERCENTAGE_SUBTOTAL| 5%    | -                 | All tiers           | 2026-01-01 |

---

## Business Rules Summary

### Pricing
- **Tier discounts**: BRONZE 0% · SILVER 5% · GOLD 10% · PLATINUM 15%
- **Volume discounts** (additive with tier): qty 1-4: 0% · qty 5-9: 3% · qty 10-19: 7% · qty 20+: 12%
- **Discount cap**: combined discount cannot exceed 40%
- **Tax rates**: ELECTRONICS 18% · CLOTHING 5% · FOOD 0% · MEDICAL 12% · GENERAL 8%
- **Tax is computed on item-level discounted prices, BEFORE promo code deduction**
- **Grand total formula**: `subtotal - promoDiscount + totalTax + shippingCost`

### Shipping
- **Zone base rates**: Zone 1 $5 · Zone 2 $8 · Zone 3 $12 · Zone 4 $18 · Zone 5 $25
- **Speed multipliers**: STANDARD 1.0x · EXPRESS 1.5x · OVERNIGHT 2.5x
- **Weight surcharge**: $0.50/kg over 5 kg
- **Dangerous goods**: +$15 flat surcharge (never waived)
- **Free shipping**: PLATINUM + STANDARD (no dangerous goods) OR FREESHIP promo

### Fraud Detection
| Rule                  | Severity | Trigger                                                        | Outcome           |
|-----------------------|----------|----------------------------------------------------------------|-------------------|
| HIGH_VALUE_NEW_CUSTOMER | HIGH   | total > $1000 AND account < 90 days                           | REJECTED          |
| PROMO_ABUSE           | MEDIUM   | promo used AND total > $500 AND account < 30 days              | PROCESSED+warning |
| SUSPICIOUS_ZONE       | MEDIUM   | zone >= 4 AND BRONZE AND total > $300                         | PROCESSED+warning |
| BULK_PURCHASE         | LOW      | any item qty >= 10 AND BRONZE tier                            | PROCESSED+note    |

---

## Test Scenarios & Expected Outputs

### Order 1 — Happy Path
- **Customer**: C002 (Gold), **Promo**: SAVE10, **Zone**: 2 STANDARD
- **Expected**: `status=PROCESSED`, `grandTotal=1218.14`, no fraud flags

### Order 2 — Fraud Rejection
- **Customer**: C005 (Bronze, 25 days old), 2× Laptop Pro (~$2837 total)
- **Expected**: `status=REJECTED`, `HIGH_VALUE_NEW_CUSTOMER` flag

### Order 3 — Inventory Shortage
- **Customer**: C001, includes P007 (out of stock), `allowPartialFulfillment=false`
- **Expected**: `status=REJECTED`, error mentions P007

### Order 4 — Platinum + FREESHIP + Mixed Categories
- **Customer**: C001 (Platinum), **Promo**: FREESHIP, **Zone**: 3 EXPRESS
- **Expected**: `status=PROCESSED`, `shippingCost=0.00`, `grandTotal=819.42`

### Order 5 — Multiple Fraud Warnings (Still Processes)
- **Customer**: C005 (Bronze, 25 days), **Promo**: SAVE10, qty=10 Blue Jeans, **Zone**: 4
- **Expected**: `status=PROCESSED`, 3 fraud flags (MEDIUM+MEDIUM+LOW), `grandTotal=672.27`

---

## Known Legacy Issues (for candidate reference)

These are intentional legacy patterns that candidates should document and account for during migration:

1. **String dates**: `Customer.registrationDate` is a `String ("YYYY-MM-DD")` instead of `LocalDate`
2. **Mixed category representations**: Products have both `ProductCategory` enum AND `legacyCategoryCode` String
3. **Procedural fraud rules**: `FraudDetectionService` uses if-else chains instead of a strategy pattern
4. **Double arithmetic**: `MoneyUtils` uses `double` + rounding instead of `BigDecimal`
5. **Static synchronized HashMap**: `OrderRepository` uses `Collections.synchronizedMap(new HashMap<>())` — not suitable for multi-JVM
6. **No dependency injection**: Services are manually wired in `Main.java`
7. **Mutable stock**: `ProductRepository.decrementStock()` modifies in-memory state directly
8. **Missing customerId on OrderResult**: `OrderRepository.findByCustomer()` relies on orderId prefix convention
