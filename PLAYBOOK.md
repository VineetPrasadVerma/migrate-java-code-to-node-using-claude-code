# Migration Playbook — Step-by-Step Execution Guide

## Java Order Processing System → Node.js + Java CLI

> Follow each step in order. Each step has a clear objective, exact commands/prompts,
> and a verification check before moving on.

---

## Prerequisites Checklist

```bash
java -version        # need 11+
mvn -version         # need 3.6+
node -v              # need 18+
npm -v               # need 9+
curl --version       # for API testing
jq --version         # for JSON parsing: brew install jq
python3 --version    # for validate.sh
```

---

# PART A: AI WORKFLOW SETUP

*Do this once before writing any migration code.*

---

## Step 1 — Create CLAUDE.md (Project Memory)

`CLAUDE.md` is automatically loaded by Claude Code at the start of every session.
It prevents the AI from making architecture decisions that violate your strategy.

Create `/Users/vineetverma/Desktop/Velotio/Medispend/POC/CLAUDE.md`:

```markdown
# CLAUDE.md — Order Processing Migration

## What This Project Is
Migrating a Java order processing system to a Node.js + Java hybrid.
Java retains ALL business logic. Node.js calls Java via CLI (child_process.execFile).
Node.js is the HTTP API layer only — it spawns the Java JAR for each order.

## Architecture (NON-NEGOTIABLE)
- Java JAR:         java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar
- Node.js Express:  port 3000 (spawns Java JAR per request via stdin/stdout)
- React FE (future): port 5173

## Hard Rules
1. NEVER implement business logic in Node.js — it lives in Java. Node spawns Java.
2. ALL monetary values must match Java output to the cent (HALF_UP rounding)
3. Dates are always "YYYY-MM-DD" strings — parse with dayjs, never with `new Date()`
4. Run `./java-order-system/validate.sh` after any Java change
5. Run `./migration-reference/phase5-verification/compare-outputs.sh` after any Node change
6. orderService.js sends the request via stdin, reads response from stdout — no HTTP

## Project Layout
- `java-order-system/`    → Original Java app (JAR is the integration point)
- `nodejs-api/`           → Node.js Express API (CLI wrapper layer)
- `ai-context/`           → Extracted business rules and domain docs (read before coding)
- `migration-reference/`  → Reference approach docs

## CLI Integration Pattern
```js
import { execFile } from 'child_process'
// Send JSON via stdin → receive JSON via stdout
// Never call Java over HTTP in this project
```

## Test Strategy

- Java: JUnit tests in `java-order-system/src/test/`
- Node: Jest tests in `nodejs-api/tests/`
- Integration: compare-outputs.sh verifies CLI output == Node API output
- NEVER mock the Java JAR in integration tests

## Business Rule Source of Truth

Read `ai-context/BUSINESS_RULES.md` before modifying any pricing, shipping,
promo, or fraud logic. The Java code is authoritative; BUSINESS_RULES.md is its documentation.

## Commit Convention

feat: | fix: | test: | refactor:
Every commit must pass: mvn test (Java) and npm test (Node)

```

**Verify**: Open a new Claude Code session in the POC folder. Claude should acknowledge the project context.

---

## Step 2 — Create Cursor Rules

Cursor rules live in `.cursor/rules/`. Each `.mdc` file becomes a rule you can
attach to any Cursor Agent prompt using `@rulename`.

```bash
mkdir -p /Users/vineetverma/Desktop/Velotio/Medispend/POC/.cursor/rules
```

Create 4 rule files:

`**.cursor/rules@analyze-service.mdc**` — structured analysis template for any Java service file

`**.cursor/rules@extract-rules.mdc**` — extracts business rules with exact thresholds, auto-saves to `ai-context/BUSINESS_RULES.md`

`**.cursor/rules/create-node-service.mdc**` — scaffolds a Node.js service that spawns the Java JAR via CLI

`**.cursor/rules@verify-parity.mdc**` — two-way parity check: Java CLI output vs Node.js API output

