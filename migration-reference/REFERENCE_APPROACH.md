# Reference Migration Approach
## Java Order Processing System → Node.js + Java API

> **Purpose**: This document describes the ideal engineering approach for migrating the Java Order Processing System. It serves as a benchmark for evaluating interview candidates. Candidates will not see this document — they receive only the Java codebase.

---

## Architecture Goal

```
Before:
  CLI → Java (all logic)

After:
  React FE → Node.js API (orchestration) → Java Spring Boot API (business logic) → Response
```

Java retains all business logic. Node.js acts as an orchestration/API gateway layer.
The migration is incremental — Java logic is never rewritten, only wrapped.

---

# Phase 1: Understanding the Legacy System

## 1.1 Initial Reconnaissance

**Goal**: Map what the system does before touching a line of code.

```bash
# Understand structure
find src -name "*.java" | sort
wc -l src/**/**/*.java   # complexity estimate

# Read entry point first
cat src/main/java/com/company/orders/Main.java

# Understand the pipeline
cat src/main/java/com/company/orders/service/OrderProcessingService.java
```

**AI Prompt (Claude Code / Cursor)**:
```
Read Main.java and OrderProcessingService.java.
Describe the complete order processing pipeline as a numbered list of stages.
For each stage, identify: what service is called, what data enters, what data exits,
and what can go wrong (failure modes).
Do not suggest changes — only describe what exists.
```

**Expected AI output**: A stage-by-stage description of the 9-stage pipeline
(Validate → Price → Promote → Ship → Inventory → FraudCheck → Finalise).

---

## 1.2 Business Rule Extraction

**Goal**: Extract every business rule into human-readable form before writing any code.

**AI Prompt sequence**:

```
1. Read PricingEngine.java completely.
   List every business rule as: "IF [condition] THEN [effect]".
   Include all magic numbers with their business meaning.
   Flag anything that is unclear or requires domain knowledge to interpret.
```

```
2. Read FraudDetectionService.java.
   For each fraud rule:
   - Rule name
   - Trigger conditions (exact thresholds)
   - Severity level
   - Effect on the order
   Note: this file uses a legacy procedural pattern. Document the rules,
   not the pattern.
```

```
3. Read ShippingCalculator.java.
   Create a decision table showing how shipping cost is calculated.
   Include: base rate by zone, speed multipliers, weight surcharge formula,
   dangerous goods surcharge, and all free-shipping conditions.
```

```
4. Read PromotionEngine.java.
   List all promo code types and their eligibility rules.
   Identify the exact order of eligibility checks and explain why order matters.
```

**Output to create**: `ai-context/BUSINESS_RULES.md` (see Phase 1.5)

---

## 1.3 Legacy Pattern Documentation

**Goal**: Identify legacy patterns that will cause translation issues.

**AI Prompt**:
```
Read the following files and identify legacy Java patterns that a Node.js engineer
would need to understand to faithfully replicate the behavior:
- Customer.java
- MoneyUtils.java
- OrderRepository.java
- ProductRepository.java
- FraudDetectionService.java

For each pattern, explain:
1. What the legacy pattern is
2. Why it was likely done this way (historical context)
3. What behavior it produces that could be surprising
4. What the Node.js equivalent should be
```

**Expected findings**:
- `registrationDate` as String → must parse `"YYYY-MM-DD"` identically
- `double` arithmetic with HALF_UP rounding → must match exactly (test with exact values)
- Procedural fraud rules → must replicate exact threshold values
- `synchronized` HashMap → stateless HTTP API is naturally safe; document the difference

---

## 1.4 Data Flow Mapping

**AI Prompt**:
```
Trace the complete data flow for a Gold customer (C002) ordering 1× Laptop Pro (P001)
with promo code SAVE10, zone 2, STANDARD shipping.

Show every transformation step:
- Input values at each stage
- Exact calculations performed
- Output values at each stage
- Which service/method performs each calculation

Expected grand total: $1218.14. Verify your trace produces this.
```

