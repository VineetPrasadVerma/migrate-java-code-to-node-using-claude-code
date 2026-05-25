# Data Models

> All 14 model classes from `java-order-system/src/main/java/com/company/orders/model/`.  
> Jackson (default config) handles JSON serialization — enums serialize as their name string,  
> `boolean`/`int`/`double` primitives cannot be `null`, reference types can be `null`.

---

## Enums

### `CustomerTier`

| Value | JSON string | Legacy integer (pre-v2) | Tier discount |
|-------|-------------|------------------------|--------------|
| `BRONZE` | `"BRONZE"` | `0` | 0% |
| `SILVER` | `"SILVER"` | `1` | 5% |
| `GOLD` | `"GOLD"` | `2` | 10% |
| `PLATINUM` | `"PLATINUM"` | `3` | 15% |

> Legacy integer mapping (`0`–`3`) lives in `CustomerRepository.fromLegacyCode()`.  
> Node.js must validate against the exact uppercase string values — `"gold"` will not match.

---

### `OrderStatus`

| Value | JSON string | When set |
|-------|-------------|---------|
| `PENDING` | `"PENDING"` | Declared; not set on `OrderResult` by current `OrderProcessingService` |
| `VALIDATED` | `"VALIDATED"` | Declared; not set on `OrderResult` by current code |
| `PRICED` | `"PRICED"` | Declared; not set on `OrderResult` by current code |
| `INVENTORY_CHECKED` | `"INVENTORY_CHECKED"` | Declared; not set on `OrderResult` by current code |
| `FRAUD_CHECKED` | `"FRAUD_CHECKED"` | Declared; not set on `OrderResult` by current code |
| `PROCESSED` | `"PROCESSED"` | Final success outcome |
| `REJECTED` | `"REJECTED"` | Any rejection (validation, inventory, or fraud) |

> ⚠️ Only `PROCESSED` and `REJECTED` are ever written to `OrderResult` in the current implementation.  
> The intermediate states (`PENDING` → `FRAUD_CHECKED`) are documented in the Javadoc state machine  
> but are not emitted — they exist for a future finer-grained status tracking feature.

---

### `ProductCategory`

| Value | JSON string | Legacy code | Tax rate |
|-------|-------------|------------|---------|
| `ELECTRONICS` | `"ELECTRONICS"` | `"ELEC"` | 18% |
| `CLOTHING` | `"CLOTHING"` | `"CLTH"` | 5% |
| `FOOD` | `"FOOD"` | `"FOOD"` | 0% |
| `MEDICAL` | `"MEDICAL"` | `"MEDI"` | 12% |
| `GENERAL` | `"GENERAL"` | `"GEN"` | 8% |

> ⚠️ Legacy code `"GEN"` is 3 characters; all others are 4.  
> Never pass a legacy code to tax/pricing logic — always resolve to the canonical enum name first.

---

### `ShippingType`

| Value | JSON string | Speed multiplier |
|-------|-------------|-----------------|
| `STANDARD` | `"STANDARD"` | 1.0× |
| `EXPRESS` | `"EXPRESS"` | 1.5× |
| `OVERNIGHT` | `"OVERNIGHT"` | 2.5× |

> PLATINUM customers receive free STANDARD shipping (unless dangerous goods present).

---

### `FraudFlag`

| Value | JSON string | Severity | Default description |
|-------|-------------|---------|---------------------|
| `HIGH_VALUE_NEW_CUSTOMER` | `"HIGH_VALUE_NEW_CUSTOMER"` | `"HIGH"` | `"Order total exceeds $1,000 threshold for accounts under 90 days old"` |
| `PROMO_ABUSE` | `"PROMO_ABUSE"` | `"MEDIUM"` | `"Promotional code applied to high-value order from account under 30 days old"` |
| `SUSPICIOUS_ZONE` | `"SUSPICIOUS_ZONE"` | `"MEDIUM"` | `"High-value order shipping to zone 4+ from a Bronze-tier account"` |
| `BULK_PURCHASE` | `"BULK_PURCHASE"` | `"LOW"` | `"Single product quantity >= 10 units in order from Bronze-tier account"` |

> `severity` is a plain `String` field on the enum (`"HIGH"` / `"MEDIUM"` / `"LOW"`).  
> `FraudFlagResult` copies the severity string into its own `severity` field at construction time — the two are not linked after that.  
> Multiple MEDIUM flags on one order do NOT escalate to HIGH.

---

## Input Models (Request)

### `Order`

Primary input submitted by the client.

