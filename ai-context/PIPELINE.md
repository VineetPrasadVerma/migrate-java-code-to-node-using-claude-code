# Order Processing Pipeline

> Source of truth: `OrderProcessingService.processOrder()` (Java)  
> Entry point: `Main.main()` — reads JSON from file arg or stdin, prints `OrderResult` JSON to stdout.

---

## Pipeline Stages

### Stage 1 — Input Parsing
**Service/Method:** `Main.main()` → `JsonUtils.fromFile()` / `JsonUtils.fromJson()`

**Data In:** Raw JSON string (from file path argument or stdin)

**Data Out:** Hydrated `Order` object (orderId, customerId, items, promoCode, orderDate, shippingAddress, allowPartialFulfillment, etc.)

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| Malformed / unparseable JSON | Exception thrown → stderr message + exit code 1 (no `OrderResult` produced) |
| File not found (when file arg used) | Exception thrown → stderr + exit code 1 |

**Reversible?** N/A — nothing has been mutated yet.

---

### Stage 2 — Validation
**Service/Method:** `ValidationService.validate(order)`

**Data In:** `Order` object

**Data Out:** `ValidationResult` (isValid flag + list of error strings)

**What is checked:**
- Customer exists in `CustomerRepository`
- All product IDs exist in `ProductRepository`
- All quantities are positive
- Promo code exists in `PromoCodeRepository` (if provided; invalid code is non-fatal at this stage — see Stage 4)
- Any other structural constraints enforced by `ValidationService`

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| Any validation error | `buildRejected()` called immediately; result persisted via `orderRepository.save()`; pipeline **halts** — no pricing is computed |

**Reversible?** Yes — no side effects have occurred; nothing to undo.

---

### Stage 3 — Entity Resolution
**Service/Method:** `CustomerRepository.findById()`, `ProductRepository.findByIds()`

**Data In:** `customerId` and list of `productId` strings from the `Order`

**Data Out:** Hydrated `Customer` object; `Map<String, Product>` keyed by product ID

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| Customer or product not found | Would have been caught in Stage 2; reaching this stage guarantees both resolve successfully |

**Reversible?** Yes — read-only lookups; no state changed.

---

### Stage 4 — Pricing (Line Items)
**Service/Method:** `PricingEngine.calculateLineItems(items, customer, products)`

**Data In:** Raw `List<OrderItem>`, `Customer` (tier, etc.), `Map<String, Product>` (unit price, tax rate, etc.)

**Data Out:** `List<ProcessedOrderItem>` — each item decorated with:
- `unitPrice` (post tier/volume discount)
- `lineSubtotal` (pre-tax, post discount)
- `taxAmount`

Also computed here:
- `subtotal` = Σ `lineSubtotal` (HALF_UP rounded)
- `totalTax` = Σ `taxAmount` (HALF_UP rounded)

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| No explicit failure path — inputs validated upstream | — |

**Reversible?** Yes — pure calculation; no state changed.

---

### Stage 5 — Promotion Application
**Service/Method:** `PromotionEngine.applyPromotion(promoCode, subtotal, customer, orderDate)`

**Data In:** Promo code string (nullable), `subtotal`, `Customer`, `orderDate`

**Data Out:** `PromotionResult` containing:
- `isValid` flag
- `discountAmount` (applied against subtotal only, **not** against tax)
- `appliedCode`
- `isFreeShipping` flag
- `invalidReason` (if not valid)

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| Invalid/expired/inapplicable promo code | `isValid = false`; warning added to `warnings` list; `promoDiscount` set to 0.0 — pipeline **continues** |

**Reversible?** Yes — no state changed.

---

### Stage 6 — Shipping Calculation
**Service/Method:** `ShippingCalculator.calculate(order, products, customer, isFreeShipping)`

**Data In:** `Order` (shipping address / zone), `Map<String, Product>` (weights/dimensions), `Customer` (tier), `isFreeShipping` boolean (from promo result)

**Data Out:** `shippingCost` (double, HALF_UP rounded)

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| No explicit failure path defined | — |

