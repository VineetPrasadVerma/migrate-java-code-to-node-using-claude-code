Analyze the Java service file provided and produce a structured specification.

Output exactly this format:

## Service: [Name]
**Responsibility**: One sentence.

### Dependencies
- [what it depends on]: [why]

### Public Methods
For each public method:
**[methodName]([params])**
- Inputs: field names, Java types, constraints
- Output: returned type and key fields
- Rules: numbered list of every business rule (include exact threshold values)
- Failures: exact error messages this method can produce

### Legacy Patterns
List any Java patterns that will affect Node.js migration:
- Pattern name: what it does, what Node.js equivalent should be

### Draft API Contract
If this service were a REST endpoint:
  METHOD /api/v1/[path]
  Request: [fields]
  Response: [fields]
  Errors: [HTTP codes and when]

Do NOT suggest improvements. Describe only what exists in the code.
