# Business Rules

> This file is the documentation layer for the Java source code.  
> The Java code is the authoritative source of truth — this file describes it, not the other way around.  
> Update this file whenever a Java service is changed.

---

## Pricing Rules
**Source file:** `java-order-system/src/main/java/com/company/orders/service/PricingEngine.java`  
**Service method:** `PricingEngine.calculateLineItems(items, customer, products)`

---

### Rule Catalogue

| Rule | Condition | Effect | ⚠️ |
|------|-----------|--------|----|
| RULE-1 | Customer tier = BRONZE | Tier discount = 0% | |
| RULE-2 | Customer tier = SILVER | Tier discount = 5% | |
| RULE-3 | Customer tier = GOLD | Tier discount = 10% | |
| RULE-4 | Customer tier = PLATINUM | Tier discount = 15% | |
| RULE-5 | qty >= 20 | Volume discount = 12% | ⚠️ ordering-sensitive |
| RULE-6 | qty >= 10 AND < 20 | Volume discount = 7% | ⚠️ ordering-sensitive |
| RULE-7 | qty >= 5 AND < 10 | Volume discount = 3% | ⚠️ ordering-sensitive |
| RULE-8 | qty < 5 | Volume discount = 0% | |
| RULE-9 | Always | combinedDiscount = tierDiscount **+** volumeDiscount (additive, NOT compounded) | ⚠️ critical |
| RULE-10 | combinedDiscount > 0.40 | Cap combinedDiscount at 40% | ⚠️ named constant |
| RULE-11 | Per line | discountedUnitPrice = round(basePrice × (1 − combinedDiscount)) | ⚠️ HALF_UP rounding |
| RULE-12 | Per line | lineSubtotal = round(discountedUnitPrice × quantity) | ⚠️ HALF_UP rounding |
| RULE-13 | Category = ELECTRONICS | Tax rate = 18% | |
| RULE-14 | Category = CLOTHING | Tax rate = 5% | |
| RULE-15 | Category = FOOD | Tax rate = 0% | |
| RULE-16 | Category = MEDICAL | Tax rate = 12% | |
| RULE-17 | Category = GENERAL | Tax rate = 8% | |
| RULE-18 | Per line | taxAmount = round(lineSubtotal × taxRate) — on post-discount, **pre-promo** price | ⚠️ HALF_UP rounding, ordering-sensitive |
| RULE-19 | Per line | lineTotal = round(lineSubtotal + taxAmount) | ⚠️ HALF_UP rounding |
| RULE-20 | Unknown tier | Tier discount defaults to 0.0 | |
| RULE-21 | Unknown category | Tax rate defaults to 0.0 | |

---

### Pricing Matrices

#### Tier Discount Table

| Customer Tier | Discount Rate |
|---------------|--------------|
| BRONZE        | 0%           |
| SILVER        | 5%           |
| GOLD          | 10%          |
| PLATINUM      | 15%          |

#### Volume Discount Brackets

| Quantity Range | Discount Rate | Bracket        |
|---------------|--------------|----------------|
| 1 – 4         | 0%           | `[1, 5)`       |
| 5 – 9         | 3%           | `[5, 10)`      |
| 10 – 19       | 7%           | `[10, 20)`     |
| 20+           | 12%          | `[20, ∞)`      |

> ⚠️ Brackets are evaluated **highest-first** in the Java code (`>= 20` before `>= 10` before `>= 5`).  
> Node.js implementation must preserve this evaluation order or replicate via a sorted lookup.

#### Discount Cap Rule

| Constant        | Value |
|-----------------|-------|
| MAX_DISCOUNT_CAP | 40%  |

`combinedDiscount = Math.min(tierRate + volumeRate, 0.40)`

Examples under the cap:
| Tier     | Qty | Tier% | Volume% | Raw Sum | Capped? | Applied |
|----------|-----|-------|---------|---------|---------|---------|
| BRONZE   | 3   | 0%    | 0%      | 0%      | No      | 0%      |
| SILVER   | 7   | 5%    | 3%      | 8%      | No      | 8%      |
| GOLD     | 15  | 10%   | 7%      | 17%     | No      | 17%     |
| PLATINUM | 20  | 15%   | 12%     | 27%     | No      | 27%     |
| PLATINUM | 20+ | 15%   | 12%     | 27%     | No      | 27%     |
| (any)    | 20+ | 30%+  | 12%     | 42%+    | **Yes** | **40%** |

> Note: With the current tier table (max 15%) and volume table (max 12%), the sum peaks at 27% — the 40% cap is only reachable if tier or volume values are ever increased.

#### Tax Rate Table

| Product Category | Tax Rate |
|-----------------|---------|
| ELECTRONICS     | 18%     |
| CLOTHING        | 5%      |
| FOOD            | 0%      |
| MEDICAL         | 12%     |
| GENERAL         | 8%      |

---

### Complete Pricing Formula — Order of Operations

```
Given:
  basePrice        = product.basePrice
  tierRate         = TIER_DISCOUNTS[customer.tier]
  volumeRate       = getVolumeDiscount(item.quantity)    ← qty-bracket lookup
  taxRate          = TAX_RATES[product.category]

Step 1 — Combined discount (additive, capped):
  combinedDiscount = min(tierRate + volumeRate, 0.40)

Step 2 — Discounted unit price  [ROUND HERE]:
  discountedUnitPrice = round(basePrice × (1 − combinedDiscount))

Step 3 — Line subtotal  [ROUND HERE]:
  lineSubtotal = round(discountedUnitPrice × quantity)

Step 4 — Tax on line subtotal  [ROUND HERE]:
  taxAmount = round(lineSubtotal × taxRate)
              ↑ NOTE: tax is on post-discount, PRE-PROMO price
                Promo codes do NOT reduce tax liability.

Step 5 — Line total  [ROUND HERE]:
  lineTotal = round(lineSubtotal + taxAmount)

Order-level aggregation (in OrderProcessingService):
  subtotal     = round(Σ lineSubtotal)    ← sum of pre-tax line values
  totalTax     = round(Σ taxAmount)       ← sum of per-line taxes
  grandTotal   = round(subtotal − promoDiscount + totalTax + shippingCost)
```

