# Test Cases — Arithmetic Traces

> Five canonical test scenarios from `java-order-system/test-inputs/` and `expected-outputs/`.  
> Each trace shows the exact calculation chain that produces every number in the expected output.  
> All rounding is HALF_UP 2dp via `Math.round(x * 100) / 100`.

---

## Reference Data Used Across All Cases

### Customers
| ID | Tier | Registered | Age on 2026-05-20 |
|----|------|-----------|-------------------|
| C001 | PLATINUM | 2023-01-15 | ~1,220 days (established) |
| C002 | GOLD | 2023-06-20 | ~1,034 days (established) |
| C005 | BRONZE | 2026-04-25 | **25 days (NEW)** |

### Tier discount rates
| Tier | Rate |
|------|------|
| BRONZE | 0% |
| GOLD | 10% |
| PLATINUM | 15% |

### Volume brackets
| Qty | Rate |
|-----|------|
| 1–4 | 0% |
| 5–9 | 3% |
| 10–19 | 7% |
| 20+ | 12% |

### Tax rates
| Category | Rate |
|----------|------|
| ELECTRONICS | 18% |
| CLOTHING | 5% |
| MEDICAL | 12% |

### Zone base rates
| Zone | Base |
|------|------|
| 1 | $5.00 |
| 2 | $8.00 |
| 3 | $12.00 |
| 4 | $18.00 |

### Speed multipliers: STANDARD 1.0× · EXPRESS 1.5× · OVERNIGHT 2.5×
### Weight surcharge: $0.50/kg over 5.0 kg threshold

---

## Order 1 — Happy Path (`order1_happy_path.json`)

**Input:** Customer C002 (GOLD), items P001×1 + P002×3, promo SAVE10, zone 2 STANDARD  
**Expected:** `status=PROCESSED`, `grandTotal=1218.14`, no fraud flags

---

### Stage 1 — Validation
All checks pass: orderId, customerId, products exist, qty in range, zone=2 ∈ [1–5], shippingType non-null, SAVE10 exists.

### Stage 2 — Entity Resolution
- Customer: C002, tier=GOLD → `tierDiscountRate = 0.10`

### Stage 3 — Pricing

**P001 — Laptop Pro (ELECTRONICS, basePrice=$1200.00, weight=2.5kg)**
```
tierDiscount   = 0.10  (GOLD)
volumeDiscount = 0.00  (qty=1, bracket [1,4])
combined       = min(0.10 + 0.00, 0.40) = 0.10

discountedUnitPrice = round(1200.00 × (1 − 0.10))
                    = round(1200.00 × 0.90)
                    = round(1080.00) = 1080.00

lineSubtotal = round(1080.00 × 1) = 1080.00

taxRate   = 0.18  (ELECTRONICS)
taxAmount = round(1080.00 × 0.18)
          = round(194.40) = 194.40

lineTotal = round(1080.00 + 194.40) = 1274.40
```

**P002 — USB Cable (ELECTRONICS, basePrice=$15.00, weight=0.1kg)**
```
tierDiscount   = 0.10  (GOLD)
volumeDiscount = 0.00  (qty=3, bracket [1,4])
combined       = min(0.10 + 0.00, 0.40) = 0.10

discountedUnitPrice = round(15.00 × 0.90) = round(13.50) = 13.50

lineSubtotal = round(13.50 × 3) = round(40.50) = 40.50

taxRate   = 0.18  (ELECTRONICS)
taxAmount = round(40.50 × 0.18)
          = round(7.29) = 7.29

lineTotal = round(40.50 + 7.29) = 47.79
```

**Aggregates**
```
subtotal  = round(1080.00 + 40.50) = 1120.50
totalTax  = round(194.40 + 7.29)   = 201.69
```

### Stage 4 — Promotion (SAVE10)
```
Type: PERCENTAGE_SUBTOTAL
Expiry: 2026-12-31 > orderDate 2026-05-20 → valid
EligibleTiers: null (all tiers) → valid
newCustomerOnly: false → valid

promoDiscount = round(1120.50 × 10 / 100)
              = round(112.05) = 112.05
```

