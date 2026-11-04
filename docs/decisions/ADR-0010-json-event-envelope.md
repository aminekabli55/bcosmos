# ADR 0010 — JSON events with an explicit envelope and `schemaVersion`

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (used from Phase 4)
- **Source:** spec v2 §2.2

## Context

Kafka messages must stay readable and debuggable on a single-machine project, but they also get persisted to
Parquet (bronze) and replayed long after the producer changed.

## Decision

Every event is JSON with an explicit envelope carrying at least: event type and version (for example
`repo.commit.discovered.v1`), `schemaVersion`, a unique event id, the producing component, a timestamp, the
repository/run identifiers, and the payload. Consumers validate the envelope and route unknown versions to the
dead-letter topic instead of guessing.

## Consequences

- Messages can be inspected with any JSON tool and replayed from bronze Parquet; events are self-describing.
- No schema registry to run locally; Avro + Schema Registry remains a drop-in upgrade if the project ever needs it.
- Payloads are additive-only within a version, and a breaking change means a new `.v2` type.