> ⚠️ **Rounding rule**: HALF_UP to 2 decimal places after **each** multiplication step.  
> Do NOT collapse the formula into one expression — intermediate rounding affects the final result.  
> Node.js implementation must use `Decimal.js` (or equivalent) with `ROUND_HALF_UP` mode,  
> or a custom `round2(x)` helper equivalent to `Math.round(x * 100) / 100` (valid for positive values).

---

### Node.js Migration Notes

1. **Do not compound discounts.** `combined = tier + volume`, never `1 - (1-tier)*(1-volume)`.
2. **Round after every multiplication.** Four separate rounding calls per line item — not one at the end.
3. **Tax precedes promo.** `taxAmount` is computed before any promo discount is subtracted. The promo code reduces `subtotal` only; it never changes `taxAmount`.
4. **Volume brackets are closed on the left, open on the right.** `[1,5)`, `[5,10)`, `[10,20)`, `[20,∞)` — check `>=` thresholds in descending order.
5. **Static helper methods** (`getTierDiscountRate`, `getTaxRate`) are called by other services. Export them as standalone functions from the pricing module.
6. **Enum string values.** Java `CustomerTier` and `ProductCategory` enums serialize as their name strings (e.g., `"GOLD"`, `"ELECTRONICS"`). Node.js objects must match exactly (case-sensitive).

---

### Ambiguities (require domain expert or test verification)

1. **Zero/negative quantity items** — `getVolumeDiscount` has no guard; falls through to 0%. Whether qty ≤ 0 is blocked upstream by `ValidationService` is unconfirmed without reading that file.
2. **`MoneyUtils.round()` implementation** — HALF_UP is documented but the actual implementation (BigDecimal vs double) is in `MoneyUtils.java` and not verified here. Must read `MoneyUtils.java` to confirm true HALF_UP semantics.
3. **`lineTotal` usage** — computed and stored on `ProcessedOrderItem`, but `OrderProcessingService` aggregates `lineSubtotal` and `taxAmount` separately. Unclear if `lineTotal` is a display-only field or used in any downstream calculation.
4. **Partial fulfilment and volume brackets** — when items are dropped during partial fulfilment, `calculateLineItems` is called again with the reduced set. Volume brackets apply per-line, so no cross-line interaction, but whether this is intentional business behaviour (vs. a bug) is unconfirmed.

---

## Promotion Rules
**Source file:** `java-order-system/src/main/java/com/company/orders/service/PromotionEngine.java`  
**Service method:** `PromotionEngine.applyPromotion(promoCodeStr, subtotal, customer, orderDate)`

---

### Rule Catalogue

| Rule | Condition | Effect | ⚠️ |
|------|-----------|--------|----|
| RULE-1 | `promoCodeStr` is null or blank | Silent skip — `noPromo()` returned, `promoDiscount = 0` | |
| RULE-2 | Promo code provided | Normalize: `trim()` + `toUpperCase()` before lookup | ⚠️ string parsing |
| RULE-3 | Normalized code not found in repository | `invalid` — `"Promo code not found: <CODE>"` | |
| RULE-4 | `orderDate` is null or empty | Effective order date defaults to today (`LocalDate.now()`) | ⚠️ date parsing |
| RULE-5 | `effectiveOrderDate >= promoCode.expiryDate` | `invalid` — `"Promo code '<CODE>' expired on <date>"` | ⚠️ date comparison; expiry day is itself expired |
| RULE-6 | `eligibleTiers` non-null AND non-empty AND customer tier not in list | `invalid` — `"not available for <TIER> tier customers"` | ⚠️ null/empty guard |
| RULE-7 | `newCustomerOnly = true` | Compute `accountAgeDays = DAYS.between(registrationDate, effectiveOrderDate)` | ⚠️ date arithmetic |
| RULE-8 | `newCustomerOnly = true` AND `accountAgeDays >= 30` | `invalid` — `"restricted to new customers (account < 30 days)"` | ⚠️ threshold 30; boundary ≥30 fails |
| RULE-9 | Type = `"FREE_SHIPPING"` + all checks pass | `discountAmount = 0.00`, `freeShipping = true` | ⚠️ string match; flag propagated to ShippingCalculator |
| RULE-10 | Type = `"PERCENTAGE_SUBTOTAL"` + all checks pass | `discount = round(subtotal × discountValue / 100)` | ⚠️ HALF_UP rounding |
| RULE-11 | Type = `"PERCENTAGE_IF_ABOVE"` AND `subtotal < minimumAmount` | `invalid` — `"requires a subtotal of $<MIN>"` | ⚠️ float comparison |
| RULE-12 | Type = `"PERCENTAGE_IF_ABOVE"` AND `subtotal >= minimumAmount` + all checks pass | `discount = round(subtotal × discountValue / 100)` | ⚠️ HALF_UP rounding |
| RULE-13 | Type not recognized | `invalid` — `"Unknown promo code type '<TYPE>'"` | ⚠️ string match fallthrough |
| RULE-14 | Any invalid result | Non-fatal — added to `warnings`, `promoDiscount = 0`, pipeline continues | ⚠️ ordering-sensitive |
| RULE-15 | Always | `promoDiscount` subtracts from `subtotal` only — never reduces `totalTax` | ⚠️ ordering-sensitive; tax locked pre-promo |

---

### Promo Code Types

| Type | Eligibility Gate | Discount Calculation |
|------|-----------------|---------------------|
| `FREE_SHIPPING` | None beyond standard checks | `discountAmount = 0`, `freeShipping = true` |
| `PERCENTAGE_SUBTOTAL` | None beyond standard checks | `round(subtotal × discountValue / 100)` |
| `PERCENTAGE_IF_ABOVE` | `subtotal >= promoCode.minimumAmount` | `round(subtotal × discountValue / 100)` |

