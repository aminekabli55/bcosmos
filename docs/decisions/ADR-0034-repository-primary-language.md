# ADR 0034 — A repository's primary language is what its files are

- **Status:** Accepted (Phase 10 follow-up)
- **Date:** 2026-10-15
- **Related:** [ADR 0012](ADR-0012-language-adapter.md) (language adapter pattern),
  [ADR 0033](ADR-0033-typescript-adapter.md) (TypeScript adapter and polyglot pipeline), spec v2 §3.1 and §12

## Context

Phase 1 had one adapter, so `RepositoryRegistrationService` stored the constant `"java"` as the repository's
`primary_language` and said in a comment that *"language detection per repository arrives with the second adapter
(spec section 3.1, phase 10)"*.

Phase 10 shipped the second adapter and the per-language support badges, but the promise was never kept: nothing
ever wrote that column again. The consequence was visible in the running application — a TypeScript-only repository
registered as `sample-data/angular-fixture` displayed **`Java · L3`** in the repository list while its four files
were analysed as `typescript` and its four findings came from the ESLint baseline. The badge is read from
`repository.primaryLanguage` (`frontend/src/app/repositories/repositories.html`), so the UI was reporting the wrong
support level for exactly the case Phase 10 exists to demonstrate.

## Decision

1. **Registration still stores `"java"`.** Registering a repository must not read the tree: `SafePathValidator`
   only canonicalises the path, nothing is discovered, and a repository may legitimately be empty or unreadable at
   that moment. The column is a *current best answer*, not a user-declared fact.

2. **A finished run recomputes it from the files that run really analysed.** In `AnalysisRunService.finish(...)`
   the backend aggregates `file_record.language` for the run
   (`FileRecordJpaRepository.countLanguagesByRunId`, aggregated in SQL so a large run does not load its rows) and
   stores the dominant language through `PrimaryLanguageResolver`.

3. **The resolver is a pure function with a deterministic tie-break.** Highest file count wins; equal counts are
   broken alphabetically so two runs over the same tree cannot disagree; a run that saw no usable language leaves
   the stored value untouched instead of blanking it.

4. **A failed run does not relabel the repository.** Only `finish(...)` performs the census; `fail(...)` keeps the
   previous value, because a run that died may have seen only part of the tree.

## Consequences

- The repository list tells the truth for single-language projects, and the badge matches the language that
  produced the findings in the Quality screen.
- Mixed repositories are reported as the language of the majority of their files. That is a coarse statement, and
  the per-file `language` on `file_record` remains the precise one — no API response should present the repository
  value as more than a summary.
- The change is additive: one column update, no schema change, no Liquibase changeset, no API shape change.
- **Known limitation:** the census runs on the backend's on-demand path. Repositories ingested through the miner
  keep the language recorded at ingest time (`RepositoryEntity.mined(...)` stores `"java"`), because the worker owns
  those rows and does not run this census. The corpus today is Java-only, so nothing displays wrongly, but a
  TypeScript repository mined through Kafka would need the same call in the worker's commit consumer.
- `RepositoryRegistrationService`'s obsolete comment is replaced by a pointer to this ADR.