> The content for each rule file is already created in `.cursor/rules/`. If starting fresh,
> ask Cursor Agent: "Create 4 Cursor rules in .cursor/rules/ based on CLAUDE.md — analyze-service,
> extract-rules, create-node-service, verify-parity"

**How to use in Cursor Agent**: Type `@` in the chat to see all rules in autocomplete.
Attach a rule to your prompt like:

```
@extract-rules

File: java-order-system/src/main/java/com/company/orders/service/PricingEngine.java
```

**Verify**: In Cursor Agent, type `@` — you should see all 4 rules in the autocomplete list.

---

## Step 3 — Set Up Postman MCP

The Postman MCP lets you create and manage API collections directly from Cursor Agent.
Configure it in `.cursor/mcp.json` (already done if you followed Part A setup).

**In Cursor Agent, run this prompt:**

```
Using the Postman MCP:
1. Check my authenticated user with getAuthenticatedUser
2. List my workspaces with getWorkspaces
3. Create a new workspace called "Order Processing Migration" if one doesn't exist
```

Note the workspace ID — you'll use it when creating collections in Step 20.

---

# PART B: CODEBASE ANALYSIS

*Understand before you build. This creates your ai-context/ reference files.*

---

## Step 4 — Analyze the Pipeline Orchestrator

```bash
mkdir -p /Users/vineetverma/Desktop/Velotio/Medispend/POC/ai-context
```

**Prompt to Claude Code** (paste exactly):

```
Read these two files:
- java-order-system/src/main/java/com/company/orders/Main.java
- java-order-system/src/main/java/com/company/orders/service/OrderProcessingService.java

Describe the complete order processing pipeline as a numbered list of stages.
For each stage:
1. Stage name
2. Which service/method is called
3. What data enters this stage
4. What data exits this stage
5. What can go wrong (failure modes and their effect)
6. Whether this stage is reversible (e.g. inventory can be released)

Then draw the state machine as ASCII:
  PENDING → ... → PROCESSED
                → REJECTED

Save the output to ai-context/PIPELINE.md
```

**Verify**: `ai-context/PIPELINE.md` exists and shows stages ending in PROCESSED/REJECTED.

---

## Step 5 — Extract Business Rules (Run for Each Service)

Run `@extract-rules` for each service file. Do them in this order (dependencies first):

### 5a — PricingEngine

**Prompt:**

```
@extract-rules

File: java-order-system/src/main/java/com/company/orders/service/PricingEngine.java

After extraction, also create a pricing matrix table with:
- Tier discounts (all 4 tiers)
- Volume discount brackets (all 4 brackets)
- Tax rates (all 5 categories)
- The discount cap rule
- The complete pricing formula showing order of operations

Save to ai-context/BUSINESS_RULES.md (create the file, Pricing section)
```

### 5b — PromotionEngine

**Prompt:**

```
@extract-rules

File: java-order-system/src/main/java/com/company/orders/service/PromotionEngine.java

Also document:
- The 5 eligibility checks and their exact ORDER (order matters — fail on first failure)
- Each promo type and its calculation formula
- What happens when a promo fails (is the order rejected or does it proceed?)

Append to ai-context/BUSINESS_RULES.md (Promotions section)
```

### 5c — ShippingCalculator

**Prompt:**

```
@extract-rules

File: java-order-system/src/main/java/com/company/orders/service/ShippingCalculator.java

Also create a decision table:
- Rows: zones 1-5
- Columns: STANDARD, EXPRESS, OVERNIGHT (base cost only, no surcharges)
- Then separately document: weight surcharge formula, dangerous goods, free shipping conditions

Append to ai-context/BUSINESS_RULES.md (Shipping section)
```

### 5d — FraudDetectionService

**Prompt:**

```
@extract-rules

File: java-order-system/src/main/java/com/company/orders/service/FraudDetectionService.java

Create a table with columns:
  Rule Name | Severity | Trigger Conditions (exact thresholds) | Effect on Order

Also note:
- Are rules evaluated independently or does one block another?
- What happens to inventory if order is rejected by fraud?
- Is the grand total computed before or after fraud check? (crucial for threshold logic)

Append to ai-context/BUSINESS_RULES.md (Fraud Detection section)
```