---

### Eligibility Check Sequence (fail-fast, in order)

```
1. Code present?              null / blank → noPromo (silent, not an error)
2. Code exists in repo?       not found   → invalid
3. Not expired?               orderDate >= expiryDate → invalid
4. Tier eligible?             customer.tier not in eligibleTiers → invalid
5. New customer (if flag)?    accountAgeDays >= 30 → invalid
6. Minimum subtotal (PERCENTAGE_IF_ABOVE only)?  subtotal < minimumAmount → invalid
```

> Checks are evaluated in this exact order. The first failure short-circuits the rest.

---

### Key Date Rules

| Field | Format | Parsed with | Default if absent |
|-------|--------|-------------|------------------|
| `orderDate` (order input) | `"YYYY-MM-DD"` | `LocalDate.parse()` | `LocalDate.now()` |
| `promoCode.expiryDate` | `"YYYY-MM-DD"` | `LocalDate.parse()` | — (must be present) |
| `customer.registrationDate` | `"YYYY-MM-DD"` | `LocalDate.parse()` | — (must be present if `newCustomerOnly`) |

> ⚠️ Node.js: always use `dayjs(str, 'YYYY-MM-DD')` — never `new Date(str)` (timezone ambiguity).

---

### New Customer Threshold

| Constant | Value | Boundary semantics |
|----------|-------|--------------------|
| `NEW_CUSTOMER_DAYS` | 30 | Account age **strictly < 30 days** qualifies. Age = 30 does **not** qualify. |

---

### Discount Formula

```
For PERCENTAGE_SUBTOTAL and PERCENTAGE_IF_ABOVE:
  promoDiscount = round(subtotal × discountValue / 100)

  Where:
    subtotal      = Σ lineItem.lineSubtotal (pre-tax, post tier/volume discount)
    discountValue = percentage figure stored on PromoCode (e.g. 10 = 10%)

  Applied in grand total as:
    grandTotal = subtotal − promoDiscount + totalTax + shippingCost
                                          ↑
                          tax is NOT reduced by the promo
```

> ⚠️ `discountValue / 100` — `discountValue` is a percentage stored as a number (e.g., `10` for 10%), not a decimal fraction.

---

### Node.js Migration Notes

1. **Normalize before lookup.** Input code must be `.trim().toUpperCase()` before the repository call.
2. **Date strings only, no `new Date()`.** Use `dayjs` for all date parsing and arithmetic. Both `orderDate` and `customer.registrationDate` arrive as `"YYYY-MM-DD"` strings.
3. **Expiry boundary.** A code expires **on** its expiry date — `orderDate.isSameOrAfter(expiryDate)` → rejected.
4. **New-customer boundary.** `accountAgeDays >= 30` → not new. Exactly 30 days old = not a new customer.
5. **`eligibleTiers` null/empty = no restriction.** Only apply tier check when the array is non-null and has entries.
6. **`freeShipping` flag must propagate.** `PromotionResult.isFreeShipping` is read by `ShippingCalculator` — do not drop it.
7. **`discountValue` is a percentage integer** (e.g., `10`), not a decimal (not `0.10`). Divide by 100 in the formula.
8. **HALF_UP rounding on discount amount.** Use `round2(subtotal * discountValue / 100)`.

---

### Ambiguities

1. **`eligibleTiers = null` vs `eligibleTiers = []`** — both skip the tier check. Relies on the repository never returning an empty list that means "no tiers allowed". Requires confirming `PromoCodeRepository` data contract.
2. **`newCustomerOnly` + tier restriction on same code** — both checks can coexist; neither validates the other. A code that is simultaneously `newCustomerOnly` and tier-restricted is technically valid but may be a data-entry error.
3. **`discountValue` field type and constraints** — inferred as a percentage number from the formula `subtotal * discountValue / 100`. The `PromoCode` model field type (int vs double) and valid range are not visible here.
4. **`customer.registrationDate` format** — assumed `"YYYY-MM-DD"` (Java `LocalDate.parse()` default). If the repository stores dates in another format, the parse will throw at runtime.
5. **`PERCENTAGE_IF_ABOVE` with `minimumAmount = 0`** — the minimum check `subtotal < 0` is always false, making the type behave identically to `PERCENTAGE_SUBTOTAL`. Likely a repository data concern but worth confirming.

---

## Shipping Rules
**Source file:** `java-order-system/src/main/java/com/company/orders/service/ShippingCalculator.java`  
**Service method:** `ShippingCalculator.calculate(order, products, customer, freeShipping)`

---

### Rule Catalogue

| Rule | Condition | Effect | ⚠️ |
|------|-----------|--------|----|
| RULE-1 | `freeShipping = true` (FREESHIP promo) | Base + weight waived; dangerous surcharge still applies | ⚠️ ordering-sensitive; flag from PromotionEngine |
| RULE-2 | Tier = PLATINUM AND type = STANDARD AND no dangerous goods | Base + weight waived; dangerous surcharge still applies | ⚠️ all 3 sub-conditions required |
| RULE-3 | RULE-1 or RULE-2 applies | `shippingCost = round(dangerousSurcharge)` = 0.00 or 15.00 | ⚠️ HALF_UP rounding |
| RULE-4 | Zone not in {1–5} | Default base rate = $25.00 (Zone 5 fallback, no warning) | ⚠️ silent fallback |
| RULE-5 | Zone = 1 | Base rate = $5.00 | |
| RULE-6 | Zone = 2 | Base rate = $8.00 | |
| RULE-7 | Zone = 3 | Base rate = $12.00 | |
| RULE-8 | Zone = 4 | Base rate = $18.00 | |
| RULE-9 | Zone = 5 | Base rate = $25.00 | |
| RULE-10 | Type = STANDARD or null | Speed multiplier = 1.0× | ⚠️ null defaults to STANDARD |
| RULE-11 | Type = EXPRESS | Speed multiplier = 1.5× | |
| RULE-12 | Type = OVERNIGHT | Speed multiplier = 2.5× | |
| RULE-13 | Type unrecognized | Speed multiplier defaults to 1.0× | ⚠️ silent fallthrough |
| RULE-14 | totalWeightKg ≤ 5.0 | weightSurcharge = $0.00 | |
| RULE-15 | totalWeightKg > 5.0 | `weightSurcharge = round((totalWeightKg − 5.0) × $0.50)` | ⚠️ HALF_UP rounding; only excess kg charged |
| RULE-16 | Product not found in map | Contributes 0.0 to weight (null-safe fallback) | ⚠️ null guard |
| RULE-17 | Any item has `isDangerousGood = true` | `dangerousSurcharge = $15.00` flat per order | ⚠️ hardcoded; per-order not per-item |
| RULE-18 | No dangerous goods | `dangerousSurcharge = $0.00` | |
| RULE-19 | Standard path | `baseCharge = round(baseRate × multiplier)` then `cost = round(baseCharge + weightSurcharge + dangerousSurcharge)` | ⚠️ two separate rounds; do not collapse |
| RULE-20 | Always | Zone rates reconstructed from tests in 2021; no original rate card exists | ⚠️ no external source of truth |