### Stage 5 — Shipping
```
Weight = (2.5 × 1) + (0.1 × 3) = 2.5 + 0.3 = 2.8 kg
No dangerous goods → dangerousSurcharge = $0.00
excessWeight = max(0, 2.8 − 5.0) = 0 → weightSurcharge = $0.00

freeShipping promo? No (SAVE10 is not FREE_SHIPPING)
platinumFreeStandard? No (C002 is GOLD)

→ Standard calculation:
baseRate        = ZONE_BASE_RATES[2] = $8.00
speedMultiplier = STANDARD → 1.0×
baseCharge      = round(8.00 × 1.0) = $8.00
shippingCost    = round(8.00 + 0.00 + 0.00) = $8.00
```

### Stage 6 — Grand Total
```
grandTotal = round(subtotal − promoDiscount + totalTax + shippingCost)
           = round(1120.50 − 112.05 + 201.69 + 8.00)
           = round(1218.14)
           = 1218.14  ✓
```

### Stage 7 — Inventory
P001 stock=50 ≥ 1 ✓ · P002 stock=200 ≥ 3 ✓ → reserved, no shortage.

### Stage 8 — Fraud Detection
```
accountAgeDays = DAYS(2023-06-20 → 2026-05-20) ≈ 1,034 days

HIGH_VALUE_NEW_CUSTOMER: 1218.14 > 1000 BUT 1034 ≮ 90 → NO FLAG
PROMO_ABUSE:             promoCode present AND 1218.14 > 500 BUT 1034 ≮ 30 → NO FLAG
SUSPICIOUS_ZONE:         zone=2 ≱ 4 → NO FLAG
BULK_PURCHASE:           tier=GOLD (not BRONZE) → NO FLAG
```

**Result: PROCESSED, no flags** ✓

---

### Summary Table — Order 1

| Field | Value | Derivation |
|-------|-------|-----------|
| `subtotal` | 1120.50 | 1080.00 + 40.50 |
| `totalTax` | 201.69 | 194.40 + 7.29 |
| `promoDiscount` | 112.05 | 10% × 1120.50 |
| `shippingCost` | 8.00 | zone 2, STANDARD, 2.8kg (no surcharges) |
| `grandTotal` | **1218.14** | 1120.50 − 112.05 + 201.69 + 8.00 |

---
---

## Order 2 — Fraud Rejection (`order2_fraud_rejection.json`)

**Input:** Customer C005 (BRONZE, 25 days old), items P001×2, no promo, zone 1 STANDARD  
**Expected:** `status=REJECTED`, `HIGH_VALUE_NEW_CUSTOMER` flag, `grandTotal=2837.00`

---

### Stage 1 — Validation
All checks pass: P001 exists, qty=2 ∈ [1,100], zone=1 ∈ [1–5].

### Stage 2 — Entity Resolution
- Customer: C005, tier=BRONZE → `tierDiscountRate = 0.00`

### Stage 3 — Pricing

**P001 — Laptop Pro (ELECTRONICS, basePrice=$1200.00, weight=2.5kg)**
```
tierDiscount   = 0.00  (BRONZE)
volumeDiscount = 0.00  (qty=2, bracket [1,4])
combined       = min(0.00 + 0.00, 0.40) = 0.00

discountedUnitPrice = round(1200.00 × (1 − 0.00)) = 1200.00

lineSubtotal = round(1200.00 × 2) = 2400.00

taxRate   = 0.18  (ELECTRONICS)
taxAmount = round(2400.00 × 0.18) = round(432.00) = 432.00

lineTotal = round(2400.00 + 432.00) = 2832.00
```

**Aggregates**
```
subtotal = 2400.00
totalTax = 432.00
```

### Stage 4 — Promotion
No promo code provided → `noPromo()`, `promoDiscount = 0.00`.

### Stage 5 — Shipping
```
Weight = 2.5 × 2 = 5.0 kg
No dangerous goods → dangerousSurcharge = $0.00
excessWeight = max(0, 5.0 − 5.0) = 0.0  ← threshold is ≤ 5.0; exactly 5.0 kg incurs no surcharge
weightSurcharge = $0.00

platinumFreeStandard? No (BRONZE, not PLATINUM)

baseRate        = ZONE_BASE_RATES[1] = $5.00
speedMultiplier = STANDARD → 1.0×
baseCharge      = round(5.00 × 1.0) = $5.00
shippingCost    = round(5.00 + 0.00 + 0.00) = $5.00
```

### Stage 6 — Grand Total
```
grandTotal = round(2400.00 − 0.00 + 432.00 + 5.00)
           = round(2837.00)
           = 2837.00  ✓
```