| Field | Java type | JSON key | Serialized as | Required | Constraints | Notes |
|-------|-----------|----------|--------------|----------|-------------|-------|
| `orderId` | `String` | `orderId` | string | Yes | non-null, non-blank | Validated by Rule 1 in ValidationService |
| `customerId` | `String` | `customerId` | string | Yes | non-null, non-blank, must exist in CustomerRepository | Validated by Rule 2 |
| `items` | `List<OrderItem>` | `items` | array of objects | Yes | non-null, non-empty | Validated by Rule 3; see `OrderItem` model |
| `promoCode` | `String` | `promoCode` | string or `null` | No | null if absent; if present, must exist in PromoCodeRepository | Expiry/eligibility checked later by PromotionEngine |
| `shippingZone` | `int` (primitive) | `shippingZone` | number | Yes | integer 1–5 inclusive | Defaults to `0` if absent from JSON (fails Rule 7 validation); zone-to-address resolution done by caller |
| `shippingType` | `ShippingType` (enum) | `shippingType` | string: `"STANDARD"` \| `"EXPRESS"` \| `"OVERNIGHT"` | Yes | non-null | Null check enforced by Rule 8; unknown strings throw on deserialization |
| `allowPartialFulfillment` | `boolean` (primitive) | `allowPartialFulfillment` | boolean | No | defaults to `false` if absent | Controls InventoryService behaviour; no validation rule enforces its presence |
| `orderDate` | `String` | `orderDate` | string `"YYYY-MM-DD"` or `null` | No | ISO date string; defaults to today if null/absent | Used for promo expiry and fraud age calculations |

---

### `OrderItem`

One line in an incoming order.

| Field | Java type | JSON key | Serialized as | Required | Constraints | Notes |
|-------|-----------|----------|--------------|----------|-------------|-------|
| `productId` | `String` | `productId` | string | Yes | non-null, non-blank, must exist in ProductRepository | Validated per-item; duplicates within the same order rejected |
| `quantity` | `int` (primitive) | `quantity` | number | Yes | 1–100 inclusive | Defaults to `0` if absent (fails Rule 5a); upper limit 100 is a bare literal in ValidationService |

---

## Reference / Catalog Models

### `Customer`

Resolved internally from `customerId`; never sent directly by the client.

| Field | Java type | JSON key | Serialized as | Required | Constraints | Notes |
|-------|-----------|----------|--------------|----------|-------------|-------|
| `id` | `String` | `id` | string | Yes | — | Primary key in CustomerRepository |
| `name` | `String` | `name` | string or `null` | No | — | Display only; not used in any business logic |
| `email` | `String` | `email` | string or `null` | No | — | Display only; not used in any business logic |
| `tier` | `CustomerTier` (enum) | `tier` | string: `"BRONZE"` \| `"SILVER"` \| `"GOLD"` \| `"PLATINUM"` | Yes | Used in pricing, shipping, promo, fraud logic | Assigned by CRM; not computed here |
| `registrationDate` | `String` | `registrationDate` | string `"YYYY-MM-DD"` | Yes (for fraud/promo) | ⚠️ Legacy string date — was `java.util.Date` until 2019 migration; must use dayjs, not `new Date()` | Parsed by FraudDetectionService and PromotionEngine for account age |
| `billingAddress` | `Address` | `billingAddress` | object or `null` | No | Nested `Address` object | Billing address only; does NOT determine shipping cost |
| `country` | `String` | `country` | string or `null` | No | ⚠️ Billing country only — NOT shipping destination | Shipping determined by `Order.shippingZone`; known design debt |

---

### `Address`

Nested in `Customer.billingAddress`. No business logic uses any `Address` field directly.

| Field | Java type | JSON key | Serialized as | Required | Constraints | Notes |
|-------|-----------|----------|--------------|----------|-------------|-------|
| `street` | `String` | `street` | string or `null` | No | — | |
| `city` | `String` | `city` | string or `null` | No | — | |
| `state` | `String` | `state` | string or `null` | No | — | |
| `postalCode` | `String` | `postalCode` | string or `null` | No | — | |
| `country` | `String` | `country` | string or `null` | No | — | Billing country; separate from `Customer.country` (which is also billing) |

> ⚠️ `Address` fields are purely informational in the current system.  
> Shipping zone resolution from address is done by the **caller** before submitting the order.

---

### `Product`

Resolved internally; not sent by the client.