---

### Zone Base Rate Table

| Shipping Zone | Base Rate |
|--------------|-----------|
| 1            | $5.00     |
| 2            | $8.00     |
| 3            | $12.00    |
| 4            | $18.00    |
| 5            | $25.00    |
| Unknown/null | $25.00 (fallback, no warning) |

---

### Speed Multiplier Table

| Shipping Type | Multiplier |
|--------------|-----------|
| STANDARD     | 1.0×      |
| EXPRESS      | 1.5×      |
| OVERNIGHT    | 2.5×      |
| null / unknown | 1.0× (default) |

---

### Surcharge Constants

| Constant | Value | Scope |
|----------|-------|-------|
| `WEIGHT_THRESHOLD_KG` | 5.0 kg | Per order; excess above this threshold is charged |
| `WEIGHT_SURCHARGE_PER_KG` | $0.50/kg | Applied to excess kg only |
| `DANGEROUS_GOODS_SURCHARGE` | $15.00 | Flat per order (not per item); never waived |

---

### Complete Shipping Formula — Order of Operations

```
Given:
  items            = order.getItems()
  hasDangerousGood = any item where product.isDangerousGood == true
  totalWeightKg    = Σ (product.weightKg × item.quantity)   [0.0 if product missing]
  freeShipping     = flag from PromotionEngine (FREESHIP promo)
  platinumFree     = (customer.tier == PLATINUM)
                     AND (order.shippingType == STANDARD)
                     AND (!hasDangerousGood)

Step 1 — Surcharges (always computed):
  excessWeightKg   = max(0, totalWeightKg − 5.0)
  weightSurcharge  = round(excessWeightKg × 0.50)      [ROUND HERE]
  dangerousSurcharge = hasDangerousGood ? 15.00 : 0.00

Step 2 — Free-shipping gate:
  IF (freeShipping OR platinumFree):
    shippingCost = round(dangerousSurcharge)            [ROUND HERE]
    → return early

Step 3 — Standard calculation:
  baseRate     = ZONE_BASE_RATES[order.shippingZone] ?? 25.00
  multiplier   = getSpeedMultiplier(order.shippingType)  [null → 1.0]
  baseCharge   = round(baseRate × multiplier)            [ROUND HERE]
  shippingCost = round(baseCharge + weightSurcharge + dangerousSurcharge)  [ROUND HERE]
```

> ⚠️ **Two rounds on the standard path**: one after `baseRate × multiplier`, one on the final sum.  
> ⚠️ **Dangerous goods surcharge is NEVER waived** — not by promo, not by PLATINUM tier.

---

### Free Shipping Decision Matrix

| Condition | freeShipping flag | Tier | Type | Dangerous? | Result |
|-----------|------------------|------|------|-----------|--------|
| FREESHIP promo, no dangerous goods | true | any | any | No | $0.00 |
| FREESHIP promo, dangerous goods | true | any | any | Yes | $15.00 |
| PLATINUM + STANDARD, no dangerous goods | false | PLATINUM | STANDARD | No | $0.00 |
| PLATINUM + STANDARD, dangerous goods | false | PLATINUM | STANDARD | Yes | $15.00 |
| PLATINUM + EXPRESS | false | PLATINUM | EXPRESS | No | base + speed charges apply |
| Any other combination | false | any | any | any | full calculation |

---

### Node.js Migration Notes

1. **Two separate `round()` calls on the standard path.** `round(baseRate × multiplier)` is stored as `baseCharge`, then `round(baseCharge + weightSurcharge + dangerousSurcharge)`. Do not collapse.
2. **Dangerous goods surcharge is never waived.** Even under full free-shipping conditions, $15.00 applies if any item is a dangerous good.
3. **`platinumFreeStandard` requires all three conditions.** PLATINUM tier alone is not sufficient; type must be STANDARD and there must be no dangerous goods.
4. **Null `shippingType` defaults to 1.0× (STANDARD multiplier).** Guard with `type ? multipliers[type] ?? 1.0 : 1.0`.
5. **Unknown zone silently defaults to $25.00.** Replicate `ZONE_BASE_RATES[zone] ?? 25.00` — do not throw.
6. **Weight uses original order items, not fulfilled subset.** On partial fulfilment, `order.getItems()` is the full list. The `products` map passed to the second `calculate()` call is the fulfilled-items subset — but `order.getItems()` still iterates the full list. Products not in the map return 0.0 weight (null-safe).
7. **Zone is an integer key.** Ensure the JSON `shippingZone` field is deserialized as a number, not a string, before lookup.

---

### Ambiguities

