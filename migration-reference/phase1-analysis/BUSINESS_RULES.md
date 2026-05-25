# Business Rules — Order Processing System
## Authoritative Reference (extracted from Java source)

> Use this file as the source of truth when implementing or validating the migration.
> If any rule here contradicts observed Java behavior, update this file.

---

## Pricing Rules

### Tier Discounts (applied per line item, on base price)
| Tier     | Discount |
|----------|----------|
| BRONZE   | 0%       |
| SILVER   | 5%       |
| GOLD     | 10%      |
| PLATINUM | 15%      |

### Volume Discounts (additive with tier discount, per line item quantity)
| Quantity Range | Discount |
|----------------|----------|
| 1 – 4          | 0%       |
| 5 – 9          | 3%       |
| 10 – 19        | 7%       |
| 20+            | 12%      |

**⚠ CRITICAL**: Tier and volume discounts are ADDITIVE. Total = tier% + volume%.
**⚠ CRITICAL**: Combined discount is CAPPED at 40% maximum.

Formula: `combinedDiscount = min(tierDiscount + volumeDiscount, 0.40)`

### Tax Rates (applied per line item on discounted price)
| Category    | Tax Rate |
|-------------|----------|
| ELECTRONICS | 18%      |
| CLOTHING    | 5%       |
| FOOD        | 0%       |
| MEDICAL     | 12%      |
| GENERAL     | 8%       |

### Pricing Formula (per line item)
```
discountedUnitPrice = round(basePrice × (1 - combinedDiscount))
lineSubtotal        = round(discountedUnitPrice × quantity)    ← PRE-TAX
taxAmount           = round(lineSubtotal × taxRate)
lineTotal           = round(lineSubtotal + taxAmount)
```

### Grand Total Formula
```
subtotal      = sum of all lineSubtotal values         (pre-tax, post tier/volume discount)
totalTax      = sum of all taxAmount values            (pre-promo)
promoDiscount = promo code discount applied on subtotal
shippingCost  = zone + weight + type calculation
grandTotal    = subtotal - promoDiscount + totalTax + shippingCost
```

**⚠ CRITICAL**: Tax is computed on PRE-PROMO discounted prices.
Promo codes reduce the pre-tax subtotal, NOT the tax liability.

### Rounding
All monetary values: HALF_UP to 2 decimal places after EVERY multiplication.
Use: `Math.round(value * 100) / 100`

---

## Shipping Rules

### Zone Base Rates
| Zone | Base Rate |
|------|-----------|
| 1    | $5.00     |
| 2    | $8.00     |
| 3    | $12.00    |
| 4    | $18.00    |
| 5    | $25.00    |

### Speed Multipliers
| Type      | Multiplier |
|-----------|------------|
| STANDARD  | 1.0×       |
| EXPRESS   | 1.5×       |
| OVERNIGHT | 2.5×       |

### Weight Surcharge
- Threshold: 5.0 kg
- Surcharge: $0.50 per kg over threshold
- Formula: `max(0, totalWeightKg - 5.0) × 0.50`

### Dangerous Goods Surcharge
- Amount: $15.00 flat per order (not per item)
- Applied if ANY item in the order has `dangerousGood = true`
- **NEVER waived** — even free shipping promos do not remove this surcharge

### Free Shipping Conditions (priority order)
1. FREESHIP promo code AND valid/eligible → shipping = $0.00 (dangerous goods surcharge still applies)
2. Customer tier PLATINUM AND shippingType STANDARD AND no dangerous goods → shipping = $0.00

If neither condition met: `shippingCost = round((baseRate × multiplier) + weightSurcharge + dangerousSurcharge)`

---

## Promo Code Rules

### Eligibility Checks (in order — fail on first failure)
1. Code exists in catalog (ValidationService already checks this)
2. Code not expired: `orderDate < expiryDate` (strict less-than)
3. Customer tier in `eligibleTiers` (if non-null; null = all tiers)
4. If `newCustomerOnly`: account age < 30 days
5. For PERCENTAGE_IF_ABOVE: subtotal >= `minimumAmount`