### Stage 7 — Inventory
P001 stock=50 ≥ 2 ✓ → reserved.

### Stage 8 — Fraud Detection
```
accountAgeDays = DAYS(2026-04-25 → 2026-05-20) = 25 days

HIGH_VALUE_NEW_CUSTOMER: 2837.00 > 1000.00 ✓ AND 25 < 90 ✓ → HIGH FLAG  ⚑
PROMO_ABUSE:             promoCode = null → skipped
SUSPICIOUS_ZONE:         zone=1 < 4 → NO FLAG
BULK_PURCHASE:           qty=2 < 10 → NO FLAG
```

`hasHighSeverityFlag()` = true → **REJECTED**  
`inventoryService.releaseReservation()` called → P001 stock restored to 50.

---

### Summary Table — Order 2

| Field | Value | Derivation |
|-------|-------|-----------|
| `subtotal` | 2400.00 | 1200.00 × 2, no discount (BRONZE) |
| `totalTax` | 432.00 | 18% × 2400.00 |
| `promoDiscount` | 0.00 | no promo |
| `shippingCost` | 5.00 | zone 1, STANDARD, exactly 5.0kg (no weight surcharge) |
| `grandTotal` | **2837.00** | 2400.00 − 0 + 432.00 + 5.00 |
| Fraud flag | HIGH_VALUE_NEW_CUSTOMER | 2837.00 > $1,000 AND account 25 days < 90-day threshold |
| `status` | **REJECTED** | HIGH severity flag triggers rejection + inventory release |

---
---

## Order 3 — Inventory Shortage (`order3_inventory_shortage.json`)

**Input:** Customer C001 (PLATINUM), items P007×1 + P002×5, no promo, zone 1 STANDARD, `allowPartialFulfillment=false`  
**Expected:** `status=REJECTED`, error `"Insufficient stock for P007 … available 0"`

---

### Stage 1 — Validation
All checks pass: P007 exists in catalog (stock=0 is not a validation concern, only an inventory concern), P002 exists, zone=1.

### Stage 2 — Entity Resolution
- Customer: C001, tier=PLATINUM → `tierDiscountRate = 0.15`

### Stage 3 — Pricing (runs before inventory check)

**P007 — Wireless Headphones (ELECTRONICS, basePrice=$200.00, weight=0.3kg)**
```
tierDiscount   = 0.15  (PLATINUM)
volumeDiscount = 0.00  (qty=1, bracket [1,4])
combined       = min(0.15, 0.40) = 0.15

discountedUnitPrice = round(200.00 × 0.85) = round(170.00) = 170.00
lineSubtotal        = round(170.00 × 1) = 170.00
taxAmount           = round(170.00 × 0.18) = round(30.60) = 30.60
lineTotal           = round(170.00 + 30.60) = 200.60
```

**P002 — USB Cable (ELECTRONICS, basePrice=$15.00, weight=0.1kg)**
```
tierDiscount   = 0.15  (PLATINUM)
volumeDiscount = 0.03  (qty=5, bracket [5,9])
combined       = min(0.15 + 0.03, 0.40) = 0.18

discountedUnitPrice = round(15.00 × (1 − 0.18)) = round(15.00 × 0.82) = round(12.30) = 12.30
lineSubtotal        = round(12.30 × 5) = round(61.50) = 61.50
taxAmount           = round(61.50 × 0.18) = round(11.07) = 11.07
lineTotal           = round(61.50 + 11.07) = 72.57
```

**Aggregates**
```
subtotal = round(170.00 + 61.50) = 231.50
totalTax = round(30.60 + 11.07) = 41.67
```

### Stage 4 — Promotion
No promo code → `promoDiscount = 0.00`.

### Stage 5 — Shipping
```
Weight = (0.3 × 1) + (0.1 × 5) = 0.3 + 0.5 = 0.8 kg
No dangerous goods → dangerousSurcharge = $0.00
weightSurcharge = $0.00  (0.8 < 5.0)

platinumFreeStandard: tier=PLATINUM ✓, type=STANDARD ✓, no dangerous goods ✓ → FREE
→ shippingCost = round(0.00) = $0.00
```

### Stage 6 — Grand Total
```
grandTotal = round(231.50 − 0.00 + 41.67 + 0.00) = round(273.17) = 273.17
```
*(Computed for inclusion in the result, even though the order will be rejected next.)*