1. **Dangerous goods surcharge under free-shipping promo** — whether a FREESHIP promo customer should still pay $15 for dangerous items is a business intent question; the code charges it, but no business documentation confirms this is deliberate.
2. **Null-safe weight fallback** — products not found in the `products` map contribute 0.0 weight. Whether this is intentional or a latent bug depends on whether `ValidationService` fully prevents missing products from reaching this stage.
3. **Partial fulfilment weight uses full item list** — `order.getItems()` is used inside `calculate()`, so on the second call (after partial fulfilment), weight and dangerous-goods detection run against the original items even though only a subset was fulfilled. This may be a bug or intentional; requires test verification.
4. **Zone stored as `Integer` vs `int`** — auto-boxing behaviour when looking up `order.getShippingZone()` in the `HashMap<Integer, Double>`. If the JSON field arrives as a floating-point number (e.g., `1.0`), deserialisation must coerce it to `int` before lookup or the key will not match.
5. **Weight surcharge: fractional excess not pre-rounded** — `excessWeightKg = totalWeightKg - 5.0` retains full floating-point precision before being multiplied by $0.50. Rounding only happens after multiplication. This is correct per the code but may differ from a naive "round weight first, then multiply" interpretation.

---

## Fraud Detection Rules
**Source file:** `java-order-system/src/main/java/com/company/orders/service/FraudDetectionService.java`  
**Service method:** `FraudDetectionService.evaluate(order, customer, grandTotal, orderDate)`

---

### Rule Catalogue

| Rule | Flag | Severity | Condition | Effect | ⚠️ |
|------|------|----------|-----------|--------|----|
| RULE-1 | `HIGH_VALUE_NEW_CUSTOMER` | HIGH | `grandTotal > $1,000` AND `accountAgeDays < 90` | Order **REJECTED**; inventory reservation released | ⚠️ ordering-sensitive; grandTotal must be final before this call |
| RULE-2 | `PROMO_ABUSE` | MEDIUM | promo code present AND `grandTotal > $500` AND `accountAgeDays < 30` | Order PROCESSED with fraud warning | ⚠️ string null/blank check on promoCode |
| RULE-3 | `SUSPICIOUS_ZONE` | MEDIUM | `shippingZone >= 4` AND `customer.tier == BRONZE` AND `grandTotal > $300` | Order PROCESSED with fraud warning | ⚠️ integer comparison on zone |
| RULE-4 | `BULK_PURCHASE` | LOW | `customer.tier == BRONZE` AND any single item `quantity >= 10` | Order PROCESSED with informational note | ⚠️ first qualifying item only; `break` after first match |

---

### Detailed Rules

**RULE-1 — HIGH_VALUE_NEW_CUSTOMER (HIGH)**
Source: `FraudDetectionService.evaluate`

IF `grandTotal > 1000.00` AND `accountAgeDays < 90` THEN add HIGH flag with message:
`"Order total $<grandTotal> exceeds $1000.00 threshold for new accounts (account age: <N> days, threshold: 90 days)"`

Node.js note: ⚠️ Ordering-sensitive — `grandTotal` passed in must be the **final** post-promo, post-shipping value computed before fraud detection is called. Using any intermediate total would change rejection outcomes. ⚠️ Boundary: `> 1000.00` (strictly greater — exactly $1,000.00 does NOT trigger).

---

**RULE-2 — PROMO_ABUSE (MEDIUM)**
Source: `FraudDetectionService.evaluate`

IF `order.promoCode != null` AND `order.promoCode.trim()` is not empty AND `grandTotal > 500.00` AND `accountAgeDays < 30` THEN add MEDIUM flag with message:
`"Promotional code '<code>' applied to $<grandTotal> order from account aged <N> days (threshold: 30 days, minimum suspicious total: $500.00)"`

Node.js note: ⚠️ String null/blank check — uses the **raw promo code string from the order**, not the resolved/validated one from `PromotionResult`. A code that was invalid (rejected by PromotionEngine) still triggers this flag if the raw string is present. ⚠️ Boundary: `> 500.00` strictly.

---

**RULE-3 — SUSPICIOUS_ZONE (MEDIUM)**
Source: `FraudDetectionService.evaluate`

IF `order.shippingZone >= 4` AND `customer.tier == BRONZE` AND `grandTotal > 300.00` THEN add MEDIUM flag with message:
`"Bronze-tier account placing $<grandTotal> order to zone <zone> (zone threshold: 4, value threshold: $300.00)"`

Node.js note: ⚠️ Integer comparison — `shippingZone >= 4` (zones 4 and 5 both trigger). ⚠️ Boundary: `> 300.00` strictly.

---

**RULE-4 — BULK_PURCHASE (LOW)**
Source: `FraudDetectionService.evaluate`

IF `customer.tier == BRONZE` AND any item in `order.items` has `quantity >= 10` THEN add LOW flag with message:
`"Bronze-tier account ordering <qty> units of product <productId> (bulk threshold: 10 units)"`

Node.js note: ⚠️ Only one flag is generated per order regardless of how many items exceed the threshold — `break` exits after the first qualifying item. The flag description names the **first** qualifying item (in iteration order), not all of them. ⚠️ Boundary: `>= 10` (exactly 10 triggers).

---

**RULE-5 — Rules are independent**
Source: `FraudDetectionService.evaluate` — comment: "evaluated INDEPENDENTLY"

IF multiple rules trigger THEN all are added to the flags list — no rule prevents another from being evaluated.

Node.js note: ⚠️ Ordering-sensitive — all four checks must always run; do not short-circuit after first match.

---

**RULE-6 — Severity outcome mapping**
Source: `FraudDetectionService.hasHighSeverityFlag`, consumed by `OrderProcessingService.processOrder`

IF any flag has `severity == "HIGH"` THEN `hasHighSeverityFlag()` returns true → order REJECTED, inventory released.
IF only MEDIUM/LOW flags THEN order PROCESSED; flags surfaced as warnings.
IF no flags THEN order PROCESSED cleanly.

Node.js note: ⚠️ String comparison — `"HIGH".equals(f.getSeverity())`. Node.js: `f.severity === 'HIGH'`. The severity values are strings, not enums, at the `FraudFlagResult` level.

---