### Promo Types and Effects
| Type                | Effect                                           |
|---------------------|--------------------------------------------------|
| PERCENTAGE_SUBTOTAL | discount = round(subtotal × value / 100)         |
| PERCENTAGE_IF_ABOVE | same as above if subtotal >= minimum; else fail  |
| FREE_SHIPPING       | shippingCost = $0 (dangerous surcharge still applies) |

### Promo Code Catalog
| Code     | Type               | Value | Min Amount | New Only | Tiers            | Expires    |
|----------|--------------------|-------|------------|----------|------------------|------------|
| SAVE10   | PERCENTAGE_SUBTOTAL| 10%   | -          | No       | All              | 2026-12-31 |
| SAVE20   | PERCENTAGE_SUBTOTAL| 20%   | -          | No       | All              | 2026-06-30 |
| FREESHIP | FREE_SHIPPING      | -     | -          | No       | All              | 2026-12-31 |
| BULK15   | PERCENTAGE_IF_ABOVE| 15%   | $200       | No       | All              | 2026-12-31 |
| NEWCUST  | PERCENTAGE_SUBTOTAL| 8%    | -          | Yes      | BRONZE only      | 2026-12-31 |
| GOLD20   | PERCENTAGE_SUBTOTAL| 20%   | -          | No       | GOLD, PLATINUM   | 2026-12-31 |

**Failed promo**: order proceeds without discount. Warning added to `OrderResult.warnings`. Not a rejection.

---

## Fraud Detection Rules

| Rule                    | Severity | Trigger                                                             | Effect on Order   |
|-------------------------|----------|---------------------------------------------------------------------|-------------------|
| HIGH_VALUE_NEW_CUSTOMER | HIGH     | grandTotal > $1,000 AND account age < 90 days                      | REJECTED          |
| PROMO_ABUSE             | MEDIUM   | promoCode applied AND grandTotal > $500 AND account age < 30 days  | PROCESSED+warning |
| SUSPICIOUS_ZONE         | MEDIUM   | shippingZone >= 4 AND tier=BRONZE AND grandTotal > $300            | PROCESSED+warning |
| BULK_PURCHASE           | LOW      | any item quantity >= 10 AND tier=BRONZE                            | PROCESSED+note    |

**⚠ CRITICAL**:
- Rules are evaluated INDEPENDENTLY (one triggering does not block others)
- Any HIGH severity flag → REJECTED (inventory reservation released)
- Multiple MEDIUM/LOW flags do NOT escalate to HIGH
- When REJECTED: inventory reservation is released before returning result
- BULK_PURCHASE fires at most ONCE per order regardless of how many items trigger it

### Account Age Calculation
```
accountAgeDays = DAYS_BETWEEN(customer.registrationDate, orderDate)
```
Where `customer.registrationDate` is a String "YYYY-MM-DD" (legacy format).
`orderDate` defaults to today if not provided.

---

## Inventory Rules

- Items are checked in order, but ALL items are checked before ANY stock is decremented
- If `allowPartialFulfillment = false`: any shortage → REJECTED (no stock decremented)
- If `allowPartialFulfillment = true`: out-of-stock items dropped, pricing recalculated for fulfilled items
- Stock reservation is performed BEFORE fraud check
- If REJECTED by fraud: stock reservation is RELEASED (restored)

---

## Order State Machine

```
PENDING
  → VALIDATED (all validation passes)
      → PRICED (line items calculated)
          → INVENTORY_CHECKED (stock reserved)
              → FRAUD_CHECKED
                  → PROCESSED (no HIGH fraud flags)
                  → REJECTED  (HIGH fraud flag → stock released)
          → REJECTED (inventory shortage)
  → REJECTED (validation failure)
```
