# ADR 0026 — Kafka retry semantics and Dead Letter Queue (DLQ) routing

- **Status:** Accepted (Phase 4)
- **Date:** 2026-10-14
- **Related:** [ADR 0003](ADR-0003-kafka-events.md) (Kafka events), [ADR 0010](ADR-0010-json-event-envelope.md) (JSON envelope), spec v2 sections 5.1 and 5.3

## Context

Streaming systems must balance fault resilience with poison message handling:
- Transient errors (network blips, lock contention) should be retried with backoff.
- Fatal poison messages (corrupted JSON, unhandled schema versions, unallowed remote hosts) must not be endlessly retried, which would block consumers and flood logs.

## Decision

1. **Dead Letter Queue Naming:**
   Every working topic `<topic>.v1` has a matching `<topic>.dlq.v1` declared in `KafkaTopics.deadLetterOf(topic)` and created at startup via `KafkaTopicsConfiguration`.
2. **Retryable Errors:**
   Transient runtime failures use bounded exponential backoff:
   - Initial interval: 500ms
   - Multiplier: 2.0
   - Maximum retry attempts: 3
   If attempts are exhausted, the message is routed to the corresponding DLQ with error headers.
3. **Poison Messages (Direct to DLQ):**
   Non-retryable exceptions (e.g. `IllegalArgumentException` thrown when `!envelope.isUnderstood()`, unparseable JSON, unallowed remote host) bypass retries and are sent directly to the DLQ immediately via `DeadLetterPublishingRecoverer`.

## Consequences

- No poison message can cause a consumer partition to halt or spin endlessly.
- Operators can inspect DLQ topics for unexpected schema drifts or security violations.
- At-least-once delivery semantics are preserved without stalling the pipeline.