| Field | Java type | JSON key | Serialized as | Required | Constraints | Notes |
|-------|-----------|----------|--------------|----------|-------------|-------|
| `id` | `String` | `id` | string | Yes | — | e.g. `"P001"` |
| `name` | `String` | `name` | string | Yes | — | Display name |
| `category` | `ProductCategory` (enum) | `category` | string: `"ELECTRONICS"` \| `"CLOTHING"` \| `"FOOD"` \| `"MEDICAL"` \| `"GENERAL"` | Yes | Used for tax rate lookup | Canonical form — always prefer over `legacyCategoryCode` |
| `legacyCategoryCode` | `String` | `legacyCategoryCode` | string or `null` | No | ⚠️ Legacy v1 catalog code: `"ELEC"`, `"CLTH"`, `"FOOD"`, `"MEDI"`, `"GEN"` | Kept for backward compatibility; must translate to `category` before use |
| `basePrice` | `double` (primitive) | `basePrice` | number | Yes | > 0 (assumed) | Used as starting point for PricingEngine; defaults to `0.0` if absent |
| `weightKg` | `double` (primitive) | `weightKg` | number | Yes | >= 0 | Used by ShippingCalculator for weight surcharge |
| `dangerousGood` | `boolean` (primitive) | `dangerousGood` | boolean | Yes | — | If `true`, $15 flat surcharge added; never waived even under free shipping |
| `stockLevel` | `int` (primitive) | `stockLevel` | number | Yes | >= 0 | ⚠️ Mutable in-memory; modified directly by InventoryService via ProductRepository; not thread-safe without `synchronized` |

---

### `PromoCode`

Internal catalog model; not sent by the client (client submits only the code string).

| Field | Java type | JSON key | Serialized as | Required | Constraints | Notes |
|-------|-----------|----------|--------------|----------|-------------|-------|
| `code` | `String` | `code` | string | Yes | Normalized to `trim().toUpperCase()` before lookup | e.g. `"SAVE10"` |
| `type` | `String` | `type` | string | Yes | One of `"PERCENTAGE_SUBTOTAL"`, `"PERCENTAGE_IF_ABOVE"`, `"FREE_SHIPPING"` | ⚠️ Stored as plain `String`, not enum — unknown values fall through to `invalid` in PromotionEngine |
| `discountValue` | `double` (primitive) | `discountValue` | number | Conditional | Percentage (e.g. `10` = 10%); ignored for `FREE_SHIPPING` | ⚠️ Stored as a percentage integer/float, NOT a decimal fraction — divide by 100 in formula |
| `minimumAmount` | `double` (primitive) | `minimumAmount` | number | Conditional | Only checked for `PERCENTAGE_IF_ABOVE` | Defaults to `0.0` if absent; `0.0` makes `PERCENTAGE_IF_ABOVE` behave like `PERCENTAGE_SUBTOTAL` |
| `expiryDate` | `String` | `expiryDate` | string `"YYYY-MM-DD"` | Yes | ⚠️ Code is invalid if `orderDate >= expiryDate` (expiry day is itself expired) | Parsed with `LocalDate.parse()` — dayjs in Node.js |
| `newCustomerOnly` | `boolean` (primitive) | `newCustomerOnly` | boolean | Yes | If `true`, account age must be < 30 days | Defaults to `false` if absent from JSON |
| `eligibleTiers` | `List<CustomerTier>` | `eligibleTiers` | array of tier strings or `null` | No | `null` = all tiers eligible; empty list `[]` also treated as all tiers eligible | ⚠️ `null` and `[]` both mean "no restriction" — see BUSINESS_RULES.md Promo ambiguity #1 |

---

## Output / Result Models

### `OrderResult`

The complete response written to stdout (CLI) or returned by the API.

| Field | Java type | JSON key | Serialized as | Always present | Notes |
|-------|-----------|----------|--------------|----------------|-------|
| `orderId` | `String` | `orderId` | string | Yes | Echo of input `orderId` |
| `status` | `OrderStatus` (enum) | `status` | `"PROCESSED"` or `"REJECTED"` | Yes | ⚠️ Only these two values are ever set; other `OrderStatus` values are declared but unused |
| `lineItems` | `List<ProcessedOrderItem>` | `lineItems` | array of objects | Conditionally | Empty list `[]` on validation-rejected orders (no pricing computed); populated on all other outcomes |
| `subtotal` | `double` (primitive) | `subtotal` | number | Conditionally | `0.0` on validation-rejected orders; post-tier/volume discount, pre-tax, pre-promo |
| `totalTax` | `double` (primitive) | `totalTax` | number | Conditionally | `0.0` on validation-rejected orders |
| `promoCode` | `String` | `promoCode` | string or `null` | No | `null` if no promo applied or promo was invalid |
| `promoDiscount` | `double` (primitive) | `promoDiscount` | number | Conditionally | `0.0` if no valid promo |
| `shippingCost` | `double` (primitive) | `shippingCost` | number | Conditionally | `0.0` on validation-rejected orders |
| `grandTotal` | `double` (primitive) | `grandTotal` | number | Conditionally | `0.0` on validation-rejected orders; formula: `subtotal − promoDiscount + totalTax + shippingCost` |
| `fraudFlags` | `List<FraudFlagResult>` | `fraudFlags` | array of objects | Yes | Empty list `[]` if no fraud flags; present on REJECTED (fraud) and PROCESSED (with warnings) |
| `warnings` | `List<String>` | `warnings` | array of strings | Yes | Initialized as empty list `[]` in constructor; includes invalid promo messages, partial fulfilment drops, and MEDIUM/LOW fraud flags |
| `errors` | `List<String>` | `errors` | array of strings | Yes | Initialized as empty list `[]`; populated on validation failures and inventory failures |
| `message` | `String` | `message` | string | Yes | Human-readable outcome summary |
| `processedAt` | `String` | `processedAt` | string `"yyyy-MM-dd'T'HH:mm:ss"` (no timezone) | Yes | ⚠️ ISO datetime **without** timezone suffix — local server time; not UTC |