This prompt validates the AI's understanding of the system before any migration begins.
**If the AI cannot produce $1218.14, it has not understood the pricing logic correctly.**

---

## 1.5 AI Context File Structure

Create these files in `ai-context/` before writing any migration code:

```
ai-context/
├── DOMAIN.md           # what the system does, who uses it, key terminology
├── BUSINESS_RULES.md   # all rules extracted in 1.2 (authoritative reference)
├── DATA_MODELS.md      # all model fields, types, constraints, legacy notes
├── STATE_MACHINE.md    # order lifecycle states and transitions
├── PRICING_MATRIX.md   # tier discounts, tax rates, volume brackets as tables
├── SHIPPING_RULES.md   # zone rates, multipliers, free shipping conditions
├── FRAUD_RULES.md      # fraud rule table with thresholds and severity
├── PROMO_CODES.md      # all promo codes, types, eligibility rules
├── LEGACY_PATTERNS.md  # legacy patterns and their migration implications
└── TEST_CASES.md       # all 5 test scenarios with exact expected values
```

**Why**: Large language models have limited context windows and do not retain information between prompts. These files ensure every AI interaction has accurate, concise context without re-reading the entire Java codebase.

**Key principle**: The AI context files become the source of truth. If a rule in BUSINESS_RULES.md contradicts the Java code, fix the file. During migration, AI prompts reference these files — not the raw Java code — to keep prompts focused and reproducible.

---

# Phase 2: AI Workflow Setup

## 2.1 Claude Code Configuration (CLAUDE.md)

Create `CLAUDE.md` at the project root. This file is automatically loaded by Claude Code at the start of every session.

```markdown
# CLAUDE.md — Order Processing Migration Project

## Project Context
We are migrating a Java order processing system to Node.js.
Java retains all business logic, exposed via Spring Boot REST APIs.
Node.js is the orchestration/API gateway layer.

## Architecture
- Java API: port 8080 (Spring Boot)
- Node.js API: port 3000 (Express)
- Both services run locally during development

## Critical Rules
1. NEVER rewrite business logic — it lives in Java. Node.js calls Java APIs.
2. ALL monetary calculations must match Java output to the cent (HALF_UP rounding)
3. Run ./validate.sh after every significant change to verify no regressions
4. Read ai-context/BUSINESS_RULES.md before modifying any service-layer code
5. API contracts are defined in api-contracts/openapi.yaml — do not deviate

## Test Strategy
- Unit tests: Jest (Node.js), JUnit (Java)
- Integration: compare-outputs.sh validates Java vs Node+Java output parity
- Never mock the Java API in integration tests — use the real running service

## Commit Convention
feat: | fix: | test: | docs: | refactor:
Each commit must pass: npm test && ./validate.sh
```

**Why**: CLAUDE.md prevents the AI from making architectural decisions that violate the migration strategy (e.g. rewriting pricing logic in Node.js instead of calling Java).

---

## 2.2 Cursor Rules (.cursorrules)

```
# Migration Rules — Order Processing System

You are helping migrate a Java order processing system to Node.js.

## Hard Rules
- Business logic lives in Java. Node.js calls Java APIs over HTTP. Never duplicate logic.
- All dollar amounts: round to 2 decimal places using HALF_UP semantics (Math.round(val * 100) / 100)
- String dates: always use "YYYY-MM-DD" format. Parse with dayjs or date-fns, not new Date()
- Error responses from Java API must be forwarded to the client with original HTTP status code
- Never use console.log in production code. Use the logger (src/utils/logger.js)

## Code Style
- ES modules (import/export), not CommonJS
- async/await, not callbacks or raw Promises
- All API functions: validate input before calling Java API
- All Java API calls: 3 retries with 500ms exponential backoff on 5xx errors

## File Organization
- src/services/     — Java API client functions
- src/routes/       — Express route handlers
- src/middleware/   — validation, error handling, logging
- src/utils/        — shared utilities

## Testing
- Every service function must have a Jest unit test
- Integration tests must run against the real Java API (no mocks)
- Test file naming: *.test.js alongside source file
```