**RULE-7 — Account age computation**
Source: `FraudDetectionService.evaluate`

`accountAgeDays = DAYS.between(LocalDate.parse(customer.registrationDate), effectiveDate)`

If `orderDate` is null or blank, `effectiveDate = LocalDate.now()`.

Node.js note: ⚠️ Date parsing — both `customer.registrationDate` and `orderDate` must be `"YYYY-MM-DD"` strings; use `dayjs`. ⚠️ Account age is computed **once** and reused across all four rules — do not recompute per rule.

---

### Fraud Rules Reference Table

| Flag | Severity | Threshold 1 | Threshold 2 | Threshold 3 | Outcome |
|------|----------|------------|------------|------------|---------|
| `HIGH_VALUE_NEW_CUSTOMER` | HIGH | grandTotal **> $1,000** | accountAge **< 90 days** | — | REJECTED |
| `PROMO_ABUSE` | MEDIUM | promoCode present | grandTotal **> $500** | accountAge **< 30 days** | PROCESSED + warning |
| `SUSPICIOUS_ZONE` | MEDIUM | zone **>= 4** | tier = BRONZE | grandTotal **> $300** | PROCESSED + warning |
| `BULK_PURCHASE` | LOW | tier = BRONZE | any item qty **>= 10** | — | PROCESSED + note |

---

### Threshold Constants

| Constant | Value | Used In |
|----------|-------|---------|
| `HIGH_VALUE_THRESHOLD` | $1,000.00 | Rule 1 |
| `NEW_ACCOUNT_DAYS_HIGH` | 90 days | Rule 1 |
| `PROMO_ABUSE_THRESHOLD` | $500.00 | Rule 2 |
| `NEW_ACCOUNT_DAYS_PROMO` | 30 days | Rule 2 |
| `SUSPICIOUS_ZONE_THRESHOLD` | $300.00 | Rule 3 |
| `SUSPICIOUS_ZONE_MINIMUM` | 4 | Rule 3 |
| `BULK_PURCHASE_THRESHOLD` | 10 units | Rule 4 |

> All constants are named. No bare magic numbers in the evaluation logic.

---

### Boundary Summary (all comparisons)

| Rule | Operator | Boundary value | Boundary is… |
|------|----------|---------------|--------------|
| 1 — grandTotal | `>` | $1,000.00 | **not** triggering (exactly $1,000 = no flag) |
| 1 — accountAge | `<` | 90 days | **not** triggering (exactly 90 days = no flag) |
| 2 — grandTotal | `>` | $500.00 | **not** triggering |
| 2 — accountAge | `<` | 30 days | **not** triggering (exactly 30 days = no flag) |
| 3 — shippingZone | `>=` | 4 | **triggering** (zone 4 triggers) |
| 3 — grandTotal | `>` | $300.00 | **not** triggering |
| 4 — quantity | `>=` | 10 | **triggering** (qty 10 triggers) |

---

### Ordering Constraints

```
MUST happen before fraud detection:
  ✓ PricingEngine.calculateLineItems()     — grandTotal depends on pricing
  ✓ PromotionEngine.applyPromotion()       — grandTotal depends on promoDiscount
  ✓ ShippingCalculator.calculate()         — grandTotal depends on shippingCost
  ✓ InventoryService.checkAndReserve()     — inventory reserved before fraud check
                                             (released here on HIGH flag)

MUST happen after fraud detection:
  ✓ inventoryService.releaseReservation()  — called only on HIGH flag rejection
  ✓ orderRepository.save()                 — final persist after outcome determined
```

---

### Node.js Migration Notes

1. **All four rules run independently — no early exit.** Always evaluate all four; collect all flags before checking severity.
2. **`grandTotal` must be final.** Pass in the post-promo, post-shipping, post-partial-fulfilment grand total. Any earlier value changes rejection outcomes.
3. **Raw `promoCode` string checked, not resolved code.** PROMO_ABUSE checks `order.promoCode` directly — a code rejected by PromotionEngine still triggers the flag if the raw field is non-blank.
4. **BULK_PURCHASE emits one flag per order.** Only the first item (in array order) exceeding qty >= 10 is named in the flag message. Stop iterating after the first match.
5. **Severity is a string at the result level.** `hasHighSeverityFlag` compares `"HIGH"` as a string. Node.js: `flags.some(f => f.severity === 'HIGH')`.
6. **Account age computed once, shared across all rules.** Do not recompute `accountAgeDays` per rule.
7. **Date parsing: `"YYYY-MM-DD"` only, via dayjs.** `orderDate` null/blank → `dayjs()` (today).

---

### Ambiguities

1. **PROMO_ABUSE uses raw order `promoCode`, not the validated one.** If a customer submits a non-existent promo code (rejected by PromotionEngine), the PROMO_ABUSE rule still fires because `order.promoCode != null`. Whether this is intentional (flag the attempt) or a bug (should only fire if promo was actually applied) requires domain clarification.
2. **BULK_PURCHASE first-item-only flag.** The `break` after the first qualifying item means only one flag is emitted even if multiple items each have qty >= 10. The flag names the first qualifying item in iteration order, which depends on the order of `order.getItems()`. Whether this is intentional (one flag per order) or a simplification that loses information is unclear.
3. **No cross-rule deduplication or ordering.** Rules 1 and 2 can both trigger simultaneously (HIGH_VALUE_NEW_CUSTOMER + PROMO_ABUSE) because they have different thresholds. The result list can contain multiple flags from the same order. Whether downstream consumers handle multiple flags correctly is not validated here.
4. **`grandTotal` parameter is the post-partial-fulfilment value.** On partial fulfilment, the recalculated `finalGrandTotal` is passed in, not the original. This means fraud thresholds are evaluated against the reduced-item total — a border case where dropping an item takes grandTotal from $1,001 to $999 would avoid Rule 1. Intentional or not is unclear.
5. **No ORDER_HISTORY rule.** The class accepts `OrderRepository` as a dependency (injected in constructor) but never calls it in `evaluate()`. A comment in the original design document may have intended a repeat-order or velocity rule. The `orderRepository` field is dead code as of this implementation.