### 5e — ValidationService

**Prompt:**

```
@extract-rules

File: java-order-system/src/main/java/com/company/orders/service/ValidationService.java

List all validation rules as:
  Rule N: [field] — [constraint] — [error message]

Note: does validation fail-fast (stop at first error) or collect all errors?

Append to ai-context/BUSINESS_RULES.md (Validation section)
```

### 5f — Legacy Patterns

**Prompt:**

```
Read these files and document legacy patterns that affect Node.js migration:
- java-order-system/src/main/java/com/company/orders/model/Customer.java
- java-order-system/src/main/java/com/company/orders/util/MoneyUtils.java
- java-order-system/src/main/java/com/company/orders/repository/OrderRepository.java
- java-order-system/src/main/java/com/company/orders/repository/ProductRepository.java

For each legacy pattern found:
1. Pattern name
2. What it does in Java
3. Why it's a migration risk
4. What the Node.js equivalent must be

Save to ai-context/LEGACY_PATTERNS.md
```

**Verify**: `ai-context/BUSINESS_RULES.md` has 5 sections. Open it and manually confirm:

- Pricing cap is 40%
- Tax on ELECTRONICS is 18%
- HIGH_VALUE_NEW_CUSTOMER threshold is $1000, 90 days
- FREESHIP promo does NOT remove dangerous goods surcharge

---

## Step 6 — Create Data Model Reference

**Prompt:**

```
Read all model classes in:
  java-order-system/src/main/java/com/company/orders/model/

For each class, produce a table:
  | Field | Java Type | JSON serialized as | Required | Constraints | Notes |

Pay special attention to:
- Fields that serialize differently in JSON vs Java (e.g. enums become strings)
- Nullable fields
- Fields with legacy types (Customer.registrationDate)
- Nested objects

Save to ai-context/DATA_MODELS.md
```

---

## Step 7 — Document Test Cases

**Prompt:**

```
Read these files:
- java-order-system/test-inputs/order1_happy_path.json through order5_fraud_warnings.json
- java-order-system/expected-outputs/order1_expected.json through order5_expected.json
- java-order-system/README.md (Business Rules Summary section)

For each test case, create a trace showing WHY the numbers are what they are.
Example for Order 1:
  Customer: C002 (Gold = 10% tier discount)
  P001: $1200 × (1-0.10) = $1080. tax 18% = $194.40. lineSubtotal=$1080
  P002: $15 × (1-0.10) = $13.50. qty=3. subtotal=$40.50. tax 18%=$7.29
  Subtotal: $1120.50
  SAVE10: 10% × $1120.50 = $112.05
  Tax: $201.69
  Shipping: zone 2, standard, 2.8kg < 5kg = $8.00
  Grand total: $1120.50 - $112.05 + $201.69 + $8.00 = $1218.14 ✓

Save to ai-context/TEST_CASES.md
```

**Verify**: The math in `ai-context/TEST_CASES.md` matches `expected-outputs/`. If Claude gets wrong numbers, your BUSINESS_RULES.md needs correction.

---

# PART C: NODE.JS ORCHESTRATION LAYER

---

## Step 8 — Initialize Node.js Project

```bash
mkdir -p /Users/vineetverma/Desktop/Velotio/Medispend/POC/nodejs-api
cd /Users/vineetverma/Desktop/Velotio/Medispend/POC/nodejs-api

npm init -y
npm install express joi winston
npm install --save-dev jest supertest nodemon
```

**Prompt:**

```
Update nodejs-api/package.json:
- Add "type": "module" for ES modules
- Add scripts:
    "start": "node src/server.js"
    "dev": "nodemon src/server.js"
    "test": "node --experimental-vm-modules node_modules/.bin/jest"
    "test:integration": "INTEGRATION=true jest tests/integration"
- Add jest configuration:
    "jest": {
      "transform": {},
      "testEnvironment": "node"
    }
```