### Stage 7 — Inventory Check ← REJECTION POINT
```
P007 requested qty=1, stock=0 → INSUFFICIENT
P002 requested qty=5, stock=200 → OK (but check stops here for P007)

allowPartialFulfillment = false → any shortage = full rejection

InventoryResult.isAvailable() = false
unavailableMessages = ["Insufficient stock for product P007 (Wireless Headphones): requested 1, available 0"]
```

**No stock reserved** (the order never got to the reservation commit for P002 either).

**Result: REJECTED** — pipeline short-circuits; fraud detection never runs.

> ⚠️ Note on the expected output file: `order3_expected.json` only asserts `status`, `errors`, and `message`.  
> The actual Java output **does** include `lineItems`, `subtotal`, `totalTax`, `shippingCost`, and `grandTotal`  
> (all computed before the inventory check). The test harness treats the expected file as a partial match.

---

### Summary Table — Order 3

| Field | Value | Derivation |
|-------|-------|-----------|
| `subtotal` | 231.50 | Computed but order rejected before use |
| `grandTotal` | 273.17 | Computed but order rejected before use |
| `shippingCost` | 0.00 | PLATINUM + STANDARD + no dangerous goods = free |
| Rejection cause | Inventory | P007 stock=0, allowPartialFulfillment=false |
| `status` | **REJECTED** | Stage 7 (inventory) failure |
| Error message | `"Insufficient stock for product P007 … available 0"` | From InventoryService |

---
---

## Order 4 — Platinum + FREESHIP + Mixed Categories (`order4_platinum_freeship.json`)

**Input:** Customer C001 (PLATINUM), items P004×5 + P006×3 + P009×10, promo FREESHIP, zone 3 EXPRESS  
**Expected:** `status=PROCESSED`, `shippingCost=0.00`, `grandTotal=819.42`, no fraud flags

---

### Stage 1 — Validation
All checks pass: all products exist, quantities in range, zone=3, FREESHIP exists in catalog.

### Stage 2 — Entity Resolution
- Customer: C001, tier=PLATINUM → `tierDiscountRate = 0.15`

### Stage 3 — Pricing

**P004 — Blue Jeans (CLOTHING, basePrice=$60.00, weight=0.5kg)**
```
tierDiscount   = 0.15  (PLATINUM)
volumeDiscount = 0.03  (qty=5, bracket [5,9])
combined       = min(0.15 + 0.03, 0.40) = 0.18

discountedUnitPrice = round(60.00 × (1 − 0.18))
                    = round(60.00 × 0.82)
                    = round(49.20) = 49.20

lineSubtotal = round(49.20 × 5) = round(246.00) = 246.00

taxRate   = 0.05  (CLOTHING)
taxAmount = round(246.00 × 0.05) = round(12.30) = 12.30

lineTotal = round(246.00 + 12.30) = 258.30
```

**P006 — Blood Pressure Monitor (MEDICAL, basePrice=$120.00, weight=0.3kg)**
```
tierDiscount   = 0.15  (PLATINUM)
volumeDiscount = 0.00  (qty=3, bracket [1,4])
combined       = min(0.15 + 0.00, 0.40) = 0.15

discountedUnitPrice = round(120.00 × (1 − 0.15))
                    = round(120.00 × 0.85)
                    = round(102.00) = 102.00

lineSubtotal = round(102.00 × 3) = round(306.00) = 306.00

taxRate   = 0.12  (MEDICAL)
taxAmount = round(306.00 × 0.12) = round(36.72) = 36.72

lineTotal = round(306.00 + 36.72) = 342.72
```

**P009 — Vitamin C Supplements (MEDICAL, basePrice=$25.00, weight=0.2kg)**
```
tierDiscount   = 0.15  (PLATINUM)
volumeDiscount = 0.07  (qty=10, bracket [10,19])
combined       = min(0.15 + 0.07, 0.40) = 0.22

discountedUnitPrice = round(25.00 × (1 − 0.22))
                    = round(25.00 × 0.78)
                    = round(19.50) = 19.50

lineSubtotal = round(19.50 × 10) = round(195.00) = 195.00

taxRate   = 0.12  (MEDICAL)
taxAmount = round(195.00 × 0.12) = round(23.40) = 23.40

lineTotal = round(195.00 + 23.40) = 218.40
```