> **Default values on validation-rejected orders:** Only `orderId`, `status`, `errors`, `message`, and `processedAt` are populated. All numeric fields are `0.0` (primitive defaults), `lineItems`/`fraudFlags`/`warnings` are empty lists.

---

### `OrderResult.FraudFlagResult` (nested class)

Nested inside `OrderResult`. Present in `fraudFlags` list.

| Field | Java type | JSON key | Serialized as | Notes |
|-------|-----------|----------|--------------|-------|
| `flag` | `FraudFlag` (enum) | `flag` | string: `"HIGH_VALUE_NEW_CUSTOMER"` \| `"PROMO_ABUSE"` \| `"SUSPICIOUS_ZONE"` \| `"BULK_PURCHASE"` | The specific fraud rule that fired |
| `severity` | `String` | `severity` | `"HIGH"` \| `"MEDIUM"` \| `"LOW"` | ⚠️ Plain string copy of `FraudFlag.getSeverity()` set at construction — not the enum's live field |
| `description` | `String` | `description` | string | Full context message generated by FraudDetectionService (includes actual values, not default template) |

> ⚠️ `hasHighSeverityFlag()` compares `"HIGH".equals(f.getSeverity())` — string equality on the `severity` field, not enum comparison on `flag`.  
> Node.js: `flags.some(f => f.severity === 'HIGH')`.

---

### `ProcessedOrderItem`

One line in `OrderResult.lineItems`. Produced by `PricingEngine`.

| Field | Java type | JSON key | Serialized as | Notes |
|-------|-----------|----------|--------------|-------|
| `productId` | `String` | `productId` | string | |
| `productName` | `String` | `productName` | string | |
| `category` | `ProductCategory` (enum) | `category` | string: e.g. `"ELECTRONICS"` | |
| `quantity` | `int` (primitive) | `quantity` | number | Fulfilled quantity; may be less than ordered on partial fulfilment |
| `baseUnitPrice` | `double` (primitive) | `baseUnitPrice` | number | Original catalog price before any discount |
| `tierDiscountRate` | `double` (primitive) | `tierDiscountRate` | number | e.g. `0.10` for GOLD; applied uniformly across all lines |
| `volumeDiscountRate` | `double` (primitive) | `volumeDiscountRate` | number | e.g. `0.03` for qty 5–9; per-line based on this item's quantity |
| `discountedUnitPrice` | `double` (primitive) | `discountedUnitPrice` | number | `round(baseUnitPrice × (1 − combinedDiscount))` |
| `taxRate` | `double` (primitive) | `taxRate` | number | e.g. `0.18` for ELECTRONICS |
| `taxAmount` | `double` (primitive) | `taxAmount` | number | `round(lineSubtotal × taxRate)` — total tax for all units on this line |
| `lineSubtotal` | `double` (primitive) | `lineSubtotal` | number | `round(discountedUnitPrice × quantity)` — **pre-tax**; used in order-level `subtotal` aggregation |
| `lineTotal` | `double` (primitive) | `lineTotal` | number | `round(lineSubtotal + taxAmount)` — ⚠️ display field; `OrderProcessingService` does NOT use this for `grandTotal` (it sums `lineSubtotal` and `taxAmount` separately) |

> ⚠️ `lineTotal` is a display-only convenience field. The authoritative aggregation in `OrderProcessingService` is:  
> `subtotal = round(Σ lineSubtotal)` and `totalTax = round(Σ taxAmount)` independently.

---

### `PromotionResult`

Internal result of `PromotionEngine.applyPromotion()`. Not part of the public API response directly  
(its fields are merged into `OrderResult` by `OrderProcessingService`).