---

## 2.3 Skills for Repetitive Workflows

Skills are reusable prompt templates invoked with a `/skill-name` command.

### `/analyze-java-service`

```markdown
Analyze the Java service file I provide and produce:

1. **Service name and responsibility** (one sentence)
2. **Dependencies** (what it calls/reads)
3. **Public methods** — for each:
   - Method signature
   - Input: what fields are required, their types and constraints
   - Output: what fields are returned and their types
   - Business rules applied (numbered list)
   - Failure modes and error messages
4. **Legacy patterns** — anything that will affect translation to Node.js
5. **API contract sketch** — draft OpenAPI path for this service's functionality

Output as structured markdown suitable for ai-context/
```

Usage: `/analyze-java-service PricingEngine.java`

### `/create-node-service`

```markdown
Given the API contract in api-contracts/openapi.yaml for [endpoint],
create the Node.js service module in src/services/.

Requirements:
- Calls Java API at JAVA_API_URL env variable (default: http://localhost:8080)
- 3 retries with exponential backoff on 5xx
- Validates response schema against expected fields
- Throws descriptive errors for unexpected Java responses
- Returns typed JavaScript object matching the contract
- Includes JSDoc comments for all exported functions

Do NOT implement any business logic. This module only calls the Java API.
Read ai-context/BUSINESS_RULES.md to understand what the endpoint does
but do not replicate the logic here.
```

### `/create-route`

```markdown
Given the service module [service file], create the Express route handler in src/routes/.

Requirements:
- Input validation middleware using joi or zod
- Calls service function, handles errors gracefully
- Returns appropriate HTTP status codes:
  - 200: PROCESSED order
  - 422: REJECTED order (business rejection, not an HTTP error)
  - 400: validation errors
  - 502: Java API unavailable
  - 500: unexpected errors
- Logs request/response at info level (no sensitive data)
- Does not contain business logic
```

### `/verify-parity`

```markdown
I need to verify that the Node.js API produces identical outputs to the Java CLI.

For order input [paste JSON]:
1. Call Java CLI: java -jar java-order-system/target/order-processing-system-*.jar <input>
2. Call Node.js API: curl -X POST http://localhost:3000/api/orders -d <input>
3. Compare these fields exactly: status, grandTotal, subtotal, totalTax, promoDiscount,
   shippingCost, fraudFlags[].flag, errors[]
4. Report any discrepancies with the exact values that differ

Run this for all 5 test inputs in test-inputs/.
```

**Why skills matter**: The same analysis workflow runs for every service (7 service files). Without a skill, the prompt is re-typed slightly differently each time, leading to inconsistent documentation. Skills enforce consistent output format across the entire codebase analysis.

---

## 2.4 MCP Setup

### Postman MCP
**Purpose**: Manage API contracts and generate Postman collections from OpenAPI specs.

```
# After creating openapi.yaml:
/postman create-collection from openapi.yaml
/postman add-example ORD-001 from test-inputs/order1_happy_path.json
/postman add-example ORD-002 from test-inputs/order2_fraud_rejection.json
```

**Why**: Postman MCP lets you maintain API documentation alongside code without manual copy-paste. The collection becomes the verification tool for both Java API and Node.js API.

### Supabase MCP (optional, if adding persistence)
**Purpose**: If the migration adds a proper database to replace the in-memory repositories.

```
/supabase list-tables                     # check current schema
/supabase execute-sql "SELECT ..."        # verify data
/supabase apply-migration orders.sql      # apply schema changes
```

---

## 2.5 Memory and Context Management

### Persistent project facts (CLAUDE.md / .cursorrules)
Store: architecture decisions, hard constraints, naming conventions.
These never change during the migration.

### Session context files (ai-context/*.md)
Store: extracted business rules, API contracts, test case values.
These are created in Phase 1 and referenced throughout the migration.