**Aggregates**
```
subtotal = round(246.00 + 306.00 + 195.00) = round(747.00) = 747.00
totalTax = round(12.30 + 36.72 + 23.40)   = round(72.42)  = 72.42
```

### Stage 4 — Promotion (FREESHIP)
```
Type: FREE_SHIPPING
Expiry: 2026-12-31 > 2026-05-20 → valid
EligibleTiers: null (all tiers) → valid
newCustomerOnly: false → valid

→ PromotionResult.freeShipping(code): discountAmount=0.00, freeShipping=true
promoDiscount = 0.00
```

### Stage 5 — Shipping
```
Weight = (0.5×5) + (0.3×3) + (0.2×10) = 2.5 + 0.9 + 2.0 = 5.4 kg
No dangerous goods in P004/P006/P009 → dangerousSurcharge = $0.00

freeShipping = true (FREESHIP promo) → early return path:
  shippingCost = round(dangerousSurcharge) = round(0.00) = $0.00

⚠️ Even though weight=5.4kg > 5.0kg threshold (which would add a $0.20 surcharge),
   the weight surcharge is waived under free-shipping. Only dangerous goods surcharge
   survives the free-shipping gate — and there are none here.
```

> Note: `platinumFreeStandard` would ALSO have been true for STANDARD shipping, but the order uses EXPRESS.  
> The FREESHIP promo fires first regardless of shipping type — `if (freeShipping || platinumFreeStandard)`.

### Stage 6 — Grand Total
```
grandTotal = round(747.00 − 0.00 + 72.42 + 0.00)
           = round(819.42)
           = 819.42  ✓
```

### Stage 7 — Inventory
P004 stock=100 ≥ 5 ✓ · P006 stock=30 ≥ 3 ✓ · P009 stock=150 ≥ 10 ✓ → all reserved.

### Stage 8 — Fraud Detection
```
accountAgeDays = DAYS(2023-01-15 → 2026-05-20) ≈ 1,220 days

HIGH_VALUE_NEW_CUSTOMER: 819.42 > 1000? NO → NO FLAG
PROMO_ABUSE:             promoCode present, 819.42 > 500, but 1220 ≮ 30 → NO FLAG
SUSPICIOUS_ZONE:         zone=3 < 4 → NO FLAG
BULK_PURCHASE:           tier=PLATINUM (not BRONZE) → NO FLAG
```

**Result: PROCESSED, no flags** ✓

---

### Summary Table — Order 4

| Item | basePrice | Tier% | Vol% | Combined | discountedUnit | lineSubtotal | tax% | taxAmount |
|------|-----------|-------|------|---------|---------------|-------------|------|-----------|
| P004 ×5 | $60.00 | 15% | 3% | 18% | $49.20 | $246.00 | 5% | $12.30 |
| P006 ×3 | $120.00 | 15% | 0% | 15% | $102.00 | $306.00 | 12% | $36.72 |
| P009 ×10 | $25.00 | 15% | 7% | 22% | $19.50 | $195.00 | 12% | $23.40 |

| Field | Value | Derivation |
|-------|-------|-----------|
| `subtotal` | 747.00 | 246.00 + 306.00 + 195.00 |
| `totalTax` | 72.42 | 12.30 + 36.72 + 23.40 |
| `promoDiscount` | 0.00 | FREESHIP type has no cash discount |
| `shippingCost` | **0.00** | FREESHIP promo waives base + weight (5.4kg would have added $0.20 surcharge otherwise) |
| `grandTotal` | **819.42** | 747.00 − 0.00 + 72.42 + 0.00 |

---
---

## Order 5 — Multiple Fraud Warnings (`order5_fraud_warnings.json`)

**Input:** Customer C005 (BRONZE, 25 days old), items P004×10 + P009×5, promo SAVE10, zone 4 STANDARD  
**Expected:** `status=PROCESSED`, 3 fraud flags (MEDIUM + MEDIUM + LOW), `grandTotal=672.27`

---

### Stage 1 — Validation
All checks pass: both products exist, zone=4 ∈ [1–5], SAVE10 exists, no duplicates.

### Stage 2 — Entity Resolution
- Customer: C005, tier=BRONZE → `tierDiscountRate = 0.00`

### Stage 3 — Pricing