| Field | Java type | JSON key | Serialized as | Notes |
|-------|-----------|----------|--------------|-------|
| `appliedCode` | `String` | `appliedCode` | string or `null` | `null` when `noPromo()` |
| `discountAmount` | `double` (primitive) | `discountAmount` | number | `0.0` for invalid or free-shipping promos |
| `freeShipping` | `boolean` (primitive) | `freeShipping` | boolean | `true` only for `FREE_SHIPPING` type; passed directly to `ShippingCalculator` |
| `valid` | `boolean` (primitive) | `valid` | boolean | `true` even when `noPromo()` — "no promo" is valid; `false` only when code was rejected |
| `invalidReason` | `String` | `invalidReason` | string or `null` | Human-readable rejection reason; becomes a `warnings` entry in `OrderResult` |

> ⚠️ `valid = true` when no promo code was provided (`noPromo()`). The orchestrator checks  
> `promoResult.isValid()` to decide whether to add to warnings — `noPromo()` does not add a warning.

---

## Cross-Model Notes

### All date fields in the system

| Model | Field | Format | Timezone | Parsed by |
|-------|-------|--------|----------|-----------|
| `Order` | `orderDate` | `"YYYY-MM-DD"` | None (calendar date) | `LocalDate.parse()` in PromotionEngine, FraudDetectionService |
| `Customer` | `registrationDate` | `"YYYY-MM-DD"` | None (calendar date) | `LocalDate.parse()` in PromotionEngine, FraudDetectionService |
| `PromoCode` | `expiryDate` | `"YYYY-MM-DD"` | None (calendar date) | `LocalDate.parse()` in PromotionEngine |
| `OrderResult` | `processedAt` | `"yyyy-MM-dd'T'HH:mm:ss"` | ⚠️ Local server time, no `Z` or offset | Set in `OrderProcessingService` via `LocalDateTime.now()` |

> Node.js rule: use `dayjs(str, 'YYYY-MM-DD')` for all date-only fields.  
> Use `dayjs(str, 'YYYY-MM-DDTHH:mm:ss')` for `processedAt`.  
> **Never** use `new Date()` on any of these strings.

---

### All monetary / numeric fields

| Model | Field | Java type | Can be 0.0? | Can be negative? |
|-------|-------|-----------|-------------|-----------------|
| `Product` | `basePrice` | `double` | No (assumed > 0) | No |
| `ProcessedOrderItem` | `discountedUnitPrice` | `double` | Theoretically (100% discount) | No |
| `ProcessedOrderItem` | `lineSubtotal` | `double` | If qty = 0 (blocked by validation) | No |
| `ProcessedOrderItem` | `taxAmount` | `double` | Yes (FOOD) | No |
| `ProcessedOrderItem` | `lineTotal` | `double` | If FOOD + zero discount | No |
| `OrderResult` | `subtotal` | `double` | On validation-reject | No |
| `OrderResult` | `promoDiscount` | `double` | Yes (no promo or FREE_SHIPPING) | No |
| `OrderResult` | `shippingCost` | `double` | Yes (free shipping) | No |
| `OrderResult` | `grandTotal` | `double` | On validation-reject | No |
| `PromoCode` | `discountValue` | `double` | Technically | No |

> All monetary values are `double` primitives rounded to 2dp via `Math.round(x * 100) / 100`.  
> `grandTotal` cannot be negative under normal inputs — if `promoDiscount > subtotal + totalTax + shippingCost` that would produce a negative, but no validation prevents this edge case.

---

### Enum serialization summary

| Enum | Values | JSON representation | ⚠️ Notes |
|------|--------|--------------------|----|
| `CustomerTier` | BRONZE, SILVER, GOLD, PLATINUM | Exact name string | Legacy integers 0–3 in pre-v2 data |
| `OrderStatus` | PENDING … REJECTED | Exact name string | Only PROCESSED/REJECTED used in practice |
| `ProductCategory` | ELECTRONICS … GENERAL | Exact name string | Legacy codes: ELEC/CLTH/FOOD/MEDI/GEN |
| `ShippingType` | STANDARD, EXPRESS, OVERNIGHT | Exact name string | null defaults to STANDARD multiplier |
| `FraudFlag` | 4 values | Exact name string | Carries `severity` String and `defaultDescription` String as enum fields |
| `PromoCode.type` | 3 values | **Plain String, not enum** | Unknown values → `invalid` in PromotionEngine |
| `FraudFlagResult.severity` | HIGH, MEDIUM, LOW | **Plain String, not enum** | Copied from `FraudFlag.getSeverity()` at construction |