---

## Step 9 — Create Project Structure

**Prompt:**

```
Create the Node.js project structure for nodejs-api/.

Create these empty files (I will fill them in subsequent steps):
  src/app.js                          - Express app setup
  src/server.js                       - HTTP server start
  src/routes/orders.js                - POST /api/orders route
  src/services/orderService.js        - Spawns Java JAR via CLI
  src/middleware/validateOrder.js     - Request validation
  src/middleware/errorHandler.js      - Global error handler
  src/middleware/requestLogger.js     - Request logging
  src/utils/logger.js                 - Winston logger
  tests/unit/orderService.test.js     - Unit tests
  tests/integration/orders.test.js    - Integration tests
  .env.example                        - Environment template
  .env                                - Local env (gitignored)

Create .env with:
  PORT=3000
  JAR_PATH=../java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar
  JAVA_BIN=/opt/homebrew/opt/openjdk@11/bin/java
  LOG_LEVEL=info

Create .env.example with same keys but empty values.

Add .env to .gitignore (create .gitignore if it doesn't exist).
```

---

## Step 10 — Implement Logger

**Prompt:**

```
Create nodejs-api/src/utils/logger.js:
- Uses winston
- Reads LOG_LEVEL from process.env (default: 'info')
- JSON format in production (NODE_ENV=production)
- Pretty format in development
- Export as default logger instance
```

---

## Step 11 — Implement Request Validation Middleware

**Prompt:**

```
Create nodejs-api/src/middleware/validateOrder.js

Read ai-context/DATA_MODELS.md for the Order schema.

Requirements:
- Uses joi for schema validation
- Validates the request body against the Order schema:
    orderId:                 string, required
    customerId:              string, required
    items:                   array, min 1 item, required
      items[].productId:     string, required
      items[].quantity:      integer, min 1, max 100, required
    promoCode:               string, optional, allow null and empty string
    shippingZone:            integer, min 1, max 5, required
    shippingType:            string, one of STANDARD/EXPRESS/OVERNIGHT, required
    allowPartialFulfillment: boolean, default false
    orderDate:               string, pattern YYYY-MM-DD, optional

- On validation failure: respond with HTTP 400:
    { "error": "Invalid request", "details": ["field: message", ...] }
- On success: call next()
- Export as default middleware function
```

---

## Step 12 — Implement Error Handler and Logger Middleware

**Prompt:**

```
Create nodejs-api/src/middleware/errorHandler.js

This is the Express global error handler (4-argument function).

HTTP status mapping:
- JavaProcessError (Java process failed): 502
- Joi ValidationError: 400 with validation details
- SyntaxError (malformed JSON body): 400 with "Invalid JSON"
- Everything else: 500

Response format for all errors:
  {
    "error": "human readable message",
    "retryable": true/false   // only for 5xx
  }

Log all errors at error level with: error.message, error.stack, req.path, req.method.
Never log request bodies (may contain PII).

Create nodejs-api/src/middleware/requestLogger.js:
- Logs at info level: { method, path, statusCode, durationMs } on response finish
- Reads X-Request-ID header, generates UUID if not present
- Attaches requestId to req for use in other middleware
```

---

## Step 13 — Implement the Order Service (CLI Approach)

**Prompt:**

