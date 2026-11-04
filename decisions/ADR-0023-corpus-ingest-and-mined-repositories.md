# ADR 0023 — Corpus ingest: an allowlist at ingest time, mined repositories without a local path

- **Status:** Accepted (Phase 4)
- **Date:** 2026-10-14
- **Related:** [ADR 0002](ADR-0002-postgres-pgvector.md) (Postgres), [ADR 0022](ADR-0022-event-contract-in-analysis-core.md)
  (event contract), spec v2 sections 5.1, 6, 8 and 10

## Context

`POST /api/corpus/ingest` is the only way into the corpus, and it is an operator-facing endpoint that makes another
process clone code from the internet. Two things must not be possible: cloning something that is not on an allowlist,
and having a repository row that claims to know a path the backend does not own. The spec's `repository` table has
`canonical_local_path` as required metadata for a registered local repository, but a mined repository lives in the
miner's own cache.

## Decision

1. **The allowlist is enforced at ingest time, in the backend.** `bcosmos.corpus.allowed-hosts` is checked before any
   row or event exists, so a URL that is not allowed never reaches the miner and cannot make it clone anything. The
   check is `https` + host membership; a request that fails it is rejected with a reason.
2. **A rejected URL does not fail the whole request.** Each URL is answered individually: the accepted ones are
   registered and asked for, the rejected ones come back with why. An operator fixing a typo in a list of twelve URLs
   should not have to resubmit the eleven that were fine. The response therefore reports partial success explicitly
   rather than pretending the request was atomic.
3. **`repository.canonical_local_path` became nullable, and a constraint says what a row may be.** A `LOCAL` row has a
   canonical path and no remote URL; a `MINED` row has a remote URL and no path. `MINED` rows are unique by
   `remote_url` (Postgres allows many `NULL`s in a unique index, so `LOCAL` rows are unaffected). The alternative —
   storing the miner's cache path — was rejected: the backend would be recording a path it does not own and must
   never read.
4. **An ingest request is published after the transaction commits.** Registering a repository and asking the miner to
   walk it are one logical step, and the message must not exist if the row does not. The publication is registered as
   a transaction synchronization, the same pattern the run service uses for its dispatch event.
5. **`commit_record` is the pipeline's progress record, and it is written idempotently.** One row per
   `(repository_id, hash)`, with `author_hash` holding an already-hashed author identity and never an email or a
   name. `changed_java_files` is a count, not the list: the list travels on the event and would be re-derived
   metadata in this table.
6. **`GET /api/corpus/status` reads Postgres, not Kafka.** An endpoint that asks a broker what happened answers
   differently depending on when it is called; the table is the record of what actually happened.

## Consequences

- The pipeline is off by default (`bcosmos.kafka.enabled=false`), so ingest answers with a clear "the pipeline is
  off: set bcosmos.kafka.enabled=true" instead of a 200 that quietly did nothing. That refusal is a deliberate
  feature of `DisabledEventPublisher`.
- The miner is still to be written and will check the host itself before cloning, so a misconfigured backend cannot
  make it clone an arbitrary host. Only the backend side of that pair exists today.
- Nothing writes `commit_record` yet: the miner produces the commit events and the worker consumes them, and both are
  still to be implemented. Until then the table exists, the status endpoint reads it, and it stays empty — which the
  status response states honestly rather than implying progress.
- A mined repository has no local path, so nothing in the backend can analyse it directly; its analysis happens in
  the worker from the commit blobs. `POST /api/repositories/{id}/analyses` on a `MINED` repository is therefore a
  separate open question (see the phase guide's next step).
