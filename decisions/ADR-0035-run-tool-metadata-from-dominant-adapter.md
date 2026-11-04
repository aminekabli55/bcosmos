# ADR 0035 — A run's tool metadata comes from the adapter that did the work

- **Status:** Accepted (Phase 10 follow-up)
- **Date:** 2026-10-15
- **Related:** [ADR 0012](ADR-0012-language-adapter.md) (language adapter pattern),
  [ADR 0010](ADR-0010-json-event-envelope.md) (a run's identity is its version tuple),
  [ADR 0033](ADR-0033-typescript-adapter.md) (TypeScript adapter), [ADR 0034](ADR-0034-repository-primary-language.md)

## Context

`AnalysisRunner` recorded a run's tools **before** discovery, with
`LanguageAdapterRegistry.primaryAdapter()` — "the first registered adapter". That was correct while one adapter
existed, and its javadoc said as much: *"per-language run metadata arrives with the second adapter (phase 10)"*.

With `JavaAdapter` registered first, every run was stamped with `com.github.javaparser:javaparser-core` as its
parser and `net.sourceforge.pmd:pmd-java` as its analyzer. Saw it in the running application: run #7 of
`sample-data/angular-fixture` — four TypeScript files, four ESLint findings — shows `javaparser` and `pmd-java` on
the Quality screen, while `TypeScriptAdapter` reports `io.github.bonede:tree-sitter-typescript` and
`eslint:9.12.0/typescript-eslint:8.10.0`.

This is not cosmetic. `analyzerVersion` and `rulesetHash` are part of how a run is identified and how two runs are
declared comparable (ADR 0010), and they are what the worker's idempotency key uses. A TypeScript run claiming the
Java toolchain is a false statement in the run record, the API and the UI.

## Decision

1. **Record the tools after discovery.** `AnalysisRunner` calls `recordTools` once `recordDiscovery` has run, so it
   knows which files the run is actually about.

2. **`LanguageAdapterRegistry.dominantAdapter(paths)`** returns the adapter that handles the most of the discovered
   files. Ties keep the adapter of the first file, which is deterministic because discovery sorts paths. A run that
   discovered no supported file falls back to `primaryAdapter()`, so the row still records the tools that were on
   offer rather than nothing.

3. **One adapter describes one run.** A mixed repository records the toolchain of the language that produced most of
   its findings. This is the same coarse-but-honest rule as the primary-language census in
   [ADR 0034](ADR-0034-repository-primary-language.md); the precise record stays per file (`file_record.language`)
   and per finding (`finding.analyzer_version`, `finding.ruleset_hash`).

## Consequences

- The Quality screen names the tools that really produced the findings, and a TypeScript run no longer claims PMD.
- The rule set hash shown for a run is now the hash of the baseline that was actually applied, which is what the
  "two runs are comparable" promise depends on.
- `primaryAdapter()` keeps one caller: the fallback path. It is no longer used as a run's metadata source.
- **Known limitation:** the `analysis-worker` pipeline (`CommitAnalysisPipeline`) selects its adapter per file and
  the mined-repository run rows are written by the worker; this ADR changes the backend's on-demand path only. The
  corpus is Java-only today, so no mined row is mislabelled, but a polyglot corpus would need the same call there.
