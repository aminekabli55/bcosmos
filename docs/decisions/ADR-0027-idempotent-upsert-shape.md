# ADR 0027 — Idempotent consumer sinks and worker_file_result table

- **Status:** Accepted (Phase 4)
- **Date:** 2026-10-14
- **Related:** [ADR 0009](ADR-0009-liquibase-migrations.md) (Liquibase migrations), [ADR 0023](ADR-0023-corpus-ingest-and-mined-repositories.md) (commit_record idempotency), spec v2 sections 5.2 and 5.3

## Context

Kafka provides at-least-once delivery: consumer restarts, partition rebalancing, or network redeliveries will cause the same event to be processed more than once. Without an idempotent sink, redelivery creates duplicate findings, duplicate commit counts, and corrupted aggregate statistics.

## Decision

1. **Idempotency Gate on 6-Tuple:**
   Create table `worker_file_result` in Liquibase changeset `0017-create-worker-file-result.yaml` with a unique constraint on:
   `(repository_id, commit_hash, file_path, side, analyzer_version, ruleset_hash)`.
2. **Upsert Strategy:**
   - When a commit file is processed, the worker checks `worker_file_result`.
   - If present for the exact 6-tuple, the file analysis and Kafka event publication are skipped.
   - If absent, the file is analysed, the result row is recorded with finding count and status, and the event is published.
3. **Run-Level File Persistence:**
   For local runs (`repo.analysis.requested.v1`), redelivered file events overwrite previous results by deleting existing findings for that file record ID before saving new findings, preventing duplicate rows.
4. **Commit Record Idempotency:**
   Table `commit_record` enforces `uq_commit_record_repository_hash (repository_id, hash)`. Duplicate commit discovery events perform an update/ignore rather than duplicate row insertion.

## Consequences

- Full replayability: replaying Kafka events or restarting workers produces zero duplicate rows in Postgres.
- Verifiable by automated duplicate delivery tests.