### Active task context (task decomposition files)
For each migration task, create a file like `tasks/phase3-pricing-api.md`:

```markdown
# Task: Expose PricingEngine as REST API

## Objective
Wrap Java PricingEngine.calculateLineItems() in a Spring Boot REST endpoint.

## Context files to read before starting
- ai-context/BUSINESS_RULES.md (Pricing section)
- ai-context/DATA_MODELS.md

## Inputs
POST /api/v1/pricing/calculate
Body: { customerId, items: [{productId, quantity}] }

## Expected Output
{ lineItems: [...], subtotal, totalTax }
See api-contracts/openapi.yaml#/components/schemas/PricingResponse

## Acceptance Criteria
- [ ] Returns correct grandTotal for all 5 test cases
- [ ] Returns 400 for invalid customerId
- [ ] Returns 400 for unknown productId
- [ ] Unit test covers happy path and all error cases

## Known Issues
None yet.
```

**Why**: Language models lack persistent memory between sessions. These task files resume context instantly without re-explaining the project. They also serve as progress tracking during the migration.

---

## 2.6 Prompt Decomposition Strategy

**Anti-pattern**: "Migrate the entire OrderProcessingService to Node.js"
**Why it fails**: The AI attempts too much at once, makes architectural decisions, and misses edge cases.

**Correct approach — decompose by service, then by concern**:

```
Level 1: Per service (7 services → 7 separate AI sessions)
  └── Level 2: Per concern within service
        ├── Understand (analyze-java-service skill)
        ├── Document (add to ai-context/)
        ├── Design API contract (OpenAPI spec)
        ├── Implement Java REST endpoint
        ├── Test Java endpoint (curl)
        ├── Implement Node.js client
        ├── Implement Node.js route
        └── Verify parity (compare-outputs.sh)
```

**Task decomposition for the full migration**:

```
1. ValidationService → /api/v1/orders/validate
2. PricingEngine     → /api/v1/pricing/calculate
3. PromotionEngine   → /api/v1/promotions/apply
4. ShippingCalculator→ /api/v1/shipping/calculate
5. InventoryService  → /api/v1/inventory/check-and-reserve
6. FraudDetection    → /api/v1/fraud/evaluate
7. OrderProcessing   → /api/v1/orders/process  (calls all above via Node.js)
```

Each task is independently completable and independently testable.
The final endpoint `/api/v1/orders/process` in Node.js calls all 6 Java endpoints.

---

## 2.7 Verification Loops

After completing each service:

```bash
# 1. Java API health
curl http://localhost:8080/actuator/health

# 2. Unit test
mvn test -Dtest=PricingEngineTest

# 3. Integration test (compare Java CLI vs Java REST API)
java -jar java-order-system/target/*.jar test-inputs/order1_happy_path.json > java-cli.json
curl -s -X POST http://localhost:8080/api/v1/orders/process \
  -H "Content-Type: application/json" \
  -d @test-inputs/order1_happy_path.json > java-api.json
diff <(jq 'del(.processedAt)' java-cli.json) <(jq 'del(.processedAt)' java-api.json)

# 4. Node.js API test
curl -s -X POST http://localhost:3000/api/orders \
  -H "Content-Type: application/json" \
  -d @test-inputs/order1_happy_path.json > node-api.json
diff <(jq 'del(.processedAt)' java-cli.json) <(jq 'del(.processedAt)' node-api.json)
```

**All three outputs must match** (excluding `processedAt` timestamp).

---

# Phase 3: API-fying the Java Logic

## 3.1 Architecture Decision: Thin REST Wrapper

**Decision**: Add Spring Boot REST layer on top of existing services. Do NOT refactor the services.

**Why**:
- Services are already tested
- Refactoring introduces regression risk
- The migration is about wrapping, not improving

