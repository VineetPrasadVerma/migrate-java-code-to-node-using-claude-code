# CLAUDE.md — Order Processing Migration

## What This Project Is
Migrating a Java order processing system to a Node.js + Java hybrid.
Java retains ALL business logic. Node.js calls Java via CLI (child_process.execFile).
Node.js is the HTTP API layer only — it spawns the Java JAR for each order via stdin/stdout.

## Architecture (NON-NEGOTIABLE)
- Java JAR:          java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar
- Node.js Express:   port 3000 (spawns Java JAR per request)
- React FE (future): port 5173

## Hard Rules
1. NEVER implement business logic in Node.js — it lives in Java. Node.js spawns the Java JAR.
2. ALL monetary values must match Java output to the cent (HALF_UP rounding)
3. Dates are always "YYYY-MM-DD" strings — parse with dayjs, never with `new Date()`
4. Run `./java-order-system/validate.sh` after any Java change
5. Run `./migration-reference/phase5-verification/compare-outputs.sh` after any Node change
6. orderService.js sends request via stdin, reads response from stdout — no HTTP calls to Java

## CLI Integration Pattern
```js
import { execFile } from 'child_process'
// Send JSON via proc.stdin → receive JSON via stdout
// proc.stdin.end() must be called immediately after write
```

## Project Layout
- `java-order-system/`   → Original Java app (JAR is the integration point)
- `nodejs-api/`          → Node.js Express API (CLI wrapper layer)
- `ai-context/`          → Extracted business rules and domain docs (read before coding)
- `migration-reference/` → Reference approach and evaluation docs

## Cursor Rules Available (use with @rulename in Cursor Agent)
- `@analyze-service`     — structured analysis of a Java service file
- `@extract-rules`       — extract business rules with exact thresholds, auto-saves to ai-context/BUSINESS_RULES.md
- `@create-node-service` — scaffold a Node.js service that spawns the Java JAR
- `@verify-parity`       — two-way CLI vs Node API comparison

## Test Strategy
- Java: JUnit tests → `mvn test` in java-order-system/
- Node: Jest tests → `npm test` in nodejs-api/
- Integration: `compare-outputs.sh` verifies Java CLI output == Node.js API output
- NEVER mock the Java JAR in integration tests

## Business Rule Source of Truth
Read `ai-context/BUSINESS_RULES.md` before modifying any pricing, shipping,
promo, or fraud logic. The Java code is authoritative; the ai-context files are its documentation.

## Key Expected Values (for quick sanity checks)
- Order 1 (Gold + SAVE10):       grandTotal = 1218.14
- Order 2 (new Bronze, high):    status = REJECTED, HIGH_VALUE_NEW_CUSTOMER
- Order 3 (P007 out of stock):   status = REJECTED
- Order 4 (Platinum + FREESHIP): grandTotal = 819.42, shippingCost = 0
- Order 5 (new Bronze, zone 4):  status = PROCESSED, 3 fraud flags, grandTotal = 672.27
