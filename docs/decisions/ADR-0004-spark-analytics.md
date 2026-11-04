# ADR 0004 — Spark for corpus analytics and dataset building

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (used from Phase 5)
- **Source:** spec v2 §2.2

## Context

Corpus analytics are many repositories × many commits × many findings, plus a fix-triple dataset built by joining
findings across commits. SQL against the serving database would work for a demo but would not exercise the
distributed tooling the project is meant to demonstrate, and would block the database while doing it.

## Decision

Spark Structured Streaming writes raw events to a **bronze** Parquet layer; Spark batch jobs clean them into
**silver** and produce **gold** aggregates (`agg_*` tables) plus the `fix_triples` dataset that feeds evaluation
and fine-tuning. All Spark work lives in `spark-jobs/` and runs in local mode.

## Consequences

- The medallion layers are versioned Parquet files on local disk; Spark is an analytics engine here, not the
  serving store.
- Spark batch jobs are the only writer of `agg_*` tables, so their fixtures (hand-computed) define correctness.
- On Windows, Spark needs Java on the PATH and is best run in Docker/WSL2 to avoid Hadoop `winutils` problems.
