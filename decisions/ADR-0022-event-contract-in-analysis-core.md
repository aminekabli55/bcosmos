# ADR 0022 — The Kafka event contract lives in `analysis-core`, and one topic is added

- **Status:** Accepted (Phase 4)
- **Date:** 2026-10-14
- **Related:** [ADR 0010](ADR-0010-json-event-envelope.md) (JSON envelope with `schemaVersion`),
  [ADR 0003](ADR-0003-kafka-events.md) (Kafka), [ADR 0012](ADR-0012-language-adapter.md) (`analysis-core` is the
  only shared code), spec v2 sections 5.1–5.3

## Context

Three modules exchange the same messages: `backend` produces analysis and mine requests, `miner` produces commit
events, `analysis-worker` consumes both and produces findings. A topic name or a payload field spelled differently
in one of them is a runtime failure that no compiler catches. `analysis-core` is already the only shared code
between the Spring modules (ADR 0012), and it is plain Java with no Spring and no Jackson.

The spec's topic table (section 5.1) also has no topic for "start mining a repository", but
`POST /api/corpus/ingest` has to reach the `miner`, and the `miner` deliberately holds no database, so it cannot
read a queued row either.

## Decision

1. **The event contract is a package in `analysis-core`.** `com.bcosmos.core.events` holds `EventType`,
   `KafkaTopics`, `EventEnvelope` and one record per payload. The types are plain records — no Spring, no Jackson,
   no Kafka — so the three modules share the *shape* and each one brings its own serializer. A change to a payload is
   then a compile error in every module that produces or consumes it, instead of a message that fails at runtime.
2. **Payload records validate what they can.** An envelope without an event id, a commit event without a changed
   Java file, an absolute path in a findings payload, or an `https`-less remote URL is refused at construction, so a
   producer cannot easily emit a message no consumer can act on. This is not schema validation — that is the
   consumer's job — but it removes the cheapest mistakes.
3. **`schemaVersion` is checked, not assumed.** `EventEnvelope.isUnderstood()` compares the message's version with the
   version this build knows, so a consumer can park or dead-letter a shape it cannot read instead of mis-parsing it.
4. **A new topic, `repo.mine.requested.v1`.** `POST /api/corpus/ingest` publishes one `MINE_REQUESTED` event per URL;
   the `miner` consumes it and produces `repo.commit.discovered.v1` as the spec already describes. This is an
   addition to the spec's topic table and is recorded here rather than invented quietly in code.
5. **`repositoryId` is part of the envelope, not of each payload.** It is the partition key of every topic (spec
   section 5.1), so keeping it in one place means a producer cannot key a topic on the wrong field.

## Consequences

- A payload change is a three-module compile error, which is the point; the cost is that all three modules must be
  rebuilt (and `analysis-core` installed) before the pipeline runs.
- Changing a payload *incompatibly* means bumping `EventEnvelope.SCHEMA_VERSION` and teaching consumers to accept
  both shapes, or draining the topics first. Nothing enforces that yet beyond the version check.
- `analysis-core` now carries two unrelated concerns: language analysis (JavaParser, PMD) and the pipeline's event
  contract. If a third concern appears, the contract package is a candidate for its own tiny module.
