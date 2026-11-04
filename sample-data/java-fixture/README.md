# java-fixture — Phase 1 analysis sample

A deliberately small Java project used to drive Phase 1 acceptance
(register, analyse, browse findings). BCosmos never builds or runs
anything inside this tree.

The rules that may fire are the four in
`analysis-core/src/main/resources/pmd/bcosmos-baseline.properties`, described in
[docs/rule-catalog.md](../../docs/rule-catalog.md). That file's sha256 is stored on every run as its rule set
hash, so two runs over this fixture are only comparable while the list is unchanged.

## Findings a run over this tree must produce

| Rule | Where | Why |
|---|---|---|
| `EmptyCatchBlock` | `IngestRunner.ingestAll`, `IngestRunner.java:22` | the `catch` body is empty |

Plus one parse failure that is counted, not hidden:

- `SyntaxError.java` does not compile. The run records it as a failed file and still completes.

Numbers for a run over this tree: 7 files discovered, 6 parsed, 1 failed, **1 finding**, 28 symbols
(verified against the real API on 2026-10-15).

`IngestRunner.ingestAll` also assigns a local `files` that is never read. It produces **no** finding on purpose:
`UnusedLocalVariable` is deliberately not in the baseline, because under the pinned PMD 7.28.0 it reports nothing
when a scope has exactly one unused local (see [docs/rule-catalog.md](../../docs/rule-catalog.md)). Add the rule to
`bcosmos-baseline.properties` and this table has to gain a row — and every run a new `rulesetHash`.

## Two traps this fixture exists to keep visible

- **The catch parameter must not be named `ignored`, `ignore` or `expected`.** PMD's `EmptyCatchBlock` skips those
  names by default (`allowExceptionNameRegex`), so the finding disappears without any error. This fixture used
  `ignored` at first, and the empty catch produced nothing.
- **`node_modules/` is an ignored directory** and must never be walked.

## Shape

A controller → service → repository chain (`BookController`, `BookService`, `BookRepository`,
`InMemoryBookRepository`, `Book`) plus `IngestRunner` and the broken `SyntaxError`.
