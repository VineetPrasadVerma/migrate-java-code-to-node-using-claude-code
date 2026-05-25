Create a Node.js service module that spawns the Java JAR via CLI.

I will tell you: which request fields, which response fields to handle.

Requirements:
- ES modules (import/export)
- File location: nodejs-api/src/services/[name]Service.js
- Uses child_process.execFile to spawn Java JAR (reads JAR_PATH and JAVA_BIN from process.env)
- Export named async functions (not default export)
- Each function: validate required fields are present, send JSON via stdin, read JSON from stdout
- On Java process error or non-zero exit: throw with { message, retryable: false }
- JSDoc on every export

IMPORTANT: Do NOT implement any business logic. This module only:
1. Validates the request has required fields (not business validation — just presence check)
2. Spawns the Java JAR with the request as stdin
3. Returns the parsed stdout response unchanged

Business logic lives in Java. If something seems missing from Node.js, it is in Java.
REJECTED status is a valid business outcome — return it as-is, do not throw an error.