**Note:** If the promo code granted free shipping (`isFreeShipping = true`), the calculator returns 0.0.

**Reversible?** Yes — pure calculation; no state changed.

---

### Stage 7 — Grand Total Computation
**Service/Method:** Inline arithmetic in `OrderProcessingService.processOrder()`

**Data In:** `subtotal`, `promoDiscount`, `totalTax`, `shippingCost`

**Data Out:** `grandTotal` (double, HALF_UP rounded)

**Formula:**
```
grandTotal = subtotal − promoDiscount + totalTax + shippingCost
```

**Failure Modes:** None — pure arithmetic.

**Reversible?** N/A — no side effects.

---

### Stage 8 — Inventory Check & Reservation
**Service/Method:** `InventoryService.checkAndReserve(items, allowPartialFulfillment)`

**Data In:** `List<OrderItem>` (productId + quantity), `allowPartialFulfillment` flag

**Data Out:** `InventoryResult` containing:
- `isAvailable` flag
- `fulfilledItems` (the subset that could be reserved)
- `unavailableMessages` (per-item shortage descriptions)

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| Any item out of stock AND `allowPartialFulfillment = false` | `isAvailable = false` → REJECTED; pricing **is** included in the result; **no stock is reserved** |
| Some items out of stock AND `allowPartialFulfillment = true` | `isAvailable = true`; only available items reserved; prices **recalculated** for reduced item set; warnings added; pipeline continues |

**Partial-fulfillment recalculation:** When items are dropped, `PricingEngine`, `PromotionEngine`, and `ShippingCalculator` are all re-invoked for the fulfilled subset, producing updated `finalSubtotal`, `finalTax`, `finalPromoDiscount`, `finalShippingCost`, and `finalGrandTotal`.

**Reversible?** Yes (conditionally) — reservations are released by `inventoryService.releaseReservation()` if a subsequent fraud HIGH flag is raised.

---

### Stage 9 — Fraud Detection
**Service/Method:** `FraudDetectionService.evaluate(order, customer, finalGrandTotal, orderDate)`

**Data In:** `Order`, `Customer`, `finalGrandTotal`, `orderDate`; internally queries `OrderRepository` for order history.

**Data Out:** `List<FraudFlagResult>` — each flag has `severity` (HIGH / MEDIUM / LOW) and `description`.

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| One or more HIGH severity flags | `fraudRejected = true` → `inventoryService.releaseReservation()` called → REJECTED result persisted |
| Only MEDIUM / LOW flags | Pipeline continues to PROCESSED; flags surfaced as warnings in the result |
| No flags | Pipeline continues cleanly to PROCESSED |

**Reversible?** The inventory reservation is explicitly released on HIGH-severity rejection — this is the **only** explicit compensation/rollback in the pipeline.

---

### Stage 10 — Finalise & Persist
**Service/Method:** `OrderRepository.save(result)` (called on every terminal path — REJECTED or PROCESSED)

**Data In:** Fully assembled `OrderResult`

**Data Out:** Persisted `OrderResult`; same object returned to caller / serialised to stdout.

**Failure Modes:**
| Failure | Effect |
|---------|--------|
| Persistence failure | Exception propagates to `Main.main()` → stderr + exit code 1 |

**Reversible?** No — this is the commit point. Inventory reservation is already held (or has already been released for fraud rejections).

---

## Grand Total Formula (summary)

```
grandTotal = subtotal − promoDiscount + totalTax + shippingCost

  subtotal       = Σ lineItem.lineSubtotal   (pre-tax, post tier/volume discount)
  totalTax       = Σ lineItem.taxAmount      (computed on pre-promo prices)
  promoDiscount  = discount applied to subtotal only (not to tax)
  shippingCost   = 0 if free-shipping promo active, otherwise zone/weight based
```

All monetary arithmetic uses `MoneyUtils.round()` — HALF_UP, 2 decimal places.

---

## State Machine