**What to add**:
```
java-order-system/
├── src/main/java/com/company/orders/
│   ├── api/                          # NEW: REST layer
│   │   ├── OrderController.java      # POST /api/v1/orders/process
│   │   ├── PricingController.java    # POST /api/v1/pricing/calculate
│   │   ├── ValidationController.java # POST /api/v1/orders/validate
│   │   ├── InventoryController.java  # POST /api/v1/inventory/reserve
│   │   ├── FraudController.java      # POST /api/v1/fraud/evaluate
│   │   ├── ShippingController.java   # POST /api/v1/shipping/calculate
│   │   └── PromotionController.java  # POST /api/v1/promotions/apply
│   └── config/
│       └── AppConfig.java            # @Bean definitions for services
```

**AI Prompt**:
```
Add Spring Boot to the existing pom.xml and create a REST controller
for OrderProcessingService.

The single endpoint should be:
  POST /api/v1/orders/process
  Request body: Order (existing class)
  Response: OrderResult (existing class)
  HTTP 200 for both PROCESSED and REJECTED outcomes
  HTTP 400 for validation failures (empty body, malformed JSON)
  HTTP 500 for unexpected exceptions

Do NOT modify OrderProcessingService.java or any existing class.
Wire services using @Bean in AppConfig.java (constructor injection, same order as Main.java).
Add spring-boot-starter-web and spring-boot-starter-actuator to pom.xml.

Read ai-context/DATA_MODELS.md for the Order and OrderResult schemas.
```

---

## 3.2 API Contract Design

Create `api-contracts/openapi.yaml` **before** writing any code. This is the contract between Java and Node.js.

**Key design principles**:
- Use the same field names as Java (`orderId`, `customerId`, `grandTotal`, etc.)
- Map Java enums to string values in JSON (`"PROCESSED"`, `"GOLD"`, etc.)
- All monetary values are `number` with `format: double`
- Dates are `string` with `format: date` (YYYY-MM-DD)
- Nullable fields explicitly marked

**AI Prompt**:
```
Read ai-context/DATA_MODELS.md and generate an OpenAPI 3.0 spec for:
  POST /api/v1/orders/process

Include:
- Complete request schema (Order model)
- Complete response schema (OrderResult model, including nested ProcessedOrderItem and FraudFlagResult)
- Example request using order1_happy_path.json
- Example response using order1_expected.json
- Error response schemas for 400, 500

Use exactly the same field names as the Java models.
Save to api-contracts/openapi.yaml.
```

---

## 3.3 Testing the Java API

```bash
# Start the Java API
mvn spring-boot:run

# Health check
curl http://localhost:8080/actuator/health

# Test all 5 scenarios
for f in test-inputs/order*.json; do
    echo "=== $f ==="
    curl -s -X POST http://localhost:8080/api/v1/orders/process \
      -H "Content-Type: application/json" \
      -d @"$f" | jq '{status, grandTotal, promoDiscount, shippingCost}'
done

# Validate order 1 exact values
curl -s -X POST http://localhost:8080/api/v1/orders/process \
  -H "Content-Type: application/json" \
  -d @test-inputs/order1_happy_path.json | \
  jq 'assert(.status == "PROCESSED") |
      assert(.grandTotal == 1218.14) |
      assert(.shippingCost == 8.0) |
      "All assertions passed"'
```

---

## 3.4 API Versioning

- Use URL versioning: `/api/v1/...`
- Version header: `X-API-Version: 1` (optional, for future use)
- Never remove fields from responses — add only
- Breaking changes require `/api/v2/...`

---

# Phase 4: Node.js Orchestration Layer

## 4.1 Project Structure

```
nodejs-api/
├── package.json
├── .env.example
├── src/
│   ├── app.js                    # Express setup, middleware registration
│   ├── server.js                 # HTTP server start
│   ├── routes/
│   │   └── orders.js             # POST /api/orders
│   ├── services/
│   │   └── javaOrderService.js   # Calls Java API
│   ├── middleware/
│   │   ├── validateOrder.js      # Request validation (joi/zod)
│   │   ├── errorHandler.js       # Global error handler
│   │   └── requestLogger.js      # Request/response logging
│   └── utils/
│       ├── httpClient.js         # Axios/fetch with retry logic
│       └── logger.js             # Winston logger
├── tests/
│   ├── unit/
│   │   └── javaOrderService.test.js
│   └── integration/
│       └── orders.integration.test.js
└── scripts/
    └── compare-outputs.sh        # Parity validation script
```

