# ADR 0003 — Kafka for commit and analysis events

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (used from Phase 4)
- **Source:** spec v2 §2.2

## Context

The corpus pipeline ingests commits from many repositories and analyses them in parallel. A synchronous in-process
queue inside `backend` cannot survive restarts, cannot be replayed, and cannot be scaled by adding consumer
processes.

## Decision

Apache Kafka (single node, KRaft, `apache/kafka:4.3.1` under the `pipeline` compose profile) carries commit and
analysis events. `miner` produces, `analysis-worker` consumes in a consumer group. The single-repo flow of Phase 1
uses an in-process `JobDispatcher` behind the same interface, so swapping in the Kafka implementation in Phase 4 does
not change callers.

## Consequences

- Per-repository ordering via message key, replay for reprocessing, backpressure instead of loss.
- Idempotent sinks and a dead-letter topic are mandatory (Phase 4), because at-least-once delivery is assumed.
- Kafka is not required for Phase 1–3 work: it stays behind a profile so a laptop without Kafka can still develop.
