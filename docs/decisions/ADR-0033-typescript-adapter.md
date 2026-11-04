# ADR 0033 — TypeScript language adapter and polyglot pipeline

- **Status:** Accepted (Phase 10)
- **Date:** 2026-10-15
- **Related:** [ADR 0012](ADR-0012-language-adapter.md) (Language adapter pattern),
  [ADR 0025](ADR-0025-shared-pipeline-port.md) (Shared pipeline port),
  [ADR 0027](ADR-0027-idempotent-upsert-shape.md) (Idempotent worker sinks),
  spec v2 sections 3.1, 10 and 12

## Context

Phase 10 implements support for a second language—**TypeScript**—to prove the `LanguageAdapter` architecture (spec §3.1, §12).
The design goals are:
1. **Support Level 2 (Symbols & AST Linting):** Parse TypeScript and TSX, extract symbols (classes, interfaces, methods, functions, variables) with accurate line ranges, evaluate an AST-based rule baseline matching core ESLint rules (`no-empty`, `no-unused-vars`, `no-explicit-any`, `no-console`), and record parse failures gracefully without crashing. Full call graph and inheritance hierarchy is explicitly reserved for Java (Support Level 3).
2. **Shared Adapter Contract Test Suite:** Establish `LanguageAdapterContractTest`, an abstract contract test suite verifying that all adapters uphold the contract: path validation, parse status, symbol extraction, rule set hashing, and reproducible analyzer versions.
3. **Multi-Adapter Support in Pipeline & Worker:** Register `TypeScriptAdapter` in `backend`'s `LanguageAdapterRegistry` and `analysis-worker`'s `CommitAnalysisPipeline` without modifying Kafka topics, Spark lakehouse, AI gateway, or database schemas.
4. **UI Language Support Levels:** Clearly indicate language support tiers in the web frontend (`Java · Level 3`, `TypeScript · Level 2`).
5. **Sample Fixture Verification:** Provide `sample-data/angular-fixture` demonstrating that an Angular/TypeScript project produces findings, symbols, and parse failures through the pipeline.

## Decision

**1. Tree-sitter Java Bindings for TypeScript.**
`TypeScriptAdapter` uses `io.github.bonede:tree-sitter:0.25.3` and `io.github.bonede:tree-sitter-typescript:0.23.2` via `org.treesitter.TreeSitterTypescript.TREE_SITTER_TYPESCRIPT()`.
- Parsing is in-memory and sandboxed: untrusted code is never executed (spec §0 rule 4).
- Syntax errors flagged by Tree-sitter (`tree.getRootNode().hasError()`) are recorded as `ParseStatus.FAILED` with line positions, rather than dropped.
- Definition files (`.d.ts`) are excluded from analysis (`supports(path)` returns false).

**2. Baseline Rules in `analysis-core`.**
The initial TypeScript rule baseline is pinned in `analysis-core/src/main/resources/eslint/bcosmos-baseline.properties` with SHA-256 fingerprinting:
- `eslint/no-empty`: empty block statements and catch blocks without handling logic.
- `eslint/no-unused-vars`: declared variables and parameters not referenced in scope (excluding `_`-prefixed parameters).
- `eslint/no-explicit-any`: explicit `any` type annotations.
- `eslint/no-console`: `console` logging calls in application code.

Rule explanations are integrated into `backend/src/main/resources/rules/rule-descriptions.properties` and validated by `RuleCatalogTest`.

**3. Shared Contract Test Suite.**
`LanguageAdapterContractTest<T extends ParsedFile>` in `analysis-core` runs identical contract assertions across `JavaAdapterContractTest` and `TypeScriptAdapterContractTest`:
- Rejection of null, blank, and absolute paths (`IllegalArgumentException`).
- File extension filtering (`supports(...)`).
- Line count accuracy on valid sources.
- Recording syntax errors as `parsed() == false` with non-empty diagnostics.
- Non-empty analyzer versions and deterministic rule set hashes.
- Extraction of symbols with start/end line bounds.

**4. Pipeline and Worker Dispatch.**
- `CommitAnalysisPipeline` accepts `List<LanguageAdapter<?>> adapters` and routes files by `adapter.supports(path)`.
- `AnalysisRequestedConsumer` discovers `.java`, `.ts`, and `.tsx` files (ignoring `node_modules` and `.d.ts`) and records each file's resolved `languageId` (e.g. `typescript`).

**5. Frontend Support Badges.**
`frontend/src/app/core/language-support.ts` provides tier resolution:
- Level 3 (`Java`): Full Call Graph & Relationships (`badge--lang-l3`).
- Level 2 (`TypeScript`): Symbols & AST Linting (`badge--lang-l2`).
Displayed in repository listings, registration hints, and quality parse failure tables.

## Consequences

- **Polyglot Foundation:** The architecture is proven to accommodate additional languages without modifying Kafka, Spark, database schemas, or core backend services.
- **Honest Scoping:** TypeScript support is explicitly scoped at Level 2; the UI and API do not pretend full call graph support exists for TypeScript, avoiding false precision.
- **Contract Enforcement:** New language adapters must extend `LanguageAdapterContractTest`, ensuring future adapters (e.g. Python, Go) meet baseline reliability standards.
