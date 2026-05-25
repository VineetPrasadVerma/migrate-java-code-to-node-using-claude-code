# AI Skills Reference
## Reusable Prompt Templates for the Migration

Place these in `.claude/skills/` or equivalent location for your AI IDE.

---

## `/analyze-java-service`

**Trigger**: When starting analysis of any Java service class.

```
Analyze [SERVICE_FILE].java and produce a structured service specification.

Output format:

## Service: [Name]

### Responsibility
One sentence describing what this service does.

### Dependencies
- [Repository/Service it calls]: for what purpose

### Public Methods
For each method:

**[methodName](params)**
- Input: [field names, types, constraints]
- Output: [returned type, key fields]
- Rules: numbered list of all business rules applied
- Failures: error messages this method can produce

### Legacy Patterns
Anything that affects Node.js migration (legacy types, patterns, assumptions)

### API Contract Sketch
Draft OpenAPI endpoint if this were a REST API:
  METHOD /api/v1/[path]
  Request body fields: ...
  Response fields: ...

Do not suggest improvements or refactors. Describe only what exists.
```

---

## `/create-node-service`

**Trigger**: When implementing a Node.js service module that calls a Java endpoint.

```
Create src/services/[name]Service.js that calls the Java API endpoint
POST [JAVA_ENDPOINT].

Requirements:
- Import httpClient from '../utils/httpClient.js'
- Export named functions (not default)
- Each function: validate non-null required fields, call Java API, return typed response
- Handle Java API errors: throw with meaningful message and HTTP status
- JSDoc comments on all exports

Business context:
- Read ai-context/BUSINESS_RULES.md [relevant section]
- This service does NOT implement business logic — it calls Java
- Java API response schema: [paste relevant schema from openapi.yaml]

Do not import from other Node.js services. Do not implement pricing, fraud,
or any other business calculation. If logic seems missing, it is in Java.
```

---

## `/create-route`

**Trigger**: When creating an Express route handler.

```
Create src/routes/[name].js as an Express Router for [ENDPOINT].

Requirements:
- Import and apply validateOrder middleware for request validation
- Import service from '../services/[name]Service.js'
- Map service results to HTTP status codes:
    PROCESSED status  → HTTP 200
    REJECTED status   → HTTP 422
    Validation error  → HTTP 400 (middleware handles this)
    JavaApiError      → HTTP 502
    Unknown error     → HTTP 500
- Log: logger.info() at start and end, logger.error() on failure
- No business logic in route handler

Response body: pass through Java API response unchanged.
Attach to app in src/app.js at path /api/[name].
```

---

## `/verify-parity`

**Trigger**: After implementing any endpoint, to verify functional equivalence.

```
Run parity verification for test case [ORDER_FILE]:

1. Java CLI:
   java -jar java-order-system/target/*.jar [ORDER_FILE]

2. Java REST API:
   curl -s -X POST http://localhost:8080/api/v1/orders/process \
     -H "Content-Type: application/json" -d @[ORDER_FILE]

3. Node.js API:
   curl -s -X POST http://localhost:3000/api/orders \
     -H "Content-Type: application/json" -d @[ORDER_FILE]

Compare these fields across all three outputs (exclude processedAt):
  status, grandTotal, subtotal, totalTax, promoDiscount, shippingCost,
  fraudFlags[].flag, fraudFlags[].severity, errors[]

Report any discrepancy with:
- Field name
- Expected value (from Java CLI)
- Actual value (from API)
- Likely cause based on BUSINESS_RULES.md

If all match: "PARITY VERIFIED for [ORDER_FILE]"
```

---

## `/extract-business-rule`

**Trigger**: When reading a Java method and need to document a specific rule.

```
Read [METHOD_OR_FILE] and extract all business rules.

Format each rule as:
  RULE-[N]: IF [condition with exact threshold/value] THEN [effect]
  Source: [class].[method], line ~[N]
  Migration note: [anything non-obvious for Node.js]

Flag with ⚠ any rule that involves:
- Magic numbers (undocumented constants)
- String parsing (legacy date formats, enum strings)
- Floating point arithmetic with specific rounding
- Stateful operations (stock changes, order storage)

Output as additions to ai-context/BUSINESS_RULES.md
```

---

## `/design-api-contract`

**Trigger**: Before implementing any Java REST endpoint.

```
Design the REST API contract for [SERVICE].

Based on:
- ai-context/BUSINESS_RULES.md [service section]
- Existing Java model classes: [list relevant models]

Produce OpenAPI 3.0 YAML for:
  POST /api/v1/[path]

Include:
- requestBody schema: all fields with types, required/optional, constraints
- responses:
    200: success schema with ALL response fields
    400: validation error schema
    500: server error schema
- At least one example request (use test-inputs/order1_happy_path.json values)
- At least one example response (use expected-outputs/order1_expected.json values)

Use exact field names from Java models. Do not rename or restructure fields.
Append to api-contracts/openapi.yaml under /paths.
```
