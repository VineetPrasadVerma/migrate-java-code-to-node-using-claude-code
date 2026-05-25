# AI-Assisted Legacy Migration: Java Order Processing System → Node.js + Java CLI

This repository demonstrates an **AI-first legacy modernization workflow** where the complete migration process, context extraction, verification, and tooling setup were developed using AI-assisted development practices.

The project starts with a **Java-based Order Processing System** created using **Claude Code** and migrates it into a **Node.js + Java hybrid architecture**, where:

- **Java remains the source of truth** and continues to own all business logic.
- **Node.js acts only as the API orchestration layer**, wrapping the Java application via CLI integration.
- No pricing, fraud, promotion, validation, or order-processing logic is reimplemented in Node.js.

The repository also demonstrates how to use **Skills**, **Rules**, and **MCPs** effectively during large codebase migrations.

## AI Workflow Used

### Skills
Skills were used to build persistent project understanding and reusable workflows:

- Extract business knowledge into `ai-context/BUSINESS_RULES.md`
- Extract legacy nuances into `ai-context/LEGACY_PATTERNS.md`
- Generate migration validation scenarios in `ai-context/TEST_CASES.md`
- Create automated parity verification using `verify-parity`

These artifacts allow subsequent LLM sessions to understand the project without rediscovering context.

### Rules

Project rules and migration constraints were captured using:

- `CLAUDE.md` for project memory and architectural guardrails
- Cursor rules for:
  - Service analysis
  - Business rule extraction
  - Node wrapper generation
  - Java ↔ Node parity validation

This ensured migration consistency and prevented accidental business logic movement.

### MCP Integration

The workflow also uses **Postman MCP** to:

- Create workspaces automatically
- Generate API collections
- Register migration test APIs
- Automate verification setup

## Goal

The objective of this repository is not only to migrate a Java application but also to demonstrate a **modern AI-assisted development workflow** for:

- Legacy modernization
- Context extraction
- Rule preservation
- Test derivation
- Automated parity verification
- Tool-integrated development workflows

---

# Migration Playbook — Step-by-Step Execution Guide
PLAYBOOK.md