**P004 — Blue Jeans (CLOTHING, basePrice=$60.00, weight=0.5kg)**
```
tierDiscount   = 0.00  (BRONZE)
volumeDiscount = 0.07  (qty=10, bracket [10,19])
combined       = min(0.00 + 0.07, 0.40) = 0.07

discountedUnitPrice = round(60.00 × (1 − 0.07))
                    = round(60.00 × 0.93)
                    = round(55.80) = 55.80

lineSubtotal = round(55.80 × 10) = round(558.00) = 558.00

taxRate   = 0.05  (CLOTHING)
taxAmount = round(558.00 × 0.05) = round(27.90) = 27.90

lineTotal = round(558.00 + 27.90) = 585.90
```

**P009 — Vitamin C Supplements (MEDICAL, basePrice=$25.00, weight=0.2kg)**
```
tierDiscount   = 0.00  (BRONZE)
volumeDiscount = 0.03  (qty=5, bracket [5,9])
combined       = min(0.00 + 0.03, 0.40) = 0.03

discountedUnitPrice = round(25.00 × (1 − 0.03))
                    = round(25.00 × 0.97)
                    = round(24.25) = 24.25

lineSubtotal = round(24.25 × 5) = round(121.25) = 121.25

taxRate   = 0.12  (MEDICAL)
taxAmount = round(121.25 × 0.12) = round(14.55) = 14.55

lineTotal = round(121.25 + 14.55) = 135.80
```

**Aggregates**
```
subtotal = round(558.00 + 121.25) = round(679.25) = 679.25
totalTax = round(27.90 + 14.55)   = round(42.45)  = 42.45
```

### Stage 4 — Promotion (SAVE10)
```
Type: PERCENTAGE_SUBTOTAL
Expiry: 2026-12-31 > 2026-05-20 → valid
EligibleTiers: null (all tiers) → valid
newCustomerOnly: false → valid

promoDiscount = round(679.25 × 10 / 100)
              = round(679.25 × 0.10)
              = round(67.925)

  Math.round(67.925 × 100) / 100
= Math.round(6792.5) / 100
= 6793 / 100          ← Java Math.round: 0.5 rounds UP
= 67.93  ✓
```

### Stage 5 — Shipping
```
Weight = (0.5 × 10) + (0.2 × 5) = 5.0 + 1.0 = 6.0 kg
No dangerous goods → dangerousSurcharge = $0.00

excessWeight    = max(0, 6.0 − 5.0) = 1.0 kg
weightSurcharge = round(1.0 × 0.50) = round(0.50) = $0.50

freeShipping promo? No (SAVE10 ≠ FREE_SHIPPING)
platinumFreeStandard? No (BRONZE, not PLATINUM)

baseRate        = ZONE_BASE_RATES[4] = $18.00
speedMultiplier = STANDARD → 1.0×
baseCharge      = round(18.00 × 1.0) = $18.00
shippingCost    = round(18.00 + 0.50 + 0.00) = round(18.50) = $18.50  ✓
```

### Stage 6 — Grand Total
```
grandTotal = round(679.25 − 67.93 + 42.45 + 18.50)
           = round(672.27)
           = 672.27  ✓
```

### Stage 7 — Inventory
P004 stock=100 ≥ 10 ✓ · P009 stock=150 ≥ 5 ✓ → all reserved.

### Stage 8 — Fraud Detection
```
accountAgeDays = DAYS(2026-04-25 → 2026-05-20) = 25 days

Rule 1 — HIGH_VALUE_NEW_CUSTOMER:
  672.27 > 1000.00? NO → NO FLAG (≤$1000 total, so doesn't trigger)

Rule 2 — PROMO_ABUSE:
  promoCode = "SAVE10" (non-blank) ✓
  672.27 > 500.00 ✓
  25 < 30 ✓
  → MEDIUM FLAG ⚑

Rule 3 — SUSPICIOUS_ZONE:
  shippingZone=4 >= 4 ✓
  tier=BRONZE ✓
  672.27 > 300.00 ✓
  → MEDIUM FLAG ⚑

Rule 4 — BULK_PURCHASE:
  tier=BRONZE ✓
  P004 qty=10 >= 10 ✓ → LOW FLAG ⚑ (break after first qualifying item — P009 not checked)

All rules evaluated independently. No HIGH flags.
```

`hasHighSeverityFlag()` = false → **PROCESSED**  
MEDIUM/LOW flags converted to warnings in `OrderResult.warnings`.

---

### Summary Table — Order 5

