# Legacy Patterns — Migration Risk Register

> Extracted from: `Customer.java`, `MoneyUtils.java`, `OrderRepository.java`, `ProductRepository.java`  
> Purpose: Document every Java pattern that affects correctness, safety, or behaviour parity when migrating to Node.js.

---

## Pattern 1 — `double` Arithmetic for Monetary Values

**Source:** `MoneyUtils.java`

### What it does in Java
All monetary arithmetic uses Java primitive `double` (IEEE 754 64-bit float), not `BigDecimal`.  
`MoneyUtils.round()` mitigates floating-point drift by rounding intermediate values to 2 decimal places after every multiplication:

```java
public static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
}
```

`Math.round()` in Java uses HALF_UP semantics for positive values: `0.005 → 0.01`.  
The class comment explicitly acknowledges this is a known limitation: *"A production financial system should use BigDecimal."*

### Why it's a migration risk
- JavaScript `Math.round()` has **identical semantics** to Java's for positive values, so a direct port (`Math.round(x * 100) / 100`) produces the same results — but only for positive numbers.
- The HALF_UP guarantee breaks for **negative monetary values** in both Java and JavaScript using this pattern. Although negative totals should not occur in this system, a future discount or refund could silently produce wrong rounding.
- Any attempt to "improve" the Node.js implementation by switching to `Decimal.js` with `ROUND_HALF_UP` may produce different results at specific edge values due to differences in how each library handles floating-point representation internally.
- The test suite was written against `Math.round(x * 100) / 100` behaviour. Switching libraries without running `compare-outputs.sh` will not be caught until integration testing.

### Node.js equivalent

```js
// Must replicate exactly — do NOT switch to Decimal.js without running compare-outputs.sh
function round2(value) {
  return Math.round(value * 100) / 100;
}
```

Apply after **every individual multiplication step** — not once at the end of an expression.  
Four calls per line item: after `basePrice × discount`, after `× quantity`, after `× taxRate`, after `lineSubtotal + taxAmount`.

---

## Pattern 2 — `sumRounded()` — Incremental Rounding on Accumulation

**Source:** `MoneyUtils.java`

### What it does in Java
`MoneyUtils.sumRounded()` rounds the running total after each addition to prevent floating-point drift in long summation chains:

```java
public static double sumRounded(double... values) {
    double total = 0.0;
    for (double v : values) {
        total = round(total + v);
    }
    return total;
}
```

