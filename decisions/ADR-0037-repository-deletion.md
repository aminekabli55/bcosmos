# ADR 0037 — Removing a repository is a first-class cascading operation

- **Status:** Accepted (Phase 10 follow-up, found while walking the running app)
- **Date:** 2026-10-15
- **Related:** [ADR 0009](ADR-0009-liquibase-migrations.md) (Liquibase owns the schema),
  [ADR 0023](ADR-0023-corpus-ingest-and-mined-repositories.md) (mined repositories),
  [ADR 0024](ADR-0024-mined-repo-analysis-policy.md) (on-demand runs for mined repositories),
  [ADR 0032](ADR-0032-commit-review.md) (the table whose foreign key blocked this), spec v2 §8 and §10

## Context

Registration has existed since Phase 1, and nothing in the product could remove a registration. The API surface
was `GET`/`POST`/`PUT` only — there is no other `DELETE` in `backend` — and registering an already-known path is
refused with HTTP 409 by design (`RepositoryRegistrationService` keys on the canonical path and never overwrites).
The result was a dead end: a user who registered the wrong directory, or who wanted a clean re-registration, had no
way forward from the UI, and the only escape the documentation could offer was hand-written SQL against the dev
database.

Walking that SQL revealed the second problem. `commit_review.repository_id` (changelog 0023) was the **only**
foreign key pointing at `repository` without `ON DELETE CASCADE`; `analysis_run`, `commit_record` and
`worker_file_result` all cascade, and everything below `analysis_run` (jobs, files, symbols, findings, suggestions,
chunks, relationships, transitions) cascades from there. So `DELETE FROM repository WHERE id = …` succeeded only
until the first commit review existed, and then failed with a foreign-key error that names a table the user has
never heard of.

A third issue only appears once deletion exists as a feature: an analysis run that is still `PENDING` or `RUNNING`
is writing to the repository. Deleting the row underneath a running runner is a race, and the loser is a write
against a repository that no longer exists.

## Decision

1. **`DELETE /api/repositories/{id}` exists, and answers 204.** It is the one way to forget a registration.
   `404` for an unknown or already-deleted id (the two are the same answer to the caller), `409` while an analysis
   of that repository is still `PENDING` or `RUNNING`.

2. **Deletion is one statement, and the database cascades.** `RepositoryDeletionService` loads the row, applies the
   in-flight guard, and calls `delete`. It deliberately does **not** enumerate child tables: that list would be
   wrong the first time a later phase adds a table keyed on a run. A hand-maintained cascade is a silent data
   corruption bug; a foreign key is not.

3. **The last non-cascading foreign key is fixed at the schema level** (changelog
   `0024-commit-review-repository-cascade.yaml`): `fk_commit_review_repository` is dropped and re-created with
   `ON DELETE CASCADE`, so it finally matches every other repository child. Nothing about the table's columns or
   rows changes. Fixing it in the migration rather than in a service loop keeps the invariant where it can be
   enforced — and keeps it true for the `analysis-worker` and any future client, not just this service.

4. **Only the registration is removed; the folder is not touched.** BCosmos only ever reads a repository
   (spec §0 rule 4), so deleting the row removes knowledge of the project, never the project. No response body and
   no log line repeats the canonical path (spec §10); the log line carries the id alone.

5. **The UI asks first.** Each row has a Delete control that opens an inline confirmation naming the repository and
   stating what goes with it, and nothing is sent until it is confirmed. A refused delete keeps the row, keeps the
   confirmation open, and shows the backend's own message (the 409 body), rather than optimistically removing a row
   the server still has.

## Consequences

- A mis-registered path is recoverable from the browser, and re-registering that path works immediately afterwards.
- **Deleting is irreversible and it takes history with it.** The runs, findings, symbols, suggestions, chunks,
  relationships, transitions, commit records and commit reviews of that repository are gone. Snapshot comparison
  (the History screen) has nothing left to compare, and the Run-level screens for those runs 404. There is no
  soft delete, no archive and no undo; a two-step confirmation in the UI is the only guard, which matches what the
  tool is for (a local analysis lab) but is not enough for a shared deployment.
- **Mined repositories are deletable too**, which is what an operator needs after a bad ingestion. Their
  `worker_file_result` rows cascade with them. If events for that repository are still queued in Kafka, the worker
  will fail them against a missing repository and route them to the DLQ (ADR 0026) — the delete does not cancel
  work that is already on the broker.
- **The analytics gold layer does not follow immediately.** `agg_*` is built by Spark from the lakehouse, not from
  the operational table, so the Analytics screen can keep showing a deleted repository until the next gold load.
  The per-repository dataset table *is* rebuilt from the operational `repository` dimension and loses the row at
  that point; the metric aggregates are rebuilt from the event stream, which still holds the analyses that really
  happened. Rebuilding the lakehouse without that repository (or filtering on the current dimension) is a separate,
  larger decision about whether deleted data should be purged from the lake.
- Three integration tests run against the real changelog, because a mocked repository would happily "prove" a
  cascade that the schema does not have: one deletes a repository with a run plus a `commit_review` row and asserts
  every derived table is empty (this is the regression test for changeset 0024), one deletes an unknown id and
  expects 404, one deletes a repository with a `RUNNING` run and expects 409 with the row still present.

## Alternatives considered

- **Leave it to SQL, document the statement.** This is where we started. It does not scale to a user who cannot
  reach Postgres, it silently depended on a cascade that one table was missing, and it bypasses the in-flight guard
  entirely — the very race the 409 exists to prevent.
- **Soft delete (`status = ARCHIVED`).** Rejected for now: every read path would need a filter (repository list,
  corpus status, commit list, analytics dimension), and the screens have no concept of an archived repository, so
  the change would touch far more code than the feature is worth. It remains the right answer if BCosmos ever
  becomes multi-user.
- **Delete children table by table in the service.** Explicit, but it duplicates the schema in Java and rots
  quietly. The one thing it buys — an audit trail of what was removed — is not something the product needs today.
- **Fix the cascade in the service instead of the migration** (delete `commit_review` rows first). It would leave
  the schema lying about its invariants, and leave the same trap for the next writer.