---

## Validation Rules
**Source file:** `java-order-system/src/main/java/com/company/orders/service/ValidationService.java`  
**Service method:** `ValidationService.validate(order)`

---

### Rule Catalogue

| Rule | Field | Condition | Error Message | ⚠️ |
|------|-------|-----------|---------------|----|
| RULE-1 | `orderId` | null or blank | `"orderId is required"` | ⚠️ string null/blank |
| RULE-2a | `customerId` | null or blank | `"customerId is required"` | ⚠️ string null/blank |
| RULE-2b | `customerId` | not found in CustomerRepository | `"Customer not found: <id>"` | ⚠️ referential check |
| RULE-3 | `items` | null or empty list | `"Order must contain at least one item"` | |
| RULE-4a | `items[i].productId` | null or blank | `"Item[<i>]: productId is required"` | ⚠️ string null/blank; zero-indexed |
| RULE-4b | `items[i].productId` | not found in ProductRepository | `"Item[<i>]: Product not found: <id>"` | ⚠️ referential check |
| RULE-5a | `items[i].quantity` | `< 1` | `"Item[<i>] (<productId>): quantity must be >= 1"` | ⚠️ boundary inclusive |
| RULE-5b | `items[i].quantity` | `> 100` | `"Item[<i>] (<productId>): quantity must be <= 100. Contact sales for bulk orders."` | ⚠️ hardcoded 100; boundary inclusive |
| RULE-6 | `items[i].productId` | duplicate productId within same order | `"Item[<i>]: Duplicate productId '<id>'. Combine quantities into a single line item."` | ⚠️ ordering-sensitive; only checked if product exists |
| RULE-7 | `shippingZone` | `< 1` OR `> 5` | `"shippingZone must be between 1 and 5 (got: <zone>)"` | ⚠️ integer range; boundaries 1 and 5 are valid |
| RULE-8 | `shippingType` | null | `"shippingType is required (STANDARD, EXPRESS, or OVERNIGHT)"` | ⚠️ enum null check |
| RULE-9 | `promoCode` | non-blank AND not in PromoCodeRepository (after trim+toUpperCase) | `"Promo code not recognised: '<raw_code>'"` | ⚠️ string normalization; existence only — expiry/eligibility deferred |

---

### Detailed Rules

**RULE-1 — orderId required**
Source: `ValidationService.validate`

IF `order.orderId == null` OR `order.orderId.trim().isEmpty()` THEN add error `"orderId is required"`

Node.js note: ⚠️ Trim before empty check — `!orderId || !orderId.trim()`.

---

**RULE-2 — customerId must exist**
Source: `ValidationService.validate`

IF `customerId` null or blank THEN `"customerId is required"` (repository not queried).
ELSE IF `customerRepository.exists(customerId)` is false THEN `"Customer not found: <customerId>"`.

Node.js note: ⚠️ Two-phase check — blank guard first, then repository. Both can be active in the same pass (fail-all mode) only if the field is non-blank. Node.js: skip repository call if field is already blank.

---

**RULE-3 — items list must be non-empty**
Source: `ValidationService.validate`

IF `order.items == null` OR `order.items.length == 0` THEN add error `"Order must contain at least one item"` AND **skip all per-item checks** (RULES 4–6 are inside the `else` block).

Node.js note: ⚠️ Ordering-sensitive — per-item rules 4–6 only run when items is non-null and non-empty. If items is absent, none of those errors are emitted.

---

**RULE-4 — productId must exist**
Source: `ValidationService.validate`

IF `item.productId` null or blank THEN `"Item[<i>]: productId is required"`.
ELSE IF `productRepository.exists(productId)` is false THEN `"Item[<i>]: Product not found: <productId>"`.

Node.js note: ⚠️ Index `i` is zero-based (matches the array position). Repository check is skipped when productId is blank.

---

**RULE-5 — quantity range 1–100 inclusive**
Source: `ValidationService.validate`

IF `item.quantity < 1` THEN `"Item[<i>] (<productId>): quantity must be >= 1"`.
IF `item.quantity > 100` THEN `"Item[<i>] (<productId>): quantity must be <= 100. Contact sales for bulk orders."`.

Node.js note: ⚠️ Boundary is inclusive on both ends — qty 1 and qty 100 are both valid. ⚠️ The upper-bound error message includes a customer-facing instruction (`"Contact sales for bulk orders."`). ⚠️ `100` is a hardcoded literal in the source — no named constant. ⚠️ Both checks run independently per item, but in practice `quantity < 1` and `quantity > 100` cannot both be true simultaneously.

---

**RULE-6 — no duplicate productIds**
Source: `ValidationService.validate`

IF a `productId` has already been seen in this order THEN `"Item[<i>]: Duplicate productId '<id>'. Combine quantities into a single line item."`.

Node.js note: ⚠️ Ordering-sensitive — this check is **inside the `else` branch of RULE-4b**: it only runs when the product actually exists in the repository. A non-existent productId that appears twice will produce two "Product not found" errors but no duplicate error. ⚠️ Uses a `HashSet` — detection is based on exact string equality of `productId` (post-repository key match, no normalization applied here).

---

**RULE-7 — shippingZone must be 1–5 inclusive**
Source: `ValidationService.validate`

IF `order.shippingZone < 1` OR `order.shippingZone > 5` THEN `"shippingZone must be between 1 and 5 (got: <zone>)"`.

Node.js note: ⚠️ Integer range — both boundaries inclusive. Zone 0 and zone 6 both trigger. Note: `ShippingCalculator` accepts unknown zones with a silent $25 fallback (see Shipping Rules), but `ValidationService` blocks them here — the two behaviours are complementary, not redundant.

---

**RULE-8 — shippingType must be non-null**
Source: `ValidationService.validate`

IF `order.shippingType == null` THEN `"shippingType is required (STANDARD, EXPRESS, or OVERNIGHT)"`.