```
                         ┌─────────────────────────────────────────────────────┐
   JSON Input            │                                                     │
   (file / stdin)        │              ORDER PROCESSING PIPELINE              │
        │                │                                                     │
        ▼                └─────────────────────────────────────────────────────┘
  ┌───────────┐
  │  PARSING  │  parse error → stderr + exit(1)  [no OrderResult]
  └─────┬─────┘
        │ Order object
        ▼
  ┌───────────┐
  │  PENDING  │  (implicit initial state)
  └─────┬─────┘
        │
        ▼
  ┌────────────┐  validation errors
  │  VALIDATE  │ ─────────────────────────────────────────────────────────────┐
  └─────┬──────┘                                                              │
        │ valid                                                               │
        ▼                                                                     │
  ┌──────────────────┐                                                        │
  │ ENTITY RESOLVE   │  (read-only; guaranteed to succeed post-validation)    │
  └────────┬─────────┘                                                        │
           │                                                                  │
           ▼                                                                  │
  ┌───────────────┐                                                           │
  │     PRICE     │  PricingEngine → lineItems, subtotal, totalTax           │
  └───────┬───────┘                                                           │
          │                                                                   │
          ▼                                                                   │
  ┌───────────────┐  invalid promo → warning added, promoDiscount = 0        │
  │    PROMOTE    │  (non-fatal; pipeline always continues)                   │
  └───────┬───────┘                                                           │
          │                                                                   │
          ▼                                                                   │
  ┌───────────────┐                                                           │
  │     SHIP      │  ShippingCalculator → shippingCost (0 if freeShipping)   │
  └───────┬───────┘                                                           │
          │                                                                   │
          ▼                                                                   │
  ┌───────────────┐                                                           │
  │  GRAND TOTAL  │  grandTotal = subtotal − promoDiscount + tax + shipping  │
  └───────┬───────┘                                                           │
          │                                                                   ▼
          ▼                                                          ┌──────────────┐
  ┌───────────────────────────────┐  insufficient stock             │   REJECTED   │
  │   INVENTORY CHECK & RESERVE   │ ───────────────────────────────►│  (no stock   │
  │  allowPartialFulfillment?     │  (pricing shown; no reserve)    │   reserved)  │
  └──────────────┬────────────────┘                                 └──────────────┘
                 │                                                          ▲
                 │ available (full or partial)                              │
                 │ [partial → recalculate prices]                          │
                 ▼                                                          │
  ┌──────────────────────┐  HIGH severity flag                             │
  │    FRAUD DETECT      │ ────────────────────────────── releaseReserve ──┘
  └──────────┬───────────┘
             │
             │ no HIGH flags
             ▼
  ┌───────────────────────────────────────────────────────┐
  │                      FINALISE                         │
  │  MEDIUM/LOW fraud flags → added to warnings           │
  │  orderRepository.save(result)                         │
  └───────────────────────────┬───────────────────────────┘
                              │
                              ▼
                      ┌───────────────┐
                      │   PROCESSED   │
                      └───────────────┘


  Terminal states
  ───────────────
  PROCESSED  — order fulfilled (fully or partially), inventory reserved, persisted
  REJECTED   — one of three causes:
                 (a) validation failure       → no pricing, no stock touched
                 (b) inventory unavailable    → pricing computed, no stock reserved
                 (c) HIGH fraud flag          → pricing computed, stock reserved then released
  exit(1)    — system error (parse failure, persistence failure) — not a business rejection
```

---

## Reversibility Summary

| Stage | Side Effect | Reversible? | How |
|-------|-------------|-------------|-----|
| 1 — Input Parsing | None | N/A | — |
| 2 — Validation | `orderRepository.save()` on rejection | No (once saved) | — |
| 3 — Entity Resolution | None | N/A | — |
| 4 — Pricing | None | N/A | — |
| 5 — Promotion | None | N/A | — |
| 6 — Shipping | None | N/A | — |
| 7 — Grand Total | None | N/A | — |
| 8 — Inventory Reserve | Stock decremented in `InventoryService` | **Yes** — `releaseReservation()` called on HIGH fraud | `inventoryService.releaseReservation(fulfilledItems)` |
| 9 — Fraud Detection | Triggers release if HIGH | See above | — |
| 10 — Finalise/Persist | `orderRepository.save()` | No | — |