| Item | basePrice | Tier% | Vol% | Combined | discountedUnit | lineSubtotal | tax% | taxAmount |
|------|-----------|-------|------|---------|---------------|-------------|------|-----------|
| P004 ×10 | $60.00 | 0% | 7% | 7% | $55.80 | $558.00 | 5% | $27.90 |
| P009 ×5  | $25.00 | 0% | 3% | 3% | $24.25 | $121.25 | 12% | $14.55 |

| Field | Value | Derivation |
|-------|-------|-----------|
| `subtotal` | 679.25 | 558.00 + 121.25 |
| `totalTax` | 42.45 | 27.90 + 14.55 |
| `promoDiscount` | 67.93 | round(679.25 × 0.10) — note: 67.925 → 67.93 (HALF_UP) |
| `shippingCost` | 18.50 | zone 4 ($18) × 1.0 STANDARD + $0.50 weight surcharge (6.0 kg, 1.0 kg excess) |
| `grandTotal` | **672.27** | 679.25 − 67.93 + 42.45 + 18.50 |

| Fraud Flag | Severity | Rule that fired |
|-----------|---------|----------------|
| PROMO_ABUSE | MEDIUM | promo present + 672.27 > 500 + account 25 days < 30 |
| SUSPICIOUS_ZONE | MEDIUM | zone=4 ≥ 4 + BRONZE + 672.27 > 300 |
| BULK_PURCHASE | LOW | BRONZE + P004 qty=10 ≥ 10 (first qualifying item; loop breaks) |
| `status` | **PROCESSED** | No HIGH flags; all MEDIUM/LOW → warnings |

---
---

## Cross-Case Observations

### Rounding edge cases present in these tests

| Order | Value | Raw before round | Rounded result | Note |
|-------|-------|-----------------|---------------|------|
| 5 | promoDiscount | 67.925 | **67.93** | Exact HALF_UP boundary: .5 rounds up |
| 2 | weightSurcharge | max(0, 5.0−5.0)×0.50 = 0.0 | **0.00** | Exactly at 5kg threshold — no surcharge |
| 4 | discountedUnitPrice P004 | 60×0.82 = 49.20 | **49.20** | Clean result |
| 4 | discountedUnitPrice P009 | 25×0.78 = 19.50 | **19.50** | Clean result |

### Free shipping variations tested

| Order | Free shipping trigger | Shipping type | Dangerous goods | shippingCost |
|-------|----------------------|--------------|----------------|-------------|
| 4 | FREESHIP promo | EXPRESS | None | $0.00 |
| _(not tested)_ | PLATINUM + STANDARD | STANDARD | None | $0.00 |
| _(not tested)_ | FREESHIP promo | any | Present | $15.00 |

> Order 4 exercises the promo-triggered free shipping path, not the PLATINUM+STANDARD path.  
> The PLATINUM+STANDARD+no-dangerous-goods path has no dedicated test case in this suite.

### Fraud flag combinations tested

| Order | Account age | grandTotal | Flags triggered | Outcome |
|-------|------------|-----------|----------------|---------|
| 1 | ~1,034 days | $1,218.14 | None | PROCESSED |
| 2 | 25 days | $2,837.00 | HIGH_VALUE_NEW_CUSTOMER (HIGH) | REJECTED |
| 4 | ~1,220 days | $819.42 | None | PROCESSED |
| 5 | 25 days | $672.27 | PROMO_ABUSE + SUSPICIOUS_ZONE + BULK_PURCHASE | PROCESSED |

> Order 5 is the critical test showing that MEDIUM+LOW flags alone do not reject.  
> The $672.27 grand total is intentionally below the $1,000 HIGH_VALUE threshold to exercise the non-rejection path.

### Sanity check values (from CLAUDE.md)
| Order | Expected `grandTotal` / status | Actual from trace |
|-------|-------------------------------|------------------|
| 1 | 1218.14 | **1218.14** ✓ |
| 2 | REJECTED, HIGH_VALUE_NEW_CUSTOMER | **REJECTED, HIGH_VALUE_NEW_CUSTOMER** ✓ |
| 3 | REJECTED (P007 out of stock) | **REJECTED (P007 stock=0)** ✓ |
| 4 | 819.42, shippingCost=0 | **819.42, shippingCost=0** ✓ |
| 5 | PROCESSED, 3 fraud flags, 672.27 | **PROCESSED, 3 flags, 672.27** ✓ |
