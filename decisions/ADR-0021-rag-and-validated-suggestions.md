# ADR 0021 — Retrieval, suggestions, and a verdict BCosmos computes itself

- **Status:** Accepted (Phase 3)
- **Date:** 2026-10-14
- **Related:** [ADR 0002](ADR-0002-postgres-pgvector.md) (pgvector), [ADR 0019](ADR-0019-ai-gateway.md) (AI gateway),
  spec v2 sections 7.2, 7.3 and 8

## Context

Phase 3 turns "explain this finding" into "propose a fix for it, and prove what you can about that proposal".
Two things can go wrong that a smarter model does not fix: the model is given the wrong code, and the model is
believed. So the phase has two halves — retrieve the evidence the model is allowed to see, and compute a verdict
about the answer that does not depend on the model's own confidence.

## Decision

1. **Retrieval is deterministic and explained.** `Retriever` builds one bundle from a token budget: the enclosing
   declaration (the chunk containing the finding), its direct callers and callees when the symbol table can resolve
   them, then top-k similar chunks by cosine distance, plus the baseline rule's own description. Every item is
   returned to the browser with its file, line range, kind (`ENCLOSING` / `SIMILAR`) and similarity, so a reader can
   see what the model saw and judge the answer against it. The bundle records `estimatedTokens`, `truncated` and
   `chunksConsidered`; truncation is surfaced in the UI rather than hidden.
2. **Chunks come from the symbol table, one per method or class.** `SymbolChunker` produces a chunk per symbol with a
   header (qualified name, file, signature) and stores it in `code_chunk` with the embedding, the embedding model
   and the dimension. Indexing is per analysis run, memoized by `(run, embed model, chunker version)`, and reused
   when the same run is asked again — the second question about a run does not re-embed it. Symbols whose source was
   not stored are counted and reported (`symbolsWithoutSource`), not silently skipped.
3. **The verdict is computed, not asked for.** `PatchValidator` applies the proposed unified diff in memory to the
   analysed revision (`UnifiedDiff.parse` rejects a patch that does not match), re-parses the patched file with the
   Java parser, re-runs PMD on it, and reports `{schemaOk, evidenceOk, diffApplies, parses, targetFindingGone,
   newFindings[]}` plus `notes`. `allChecksPassed` is convenience for the UI, never an approval: the human decision
   is `status` (`PROPOSED` → `ACCEPTED` / `REJECTED` / `DEFERRED`).
4. **A patch is never applied.** There is no code path that writes the diff to a repository. The diff is a proposal
   shown to a reviewer; the accept/reject/defer decision moves the finding's triage state and nothing else.
5. **Evidence citations are checked against the run.** `evidenceOk` is false when the answer cites a file and line
   the run does not contain, so a plausible-sounding citation that does not exist is visible as a failed check
   rather than as a trustworthy reference.
6. **The verdict is stored with the suggestion.** One `suggestion` row keeps the model, prompt version, tokens,
   latency, the answer and its verdict, so reopening a suggestion shows the same thing the reviewer saw without
   another model call. `GET /api/findings/{id}/suggestions` and `GET /api/suggestions/{id}` are additions to the
   spec's endpoint table for exactly that reason.
7. **The target-finding rule is verified, not assumed.** `targetFindingGone` is computed by re-running the baseline
   rule set on the patched source and checking that the finding's rule no longer reports on the patched lines. A
   patch that merely moves the violation elsewhere does not pass, and a check that cannot run (an unparseable patch)
   is reported as failed with a note — never as passed.
8. **Compilation is out of scope, and said so.** `notes` records that the patched file was parsed and analysed, not
   compiled: there is no classpath, so a patch can pass every check and still not build. The UI shows the notes
   under "What was not checked" instead of implying a guarantee.
9. **Code stays untrusted input.** Prompts put code and comments inside explicit evidence markers and state that
   they are data; a fixture with "ignore previous instructions" in a comment is a test, and it does not change the
   outcome. The model still has no tools.

## Consequences

- A reviewer can reject a suggestion on evidence alone: a failed `diffApplies`, a missing citation or a new finding
  is shown next to the answer instead of behind it.
- Embedding a large run costs money and time once; the index is reused per run and model, and the first-call cost is
  stated in the UI.
- The verdict is only as strong as the pinned rule set: `targetFindingGone` is meaningful for baseline rules and
  says nothing about a smell no rule describes. That is accepted — the baseline is what the project measures
  against, and it is documented in [docs/rule-catalog.md](../rule-catalog.md).
- The baseline list itself changed in this phase: `UnusedImports` (removed from PMD 7) and `UnusedLocalVariable`
  (not reported on the simplest case under PMD 7.28.0) were dropped, and `UnusedPrivateMethod` and
  `AvoidCatchingGenericException` were added. The rule set hash changes with it, which is the intended identity of a
  run; `JavaAdapterAnalyzeTest.everyBaselineRuleFires` now pins that each listed rule really fires.
