# ADR 0044 — An index build is bounded per call and resumable

- **Status:** Accepted (RAG follow-up after Phase 11)
- **Date:** 2026-10-19
- **Related:** [ADR 0021](ADR-0021-rag-and-validated-suggestions.md) (the index, the chunker and the suggestion flow),
  [ADR 0040](ADR-0040-rate-limit-backoff.md), [ADR 0041](ADR-0041-rate-limit-patience-for-a-sequence.md),
  [ADR 0045](ADR-0045-gemini-structured-calls-in-2026.md)

## Context

A 458-file repository chunks into **1,636 chunks**. At the 16-text batch size that is ~103 embedding calls, and on a
free Gemini key (about 100 *texts* per minute — a batch counts as its texts, not as one request) the whole index
needs roughly 16 minutes. The build was:

- **all-or-nothing**: every vector was held in memory and written only after the last batch, so a rate limit at batch
  17 of 103 discarded the 256 vectors that had already been paid for;
- **synchronous and unbounded**: the suggestion request triggered the whole build, so the caller waited for minutes
  and the patience budget (2 minutes, ten windows) ran out long before the index was complete;
- **unprogressed**: re-running started at chunk 1, so it hit the same wall at the same batch, forever.

Observed on a real run: 16 batches succeeded, the third 429 could not be honoured inside the remaining patience, and
the run's `code_chunk` stayed empty. "Suggest a fix" could never complete on that repository, however often it was
tried. That is precisely the limitation ADR 0040 recorded and ADR 0041 only partially lifted.

## Decision

1. **Each finished batch is written immediately.** `CodeChunkStore.upsertAll` stores one batch in its own short
   transaction, under the existing `(run_id, symbol_id, embed_model)` conflict key. No transaction is open during a
   provider call, and a failure after batch N costs only batch N+1.
2. **A build resumes instead of restarting.** `storedTexts` returns the text of every stored chunk and a build skips
   every draft whose stored text is unchanged. `prune` removes rows the current chunk plan no longer has, so a stale
   row cannot make a partial index look complete.
3. **One call embeds a bounded slice.** `bcosmos.retrieval.index-texts-per-call` (default **96**, just under a free
   tier's 100 texts/minute) caps how many missing texts one suggestion may send. The rest of the index follows with
   the next suggestion. `0` (or a negative value) means "embed the whole run", which is only sensible where there is
   no per-minute quota.
4. **The finding's own file is indexed first.** `SuggestionService` passes the file path to `ensureIndex`, which
   orders the missing chunks so that file's declarations are embedded before the rest of the repository. The first,
   bounded build therefore gives that finding useful neighbours rather than 96 arbitrary chunks.
5. **A partial index is a smaller evidence bundle, not a wrong one.** Retrieval searches what is stored. The notes
   say so: "the semantic index covers 288 of 1636 chunk(s) of this run; each further suggestion indexes more of it."
   `SuggestionContext.Index.chunks` already carries the stored count, and the AI Review screen shows it next to the
   run's chunk count.

No schema change: `code_chunk` was already per-run and per-model, and completeness is derived (`stored == plan`)
rather than stored as a flag.

## Consequences

- The first suggestion on a large repository answers after ~10 seconds of indexing instead of aborting after two
  minutes; each later suggestion adds another slice, and the 80th or so completes the index.
- Re-running with an unchanged run re-embeds nothing; a changed chunking limit re-embeds only the chunks whose text
  changed.
- Retrieval quality grows with use. Early suggestions see the finding's file and its neighbours; later ones see the
  whole repository. The note in the evidence bundle keeps that visible instead of pretending the index is complete.
- A build can still fail (a spent daily quota, a provider outage): what it wrote stays, and the next attempt
  continues. There is no background worker yet — the work is still paid for by the request that asks for a
  suggestion, just in bounded slices.
- Two suggestions fired at the same instant can both embed the same slice: the upsert makes that harmless
  (duplicate calls, not duplicate rows) but not free. Pacing requests remains the caller's job.