**AI Prompt**:
```
Create a Node.js Express API project for the order processing migration.

Requirements:
- Express 4.x, ES modules (import/export)
- Single route: POST /api/orders
- Validates request body (fields: orderId, customerId, items[], shippingZone, shippingType)
- Calls Java API at JAVA_API_URL/api/v1/orders/process
- Returns Java API response unchanged (pass-through)
- Global error handler for Java API failures
- Winston logger (info level for requests, error level for failures)
- Jest for testing

Read api-contracts/openapi.yaml for the exact request/response schema.
Read ai-context/BUSINESS_RULES.md to understand what the endpoint does
(so you can write meaningful tests) but do NOT implement any business logic here.
```

---

## 4.2 Java API Client with Retry

```javascript
// src/utils/httpClient.js
import axios from 'axios';

const JAVA_API_URL = process.env.JAVA_API_URL || 'http://localhost:8080';
const MAX_RETRIES = 3;
const RETRY_DELAY_MS = 500;

async function callJavaApi(path, body, retries = 0) {
  try {
    const response = await axios.post(`${JAVA_API_URL}${path}`, body, {
      timeout: 10000,
      headers: { 'Content-Type': 'application/json' }
    });
    return response.data;
  } catch (error) {
    if (retries < MAX_RETRIES && isRetryable(error)) {
      await sleep(RETRY_DELAY_MS * Math.pow(2, retries));
      return callJavaApi(path, body, retries + 1);
    }
    throw new JavaApiError(error);
  }
}

function isRetryable(error) {
  return !error.response || error.response.status >= 500;
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

class JavaApiError extends Error {
  constructor(originalError) {
    super(`Java API call failed: ${originalError.message}`);
    this.statusCode = originalError.response?.status || 502;
    this.originalError = originalError;
  }
}
```

---

## 4.3 Request Validation

```javascript
// src/middleware/validateOrder.js — validation BEFORE calling Java
import Joi from 'joi';

const orderSchema = Joi.object({
  orderId:                Joi.string().required(),
  customerId:             Joi.string().required(),
  items:                  Joi.array().items(Joi.object({
                            productId: Joi.string().required(),
                            quantity:  Joi.number().integer().min(1).max(100).required()
                          })).min(1).required(),
  promoCode:              Joi.string().optional().allow(null, ''),
  shippingZone:           Joi.number().integer().min(1).max(5).required(),
  shippingType:           Joi.string().valid('STANDARD', 'EXPRESS', 'OVERNIGHT').required(),
  allowPartialFulfillment: Joi.boolean().default(false),
  orderDate:              Joi.string().pattern(/^\d{4}-\d{2}-\d{2}$/).optional()
});
```

**Why validate in Node.js if Java validates too?**
- Fail fast before hitting Java API (saves a network round-trip)
- Better error messages in Node.js middleware format
- Java validation is the authoritative gate; Node.js validation is a pre-filter

---

## 4.4 Error Handling

```javascript
// src/middleware/errorHandler.js
export function errorHandler(err, req, res, next) {
  if (err.name === 'JavaApiError') {
    // Java service unavailable
    return res.status(502).json({
      error: 'Order processing service unavailable',
      retryable: true
    });
  }

  if (err.isJoi) {
    // Request validation failure
    return res.status(400).json({
      error: 'Invalid request',
      details: err.details.map(d => d.message)
    });
  }

  // Unexpected error
  logger.error('Unhandled error', { error: err.message, stack: err.stack });
  return res.status(500).json({ error: 'Internal server error' });
}
```

