# ADR 0029 — Two Kafka listeners: one for host clients, one for containers

- **Status:** Accepted (Phase 5)
- **Date:** 2026-10-14
- **Related:** [ADR 0003](ADR-0003-kafka-events.md) (Kafka for commit/analysis events), [ADR 0017](ADR-0017-spark-in-docker.md)
  (Spark runs in Docker), `infrastructure/docker-compose.yml`

## Context

Phase 5 puts the broker between two kinds of client for the first time:

- the Spring applications running on the developer machine (`backend`, `analysis-worker`, `miner`) connect to
  `localhost:9092`;
- `spark-submit` runs in a container and has to consume `analysis.findings.v1`, so it resolves the broker as
  `kafka:9092`.

The `apache/kafka` image defaults to a single PLAINTEXT listener advertising `localhost:9092`. A container that
connects to `kafka:9092` is handed `localhost:9092` as the address of the partition leader and then fails to reach it
(it resolves to itself). Phase 4 never hit this because only host processes talked to the broker.

A second constraint: passing **any** `KAFKA_*` environment variable disables the image's built-in KRaft defaults (the
container then fails with *Missing required configuration "process.roles"*), so the configuration has to be complete
rather than half-inherited.

## Decision

The compose `kafka` service declares two listeners explicitly:

| Listener | Advertised as | Used by |
|---|---|---|
| `INTERNAL://:9092` | `kafka:9092` | containers on the compose network (Spark) |
| `EXTERNAL://:9094` | `localhost:9092` | the Spring apps on the host |
| `CONTROLLER://:9093` | — | KRaft quorum (single node) |

`9092` is still the port a developer uses, so every documented command and every default
(`BCOSMOS_KAFKA_BOOTSTRAP=localhost:9092`) is unchanged; the published mapping is `9092:9094` and the rest of the
KRaft settings (`KAFKA_PROCESS_ROLES`, `KAFKA_CONTROLLER_QUORUM_VOTERS`, replication factors) are spelled out.

## Consequences

- The streaming job (`findings_to_bronze`) can run where Spark can actually run (Docker, ADR 0017) and still reach the
  broker, which is what makes the Phase 5 pipeline runnable end to end without a broker on the host.
- The broker is now configured in one place with a reason next to it instead of inheriting image defaults that differ
  between the vendor image and its documentation.
- Any future container client (a second worker, a Spark streaming deployment) uses `kafka:9092` and needs no change.