```
Create nodejs-api/src/services/orderService.js

Read:
- ai-context/DATA_MODELS.md (for request/response schema)
- ai-context/BUSINESS_RULES.md (to understand what Java does, NOT to implement it)

Requirements:
- ES module
- Export: processOrder(orderRequest) async function
- Integration approach: spawn Java JAR via child_process.execFile
  - JAR path: process.env.JAR_PATH (resolved relative to this file if relative)
  - Java binary: process.env.JAVA_BIN (default: 'java')
  - Send JSON request via stdin
  - Read JSON response from stdout
  - Timeout: 15 seconds
- Validates that orderRequest has required fields (orderId, customerId, items, shippingZone, shippingType)
- Returns the complete Java response object unchanged
- On Java process failure (non-zero exit, timeout, parse error):
  throw new Error with message describing the failure
- JSDoc:
  @param {Object} orderRequest - The order to process
  @returns {Promise<Object>} OrderResult from Java JAR
  @throws {Error} When Java process fails

This function is a THIN WRAPPER. It must not:
- Modify the request before sending
- Modify the response before returning
- Implement pricing, validation, fraud detection, or any business logic
- Handle REJECTED status as an error (REJECTED is a valid outcome, return it as-is)

Implementation pattern:
  import { execFile } from 'child_process'
  import path from 'path'
  import { fileURLToPath } from 'url'

  const __dirname = path.dirname(fileURLToPath(import.meta.url))
  const JAR_PATH = process.env.JAR_PATH
    ? path.resolve(process.env.JAR_PATH)
    : path.resolve(__dirname, '../../../java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar')
  const JAVA_BIN = process.env.JAVA_BIN || 'java'

  // Use a Promise wrapper around execFile
  // Write JSON to proc.stdin, read stdout, parse JSON
```

---

## Step 14 — Implement the Route

**Prompt:**

```
Create nodejs-api/src/routes/orders.js

Requirements:
- Express Router
- POST /  (will be mounted at /api/orders in app.js)
- Apply validateOrder middleware
- Call orderService.processOrder(req.body)
- HTTP status mapping:
    result.status === "PROCESSED" → 200
    result.status === "REJECTED"  → 422
    anything else                 → 200 (unknown status, pass through)
- On error: pass to next(error) for errorHandler
- Log: logger.info() with { orderId, status, grandTotal, durationMs } on success
- Log: logger.error() with { orderId, error: err.message } on failure

Create nodejs-api/src/app.js:
- Creates Express app
- Applies: express.json(), requestLogger middleware
- Mounts: orders router at /api/orders
- Applies: errorHandler (must be last)
- Exports the app (not the server, for testing)

Create nodejs-api/src/server.js:
- Imports app
- Reads PORT from process.env (default: 3000)
- Starts HTTP server
- Logs: "Node.js API listening on port [PORT]"
- Logs: "Java JAR path: [JAR_PATH]"
```

---

## Step 15 — Write Unit Tests

**Prompt:**

```
Create nodejs-api/tests/unit/orderService.test.js

Test the orderService in isolation by mocking child_process.execFile.

Test cases:
1. processOrder with valid request → calls Java JAR with JSON via stdin, returns parsed response
2. processOrder when Java returns REJECTED → returns the rejected result (not an error)
3. processOrder when Java process exits with error → throws Error
4. processOrder with missing required field (no orderId) → throws validation error before spawning Java

Read ai-context/TEST_CASES.md for realistic test data values.
Use jest.mock('child_process') to mock execFile.
```

---

## Step 16 — Start Node.js API and Run First Test

```bash
# Make sure Java JAR is built (one-time)
cd /Users/vineetverma/Desktop/Velotio/Medispend/POC/java-order-system
export PATH="/opt/homebrew/opt/openjdk@11/bin:$PATH"
mvn package -q

# Start Node.js API
cd /Users/vineetverma/Desktop/Velotio/Medispend/POC/nodejs-api
node src/server.js &

# Wait for startup
sleep 2

# Test with Order 1 — expect PROCESSED, grandTotal=1218.14
curl -s -X POST http://localhost:3000/api/orders \
  -H "Content-Type: application/json" \
  -d @../java-order-system/test-inputs/order1_happy_path.json | jq '{status, grandTotal}'

# Test with Order 2 — expect REJECTED
curl -s -X POST http://localhost:3000/api/orders \
  -H "Content-Type: application/json" \
  -d @../java-order-system/test-inputs/order2_fraud_rejection.json | jq '{status, fraudFlags}'
```

---

# PART D: VERIFICATION — PARITY CHECK

---

## Step 17 — Run the Parity Script