### Why it's a migration risk
- `OrderProcessingService` computes `subtotal` and `totalTax` using `stream().mapToDouble().sum()` (Java's built-in sum, **not** `sumRounded()`), then calls `MoneyUtils.round()` once on the result.
- If a future Node.js implementation uses `sumRounded()` as a general utility and applies it where `OrderProcessingService` uses a single round, the results will diverge for orders with many line items.
- The method exists and is part of the public API, but is not currently used for the aggregation that produces `subtotal` or `totalTax`. Its intended use context is ambiguous.

### Node.js equivalent

```js
// Only needed if replicating sumRounded() specifically
function sumRounded(values) {
  return values.reduce((total, v) => round2(total + v), 0);
}

// For subtotal/totalTax aggregation, match OrderProcessingService:
const subtotal = round2(lineItems.reduce((s, item) => s + item.lineSubtotal, 0));
const totalTax  = round2(lineItems.reduce((s, item) => s + item.taxAmount,   0));
```

---

## Pattern 3 — String-Typed Date Fields

**Source:** `Customer.java`

### What it does in Java
`Customer.registrationDate` is stored and transmitted as a plain `String` in `"YYYY-MM-DD"` format.  
This was a deliberate 2019 migration away from `java.util.Date` to avoid timezone ambiguity bugs.  
All consumers (`FraudDetectionService`, `PromotionEngine`) call `LocalDate.parse(registrationDate)` directly, which uses ISO-8601 by default and is timezone-free.

### Why it's a migration risk
- JavaScript's `new Date("2024-01-15")` parses ISO date-only strings as **UTC midnight**, then shifts them to local time when displayed or diffed — producing off-by-one-day errors in any timezone behind UTC.
- `dayjs("2024-01-15").diff(dayjs("2023-12-20"), 'day')` is also safe only when both inputs are parsed without time components.
- A developer unfamiliar with this history may use `new Date()` and introduce the exact timezone bug the 2019 migration was designed to eliminate.
- Account age calculations in `FraudDetectionService` and `PromotionEngine` are boundary-sensitive (threshold is 30 days / 90 days) — an off-by-one day error from timezone shift changes fraud outcomes and promo eligibility.

### Node.js equivalent

```js
// ALWAYS use dayjs for date parsing — NEVER new Date() on a "YYYY-MM-DD" string
const dayjs = require('dayjs');

const registrationDate = dayjs(customer.registrationDate, 'YYYY-MM-DD');
const orderDate        = dayjs(order.orderDate ?? dayjs().format('YYYY-MM-DD'), 'YYYY-MM-DD');
const accountAgeDays   = orderDate.diff(registrationDate, 'day');
```

Rule: treat all date fields as timezone-free calendar dates. Never convert to timestamps or Date objects.

---

## Pattern 4 — Billing Country vs. Shipping Zone Split

**Source:** `Customer.java`

### What it does in Java
`Customer.country` stores the **billing country** only.  
Shipping destination and shipping cost are determined entirely by `Order.shippingZone` (integer 1–5).  
The Javadoc explicitly marks this as *"known design debt from when international shipping was added."*

### Why it's a migration risk
- A developer reading `Customer` might assume `customer.country` determines shipping rates — it does not.
- Any Node.js shipping cost logic that reads `customer.country` instead of `order.shippingZone` will silently compute wrong costs with no error.
- If a future feature adds country-based shipping rates, the `customer.country` field is available but currently unused for that purpose.

### Node.js equivalent
Always use `order.shippingZone` (integer) for shipping cost calculation.  
`customer.country` is available on the customer object but must only be used for billing/tax jurisdiction purposes, not shipping.

```js
// CORRECT
const baseRate = ZONE_BASE_RATES[order.shippingZone] ?? 25.00;

// WRONG — do not do this
const baseRate = COUNTRY_RATES[customer.country];
```

---

## Pattern 5 — In-Memory Repository with Constructor-Seeded Data

**Source:** `ProductRepository.java`

### What it does in Java
`ProductRepository` seeds its in-memory `HashMap` with 10 hardcoded products at construction time (`seed()` called in the constructor).  
There is no database — the catalog is rebuilt fresh on every JVM startup.  
`OrderRepository` similarly uses an in-memory `synchronized HashMap`.

### Why it's a migration risk
- **State is lost on restart.** Any order saved to `OrderRepository` during a process run is gone when the process exits. `FraudDetectionService` depends on `OrderRepository` for velocity checks — in the CLI mode this means each invocation starts with zero order history.
- **Stock mutations are ephemeral.** `decrementStock()` modifies in-memory `Product` objects. Two successive CLI invocations of the same order can both succeed because stock is reset on the second JVM start.
- **The Node.js API server is long-lived.** Unlike the CLI (one JVM per invocation), the Node.js server and the Spring Boot Java API share process lifetime across multiple requests. Stock decrements and order history **accumulate** across requests and are not reset between calls. Parity tests must account for this.
- **Test isolation.** Integration tests that run multiple orders against the live Java API will deplete stock and may fail in order-dependent ways if not run against a freshly started Java server.

### Node.js equivalent
The Node.js layer calls the Java Spring Boot API for all state (products, customers, promo codes, inventory). It must NOT replicate the in-memory store. The Java API is the single source of state.

```
Node.js (stateless, call-through)
  → POST /api/v1/orders  →  Java Spring Boot API  →  in-memory store (Java)
```

For integration tests: restart the Java server between test suites to reset state, or use a test-isolation endpoint if one is added.

---

## Pattern 6 — `synchronized HashMap` for Thread Safety

**Source:** `OrderRepository.java`

### What it does in Java
`OrderRepository` wraps its `HashMap` with `Collections.synchronizedMap()`, acquired in 2017 as the idiomatic concurrency pattern of that era:

```java
private final Map<String, OrderResult> store = Collections.synchronizedMap(new HashMap<>());
```

This provides per-method synchronization (each individual call to `put`, `get`, `containsKey` is atomic), but **not** compound-operation atomicity (check-then-act across two method calls is not atomic).

`ProductRepository.decrementStock()` and `restoreStock()` use `synchronized` keyword on the method itself for the same reason.

### Why it's a migration risk
- Node.js is single-threaded (event loop) — there is no concurrent access to in-process objects. The synchronized pattern is simply not applicable and needs no equivalent.
- However, if the Java API is ever replaced with a real database backend, the check-then-decrement stock operation (`InventoryService` reads stock, then `ProductRepository.decrementStock()` writes it) is a **TOCTOU race** in a multi-threaded server environment. The current synchronized method only protects the write, not the read-write pair.
- Node.js callers making concurrent requests to the Java API can still trigger this race — one request could read "20 units available", another reads "20 units available", and both decrement, producing -N stock.

### Node.js equivalent
No synchronization primitives needed in Node.js for in-process state.  
For concurrent HTTP request safety against the Java API, the Node.js layer should treat inventory check and reservation as an atomic Java-side operation (which it currently is, per `InventoryService.checkAndReserve()`). Do not split the check and the reserve into two separate API calls.

---

## Pattern 7 — `orderId` Prefix Convention for Customer Lookup

**Source:** `OrderRepository.java`

### What it does in Java
`OrderResult` does not carry a `customerId` field. `OrderRepository.findByCustomer()` works around this by matching on `orderId` prefix:

```java
return o.getOrderId() != null && o.getOrderId().startsWith(customerId + "-");
```

This relies on an **undocumented naming convention**: all order IDs must be formatted as `"<customerId>-<suffix>"` (e.g., `"C001-ORD-001"`).  
The comment in the source says: *"In the real system this query hits an indexed database column. Here we rely on orderId convention."*

### Why it's a migration risk
- Any order whose `orderId` does not start with `customerId + "-"` will be invisible to `findByCustomer()` — fraud velocity checks will silently return empty history for that customer.
- There is no validation rule in `ValidationService` enforcing this naming convention.
- `FraudDetectionService` injects `OrderRepository` but never calls it (the velocity-check rule was never implemented — see `BUSINESS_RULES.md` Fraud ambiguity #5). If that rule is ever activated, the prefix convention becomes load-bearing.
- In Node.js/database migration, the `orderId` prefix workaround must be replaced with a proper `customerId` foreign key on the order record.

### Node.js equivalent
When implementing order persistence in Node.js (or a database-backed Java replacement):

```js
// Store customerId on the order record explicitly
await db.orders.insert({ orderId, customerId, ...result });

// Query by customerId directly — do NOT use prefix matching
const history = await db.orders.findAll({ where: { customerId } });
```

Do not port the prefix-matching hack. It is explicitly marked as a stub in the source.

---

## Pattern 8 — Legacy Category Codes (`LEGACY_CODE_MAP`)

**Source:** `ProductRepository.java`

### What it does in Java
Each `Product` carries two category representations:
- `category`: the canonical `ProductCategory` enum (`ELECTRONICS`, `CLOTHING`, etc.) used by all internal services
- `legacyCategoryCode`: a raw 4-character string from the v1 catalog API (`"ELEC"`, `"CLTH"`, `"FOOD"`, `"MEDI"`, `"GEN"`)

`LEGACY_CODE_MAP` provides the reverse mapping (`"ELEC"` → `ProductCategory.ELECTRONICS`).

The v1 API is the **origin** of catalog data if products are ever imported from an external system.

### Why it's a migration risk
- Any data pipeline that ingests products from the legacy v1 catalog must translate `legacyCategoryCode` → `category` using this map before passing products to pricing or tax logic. Failing to translate means wrong tax rates (e.g., `"ELEC"` passed as the category string will not match `"ELECTRONICS"` in the tax rate lookup).
- The map is `public static final` — it is part of the ProductRepository's public API and may be consumed outside this file.
- `"GEN"` maps to `GENERAL` (3-character code vs 4 for the others) — the inconsistency is easy to miss.

### Node.js equivalent

```js
const LEGACY_CODE_MAP = Object.freeze({
  ELEC: 'ELECTRONICS',
  CLTH: 'CLOTHING',
  FOOD: 'FOOD',
  MEDI: 'MEDICAL',
  GEN:  'GENERAL',   // NOTE: 3 chars, not 4
});

function resolveCategory(legacyCode, canonicalCategory) {
  // Prefer canonical if present; fall back to legacy code translation
  return canonicalCategory ?? LEGACY_CODE_MAP[legacyCode] ?? null;
}
```

Never pass a `legacyCategoryCode` value directly to tax rate or pricing lookups. Always resolve to the canonical category name first.

---

## Pattern 9 — Direct In-Place Stock Mutation

**Source:** `ProductRepository.java`

### What it does in Java
`decrementStock()` and `restoreStock()` mutate the `stockLevel` field **directly on the `Product` object** held in the repository's `HashMap`. There is no event, no audit trail, and no version check:

```java
p.setStockLevel(p.getStockLevel() - qty);  // decrementStock
p.setStockLevel(p.getStockLevel() + qty);  // restoreStock
```

`decrementStock()` throws `IllegalStateException` if stock goes negative — this is a second-check guard, not the primary availability check (which lives in `InventoryService`).

### Why it's a migration risk
- The two-phase check (read stock in `InventoryService`, decrement stock in `ProductRepository`) is not atomic at the service level — only the write step is synchronized. In a concurrent server context, this is a TOCTOU race.
- `restoreStock()` is called by `InventoryService.releaseReservation()` on fraud rejection. If this call fails or is missed, stock is permanently decremented even for rejected orders.
- There is no concept of a "reserved" state — stock is either available or decremented. A reservation that has been made but not yet confirmed cannot be distinguished from a confirmed fulfilment.
- In Node.js migration, the Java API must remain the single authority for stock mutation. Node.js must **never** attempt to compute or cache stock levels locally.

### Node.js equivalent
Node.js has no stock state. All inventory operations are delegated to the Java API:

```
Node.js → POST /api/v1/orders (Java handles check + reserve atomically)
        ← { status: "PROCESSED" | "REJECTED", ... }
```

Never split the inventory check and reservation into two separate calls from Node.js.

---

## Pattern 10 — `CustomerTier` as a Java Enum

**Source:** `Customer.java` (field: `CustomerTier tier`)

### What it does in Java
`CustomerTier` is a Java enum. Jackson (the JSON serializer) serializes and deserializes it by name — `"BRONZE"`, `"SILVER"`, `"GOLD"`, `"PLATINUM"`. An unknown string value throws a deserialization exception before the order reaches any service.

### Why it's a migration risk
- JavaScript has no native enum type. An unrecognized tier string (e.g., `"bronze"`, `"VIP"`) passes all JavaScript type checks silently.
- All tier lookups in the pricing and shipping logic use the string as a map key — a lowercase `"gold"` would return `undefined`, producing `NaN` or `0` in arithmetic without any error.
- `ValidationService` does not validate tier (it's read from the customer record, not from order input). The repository lookup provides the tier — if the repository is in-memory with valid data, the enum is always valid. In a database-backed system, stale or migrated data could introduce an unknown tier.

### Node.js equivalent

```js
const VALID_TIERS = Object.freeze(['BRONZE', 'SILVER', 'GOLD', 'PLATINUM']);

function assertValidTier(tier) {
  if (!VALID_TIERS.includes(tier)) {
    throw new Error(`Unknown CustomerTier: ${tier}`);
  }
}
```

All tier lookups must use the exact uppercase string. Add validation at the customer-resolution step, not just at the order input boundary.

---

## Summary Table

| # | Pattern | Source File | Risk Level | Node.js Action |
|---|---------|------------|-----------|---------------|
| 1 | `double` monetary arithmetic via `Math.round(x*100)/100` | `MoneyUtils.java` | **HIGH** — wrong values if changed | Replicate exactly as `round2()`; do not switch to Decimal.js without running `compare-outputs.sh` |
| 2 | `sumRounded()` incremental accumulation | `MoneyUtils.java` | MEDIUM — not used in main aggregation; risk if added | Match `OrderProcessingService` pattern: single `round2()` on final sum |
| 3 | String-typed dates `"YYYY-MM-DD"` | `Customer.java` | **HIGH** — off-by-one day in any non-UTC timezone | Always use `dayjs`; never `new Date()` on date-only strings |
| 4 | `customer.country` ≠ shipping destination | `Customer.java` | **HIGH** — silent wrong shipping cost | Use `order.shippingZone` for shipping; `customer.country` is billing only |
| 5 | In-memory constructor-seeded repository | `ProductRepository.java` | **HIGH** — state lost on restart; stock/history diverge | Node.js is stateless; Java API owns all state; restart Java between test suites |
| 6 | `synchronized HashMap` thread safety | `OrderRepository.java` | MEDIUM — TOCTOU gap for concurrent requests | No sync needed in Node.js; don't split check-and-reserve across two API calls |
| 7 | `orderId` prefix convention for customer lookup | `OrderRepository.java` | MEDIUM — silent empty results if violated | Replace with proper `customerId` FK in any real persistence layer |
| 8 | Legacy category codes (`LEGACY_CODE_MAP`) | `ProductRepository.java` | MEDIUM — wrong tax rates if untranslated | Always resolve legacy code → canonical category before pricing/tax lookups |
| 9 | Direct in-place stock mutation (no reservation state) | `ProductRepository.java` | **HIGH** — no atomicity; no reserved-vs-committed state | Never split check and reserve; Java API owns stock atomicity |
| 10 | `CustomerTier` as Java enum | `Customer.java` | MEDIUM — silent `undefined` on unknown tier | Use explicit allowlist validation; all lookups use uppercase exact-match strings |