Node.js note: ⚠️ Only a null check — an unrecognized string value (e.g., `"PRIORITY"`) that fails enum deserialization would throw during JSON parsing before reaching this validator. In Node.js, an explicit enum membership check (`['STANDARD','EXPRESS','OVERNIGHT'].includes(shippingType)`) should be added to replicate the same intent.

---

**RULE-9 — promoCode existence (not eligibility)**
Source: `ValidationService.validate`

IF `order.promoCode` is non-null AND non-blank THEN normalize to `trim().toUpperCase()` and check `promoCodeRepository.exists()`. IF not found THEN `"Promo code not recognised: '<raw_code>'"`.

Node.js note: ⚠️ Error message uses the **raw** (un-normalized) code in the message body (`order.getPromoCode()`), even though the lookup uses the normalized form. ⚠️ Blank promoCode is silently skipped — no error, no warning. ⚠️ Only existence is checked here; expiry, tier eligibility, and minimum amount are deferred to `PromotionEngine`. ⚠️ Ordering-sensitive — this check happens before pricing; a valid-but-expired code passes here and is rejected later.

---

**RULE-10 — fail-all (not fail-fast)**
Source: `ValidationService.validate` — comment: "All validation errors are collected before returning"

IF multiple rules fail THEN ALL errors are collected and returned together in a single `ValidationResult.fail(errors)`.

Node.js note: ⚠️ Ordering-sensitive — do not return after the first error. Run all checks, accumulate all errors, return the full list. Exception: per-item rules only run when items is non-null/non-empty (RULE-3 gates them).

---

**RULE-11 (outcome) — validation failure halts entire pipeline**
Source: `OrderProcessingService.processOrder`

IF `ValidationResult.isValid() == false` THEN `buildRejected()` is called immediately; no pricing, no inventory, no fraud detection. Result persisted via `orderRepository.save()`.

Node.js note: ⚠️ Ordering-sensitive — validation must be the first stage. Do not call PricingEngine or any other service until validation passes.

---

### Validation Scope Boundaries

| Checked here (ValidationService) | Deferred to later stage |
|----------------------------------|------------------------|
| `orderId` present | — |
| `customerId` present + exists | — |
| Items list non-empty | — |
| `productId` present + exists | — |
| `quantity` in `[1, 100]` | — |
| No duplicate `productId` per order | — |
| `shippingZone` in `[1, 5]` | — |
| `shippingType` non-null | — |
| Promo code exists in catalog | Promo expiry → PromotionEngine |
| | Promo tier eligibility → PromotionEngine |
| | Promo new-customer restriction → PromotionEngine |
| | Promo minimum amount → PromotionEngine |
| | Stock availability → InventoryService |
| | Fraud rules → FraudDetectionService |

---

### Error Message Format Reference

All error messages use the item's zero-based index `[i]` for per-item errors:

```
orderId errors:        "orderId is required"
customerId errors:     "customerId is required"
                       "Customer not found: <customerId>"
items errors:          "Order must contain at least one item"
per-item errors:       "Item[0]: productId is required"
                       "Item[0]: Product not found: <productId>"
                       "Item[0] (<productId>): quantity must be >= 1"
                       "Item[0] (<productId>): quantity must be <= 100. Contact sales for bulk orders."
                       "Item[0]: Duplicate productId '<productId>'. Combine quantities into a single line item."
shipping errors:       "shippingZone must be between 1 and 5 (got: <zone>)"
                       "shippingType is required (STANDARD, EXPRESS, or OVERNIGHT)"
promo errors:          "Promo code not recognised: '<rawCode>'"
```

---

### Node.js Migration Notes

1. **Fail-all, not fail-fast.** Collect all errors before returning. The caller expects the complete error list.
2. **Per-item checks are gated by RULE-3.** If `items` is null/empty, no per-item errors are emitted.
3. **RULE-6 (duplicate) is gated by RULE-4b (product exists).** Only run duplicate check when the product is confirmed to exist.
4. **Quantity cap `100` is a bare literal** — no named constant in source. Replicate as a named constant in Node.js (`MAX_ITEM_QUANTITY = 100`).
5. **Promo lookup uses `trim().toUpperCase()`** but the error message uses the raw input. Preserve the raw value for the error, normalize only for the lookup.
6. **`shippingType` null check is insufficient in Node.js** — add an enum membership check (`['STANDARD','EXPRESS','OVERNIGHT'].includes(type)`) since Node.js JSON parsing won't enforce enum types.
7. **Validation is stage 1 — must run before all other services.** Never call PricingEngine, PromotionEngine, InventoryService, or FraudDetectionService on an unvalidated order.

---

### Ambiguities

1. **`shippingType` unknown string** — a value like `"PRIORITY"` is accepted by the null-check here (non-null) but would fail enum deserialization in Java before reaching this validator. In Node.js, where JSON parsing doesn't enforce enums, an unknown string passes RULE-8 unchallenged. Requires adding an explicit allowlist check in Node.js.
2. **`quantity = 0`** — RULE-5a catches this (`< 1` → error). But PricingEngine and VolumeDiscount also handle quantity; they don't guard against 0. ValidationService is the sole gate — confirms 0-quantity items are blocked before any calculation stage.
3. **`orderId` uniqueness** — RULE-1 checks presence but not uniqueness. Two orders with the same `orderId` can both be processed and saved. Whether `orderId` uniqueness is enforced at the repository level is not visible here.
4. **`customerId` and `productId` normalization** — both are checked with null/blank guards but no `trim()` or case normalization before the repository lookup. A `customerId` of `" C001"` (leading space) would produce "Customer not found" even if `"C001"` exists. Whether the repository normalizes keys is not visible here.
5. **`allowPartialFulfillment` not validated** — the `allowPartialFulfillment` boolean field on `Order` is used in `InventoryService` but has no validation rule here. A missing/null value would use Java's default (`false`) — may produce unexpected full-rejection on partial stock. Whether this needs an explicit validation rule is unclear.