Make sure Node.js API is running and the Java JAR is built:

```bash
# Terminal 1 — Node.js API
cd /Users/vineetverma/Desktop/Velotio/Medispend/POC/nodejs-api
export PATH="/opt/homebrew/opt/openjdk@11/bin:$PATH"
node src/server.js
```

Run the comparison:

```bash
cd /Users/vineetverma/Desktop/Velotio/Medispend/POC
bash migration-reference/phase5-verification/compare-outputs.sh
```

**Expected output:**

```
PASS [order1_happy_path.json]: CLI == Node.js API
PASS [order2_fraud_rejection.json]: CLI == Node.js API
PASS [order3_inventory_shortage.json]: CLI == Node.js API
PASS [order4_platinum_freeship.json]: CLI == Node.js API
PASS [order5_fraud_warnings.json]: CLI == Node.js API
Results: 5 passed, 0 failed
```

---

## Step 18 — Run Edge Case Verification

**Prompt to Claude Code:**

```
@verify-parity

Run parity checks for these edge cases that are NOT in the standard test inputs.
For each, I want you to:
1. Create the input JSON
2. Run it through Java CLI
3. Run it through Node API (port 3000)
4. Confirm both match

Edge cases to test:
A. Dangerous goods + FREESHIP promo:
   Customer C001, product P010 (Lithium Battery, dangerous good), qty=1, promoCode=FREESHIP, zone=1, STANDARD
   Expected: shippingCost=15.00 (dangerous surcharge only, NOT waived by FREESHIP)

B. Discount cap (40% max):
   Customer C001 (PLATINUM=15%), product P002 (USB Cable), qty=25 (volume=12%), combined=27% < 40% cap
   Expected: discountedUnitPrice=10.95 ($15 × 0.73)

C. Expired promo code:
   Customer C002, product P002, qty=1, promoCode=EXPIRED
   Expected: status=PROCESSED, promoDiscount=0, warning contains "expired"

D. GOLD20 promo with SILVER customer (tier restriction):
   Customer C003 (SILVER), product P001, qty=1, promoCode=GOLD20
   Expected: status=PROCESSED, promoDiscount=0, warning about tier restriction

E. allowPartialFulfillment=true with out-of-stock item:
   Customer C002, items=[P007 qty=1 (out of stock), P002 qty=2], allowPartialFulfillment=true
   Expected: status=PROCESSED, lineItems has only P002 (P007 dropped)
```

---

## Step 19 — Run Full Test Suite

```bash
# Java unit tests
cd /Users/vineetverma/Desktop/Velotio/Medispend/POC/java-order-system
export PATH="/opt/homebrew/opt/openjdk@11/bin:$PATH"
mvn test

# Java CLI validation
bash validate.sh

# Node unit tests
cd ../nodejs-api
npm test

# Parity check
cd ..
bash migration-reference/phase5-verification/compare-outputs.sh
```

**All must pass before you consider the migration complete.**

---

## Step 20 — Add Postman Node.js Collection via MCP

**Prompt:**

```
Using the Postman MCP, create a collection "Node.js Order Processing API"
in the "Order Processing Migration" workspace.

Add 5 requests, one for each test input, pointing to http://localhost:3000/api/orders:
- Name: "Order 1 - Happy Path (Gold + SAVE10)"
  URL: POST http://localhost:3000/api/orders
  Body: [paste contents of java-order-system/test-inputs/order1_happy_path.json]
  Tests: pm.expect(pm.response.json().status).to.equal("PROCESSED")
         pm.expect(pm.response.json().grandTotal).to.equal(1218.14)
- Name: "Order 2 - Fraud Rejection"
  Tests: pm.expect(pm.response.json().status).to.equal("REJECTED")
         pm.expect(pm.response.code).to.equal(422)
- Name: "Order 3 - Inventory Shortage"
  Tests: pm.expect(pm.response.json().status).to.equal("REJECTED")
- Name: "Order 4 - Platinum FREESHIP"
  Tests: pm.expect(pm.response.json().grandTotal).to.equal(819.42)
         pm.expect(pm.response.json().shippingCost).to.equal(0)
- Name: "Order 5 - Fraud Warnings"
  Tests: pm.expect(pm.response.json().status).to.equal("PROCESSED")
         pm.expect(pm.response.json().fraudFlags.length).to.equal(3)
         pm.expect(pm.response.json().grandTotal).to.equal(672.27)

These assertions verify parity with the Java CLI output.
```