**HTTP status code mapping**:
| Java outcome                  | Node.js HTTP status |
|-------------------------------|---------------------|
| status=PROCESSED              | 200                 |
| status=REJECTED (business)    | 422 Unprocessable   |
| Validation failure            | 400                 |
| Java API 500                  | 502 (after retries) |
| Java API unreachable          | 503                 |
| Unexpected Node.js error      | 500                 |

---

## 4.5 Logging Strategy

```javascript
// Structured log per request
logger.info('order.processed', {
  orderId:       result.orderId,
  status:        result.status,
  grandTotal:    result.grandTotal,
  fraudFlagCount: result.fraudFlags.length,
  durationMs:    Date.now() - startTime
});
```

**Never log**: full order bodies (may contain PII), promo codes in cleartext, customer emails.

---

# Phase 5: Verification — Ensuring Zero Functional Regressions

## 5.1 Three-Way Parity Check

For each test case, all three outputs must match (excluding `processedAt`):

```
Java CLI output == Java API output == Node.js API output
```

```bash
#!/usr/bin/env bash
# scripts/compare-outputs.sh

JAVA_CLI_JAR="../java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar"
JAVA_API="http://localhost:8080/api/v1/orders/process"
NODE_API="http://localhost:3000/api/orders"
PASS=0
FAIL=0

compare() {
    local label="$1"
    local input="$2"

    # Get all three outputs
    java -jar "$JAVA_CLI_JAR" "$input" | jq 'del(.processedAt)' > /tmp/cli.json
    curl -s -X POST "$JAVA_API" -H "Content-Type: application/json" -d @"$input" \
      | jq 'del(.processedAt)' > /tmp/java-api.json
    curl -s -X POST "$NODE_API" -H "Content-Type: application/json" -d @"$input" \
      | jq 'del(.processedAt)' > /tmp/node-api.json

    # Compare CLI vs Java API
    if diff -q /tmp/cli.json /tmp/java-api.json > /dev/null; then
        echo "PASS [$label] CLI == Java API"
        PASS=$((PASS+1))
    else
        echo "FAIL [$label] CLI != Java API:"
        diff /tmp/cli.json /tmp/java-api.json
        FAIL=$((FAIL+1))
    fi

    # Compare CLI vs Node API
    if diff -q /tmp/cli.json /tmp/node-api.json > /dev/null; then
        echo "PASS [$label] CLI == Node API"
        PASS=$((PASS+1))
    else
        echo "FAIL [$label] CLI != Node API:"
        diff /tmp/cli.json /tmp/node-api.json
        FAIL=$((FAIL+1))
    fi
}

for f in ../java-order-system/test-inputs/order*.json; do
    compare "$(basename "$f")" "$f"
done

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
```

---

## 5.2 Key Fields to Compare

For parity validation, compare these fields exactly (numeric equality to 2 decimal places):

```json
{
  "status":        "PROCESSED",
  "grandTotal":    1218.14,
  "subtotal":      1120.50,
  "totalTax":      201.69,
  "promoDiscount": 112.05,
  "shippingCost":  8.00,
  "promoCode":     "SAVE10",
  "fraudFlags":    [],
  "errors":        []
}
```

**Do NOT compare**: `processedAt` (timestamp), line item ordering (acceptable to differ if values match).

---

## 5.3 Postman Collection Validation

**AI Prompt**:
```
Using the Postman MCP, create a collection "Order Processing Parity Tests" with:

1. A folder "Java API Tests" with requests for all 5 test inputs
   Each request has: test assertions for status, grandTotal, fraudFlags count

2. A folder "Node.js API Tests" with the same 5 requests pointed at port 3000

3. A folder "Parity Check" that runs both and compares grandTotal

Import test inputs from test-inputs/*.json as request bodies.
Import expected values from expected-outputs/*.json as test assertions.
```

---

## 5.4 Regression Test for Edge Cases

Beyond the 5 happy-path/rejection tests, validate these edge cases:

