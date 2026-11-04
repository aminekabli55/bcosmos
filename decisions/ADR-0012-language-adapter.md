# ADR 0012 — Language support through a `LanguageAdapter` interface

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (interface in Phase 1, second language in Phase 10)
- **Source:** spec v2 §2.2

## Context

The pipeline (Kafka, Spark, database, API, frontend, LLM gateway, fingerprinting) is identical for every language;
only parsing, symbols, relationships and rule tooling differ. Scattering `if (language == "java")` through the
codebase would make a second language a rewrite.

## Decision

Everything language-specific lives behind `LanguageAdapter` in `analysis-core`
(`languageId`, `supports`, `parse`, `extractSymbols`, `extractRelationships`, `analyze`, `normalizeSnippet`,
`checkPatch`). `JavaAdapter` is the only implementation in Phase 1 (Level 3 support); `TypeScriptAdapter` is added
in Phase 10 at Level 1–2. Every event, table row and API response carries a `language` field from day one.

## Consequences

- The second language is proven by passing the shared adapter contract tests without touching Kafka, Spark, backend
  or AI code.
- The interface must stay honest about support levels, and the UI has to show which level a language has.
- JavaParser/PMD dependencies stay inside `analysis-core`; `backend` depends on the module, not on the parsers.