---

# COMPLETION CHECKLIST

Before considering the migration done, verify each item:

## Java Layer

- `mvn test` — 8 unit tests pass
- `./validate.sh` — 18 CLI checks pass
- Java JAR produces correct output for all 5 test inputs

## Node.js Layer

- `npm test` — all unit tests pass
- All 5 test inputs return correct values via Node.js API
- HTTP status codes: PROCESSED→200, REJECTED→422, Java process error→502
- Node.js contains NO business logic (pricing, fraud, promo calculations)
- orderService.js uses execFile only — no HTTP calls, no calculations

## Parity

- `compare-outputs.sh` — 5/5 parity checks pass (CLI == Node.js API)
- All 5 edge cases (Step 18) produce identical results in CLI and Node.js API

## AI Workflow Artifacts

- `CLAUDE.md` at project root
- 4 custom commands in `.claude/commands/`
- `ai-context/BUSINESS_RULES.md` (complete, all 5 sections)
- `ai-context/DATA_MODELS.md`
- `ai-context/PIPELINE.md`
- `ai-context/LEGACY_PATTERNS.md`
- `ai-context/TEST_CASES.md` (with math traces)
- Postman collection created via MCP

---

# TROUBLESHOOTING GUIDE

## Node.js API returns different numbers than Java CLI

**Cause**: Business logic was accidentally implemented in Node.js, or the JSON is being modified before sending to Java.

**Fix**:

```
Read nodejs-api/src/services/orderService.js

Check: is this function doing ANY calculation? Any field modification?
Any conditional logic based on business rules?

It should only: validate required fields exist, spawn Java JAR via execFile, return parsed stdout.
Show me all the logic in this file.
```

## Java JAR not found — ENOENT or spawn error

**Cause**: JAR_PATH in .env is wrong, or JAR hasn't been built.

**Fix**:

```bash
# Build the JAR
cd java-order-system
export PATH="/opt/homebrew/opt/openjdk@11/bin:$PATH"
mvn package -q

# Verify JAR exists
ls -la target/order-processing-system-1.0.0-jar-with-dependencies.jar

# Check JAR_PATH in nodejs-api/.env resolves correctly
node -e "import('path').then(p => console.log(p.default.resolve(process.env.JAR_PATH || '../java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar')))"
```

## Java process times out (15 second timeout exceeded)

**Cause**: Java JAR is hanging (usually a stdin not being closed properly).

**Fix**:

```
Read nodejs-api/src/services/orderService.js

Check that proc.stdin.end() is called immediately after proc.stdin.write(JSON.stringify(orderRequest)).
The Java app reads all of stdin before processing — if stdin is not closed, it hangs waiting for more input.
```

## Java binary not found (JAVA_BIN error)

**Cause**: JAVA_BIN in .env points to wrong path, or Java is not installed.

**Fix**:

```bash
# Find where Java is installed
which java
/usr/libexec/java_home -v 11

# Update nodejs-api/.env
JAVA_BIN=/opt/homebrew/opt/openjdk@11/bin/java
```

## NEWCUST promo behaves differently in Node.js

**Cause**: Date arithmetic difference. Java uses `ChronoUnit.DAYS.between()`.

**Fix**:

```
The NEWCUST promo requires account age < 30 days. This is calculated in Java
using ChronoUnit.DAYS.between(registrationDate, orderDate).

If Node.js is implementing this calculation anywhere, remove it — this logic runs in Java.
Node.js only needs to pass the orderDate field through to the Java JAR via stdin.
```