```bash
# Dangerous goods surcharge is never waived (even with FREESHIP)
echo '{
  "orderId":"EDGE-001","customerId":"C001",
  "items":[{"productId":"P010","quantity":1}],
  "promoCode":"FREESHIP","shippingZone":1,"shippingType":"STANDARD"
}' | curl -s -X POST http://localhost:3000/api/orders -d @- \
   -H "Content-Type: application/json" | jq '.shippingCost'
# Expected: 15.00 (dangerous goods surcharge only)

# Discount cap: PLATINUM (15%) + volume 20+ (12%) = 27%, NOT 40%
echo '{
  "orderId":"EDGE-002","customerId":"C001",
  "items":[{"productId":"P002","quantity":25}],
  "shippingZone":1,"shippingType":"STANDARD"
}' | curl -s -X POST http://localhost:3000/api/orders -d @- \
   -H "Content-Type: application/json" | jq '.lineItems[0].discountedUnitPrice'
# Expected: 11.25 (15 * (1 - 0.27))

# NEWCUST rejected for Gold tier
echo '{
  "orderId":"EDGE-003","customerId":"C002",
  "items":[{"productId":"P002","quantity":1}],
  "promoCode":"NEWCUST","shippingZone":1,"shippingType":"STANDARD"
}' | curl -s -X POST http://localhost:3000/api/orders -d @- \
   -H "Content-Type: application/json" | jq '{promoDiscount, warnings}'
# Expected: promoDiscount=0, warning contains "NEWCUST"
```

---

# What Distinguishes a Strong Candidate

## Excellent (full marks)

**AI Workflow**:
- Created all `ai-context/` markdown files before writing code
- Used CLAUDE.md / .cursorrules to constrain AI behavior
- Created and used at least 2 custom skills
- Used MCP (Postman or Supabase) meaningfully
- Prompt chain decomposes by service, not by "do everything"

**Engineering**:
- Java REST layer adds endpoints without modifying existing services
- OpenAPI spec created before any implementation code
- Node.js is truly a pass-through — no business logic replicated
- `compare-outputs.sh` (or equivalent) runs as part of CI
- All 5 test cases produce identical output through CLI, Java API, and Node API

**Process**:
- Can articulate why each design decision was made
- Identified and documented all 8 legacy patterns from the Java codebase
- Used HALF_UP rounding correctly (verified with exact decimal assertions)
- Edge cases (dangerous goods + FREESHIP, discount cap, expired promo) all pass

## Acceptable

- Used AI to write code but prompted by file, not service concern
- Java API wrapper modifies some existing classes (acceptable if tests still pass)
- No custom skills but did create context markdown files
- Parity validation is manual (curl + visual inspection) rather than automated
- 3-4 of 5 test cases pass

## Insufficient

- Rewrote business logic in Node.js instead of calling Java API
- Used AI to generate boilerplate but did not verify business rule correctness
- No context files — re-explained rules to AI in each prompt
- No parity validation between Java CLI and Node API
- Missing edge cases (dangerous goods surcharge, discount cap)
- Cannot explain what HALF_UP rounding means or why it matters

---

# Evaluation Rubric

| Dimension                         | Weight | Excellent                                    | Acceptable                        |
|-----------------------------------|--------|----------------------------------------------|-----------------------------------|
| AI Workflow Setup                 | 25%    | CLAUDE.md, rules, skills, MCP, context files | Some context files, ad-hoc prompts|
| Business Rule Preservation        | 30%    | All rules correct, edge cases pass           | Core cases pass, edges may fail   |
| Architecture Integrity            | 20%    | No logic in Node.js, clean Java API layer    | Minor logic leakage               |
| Verification Strategy             | 15%    | Automated 3-way parity, regression suite     | Manual curl comparison            |
| Code Quality & Documentation      | 10%    | OpenAPI spec, JSDoc, CI integration          | Ad-hoc documentation              |

**Minimum passing score**: 60%. Architecture integrity failures (logic duplication) are disqualifying regardless of total score.
