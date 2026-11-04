# ADR 0001 — `backend` is a modular monolith; `analysis-worker` and `miner` are separate processes

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0
- **Source:** spec v2 §2.2

## Context

The product has three very different workloads: request/response REST traffic (register repositories, browse
findings), CPU-bound analysis of many files, and long-running Git crawling of a corpus. Splitting everything into
microservices would multiply local infrastructure for no benefit on a single-machine project.

## Decision

`backend` stays a **modular monolith** with clear package boundaries (`web`, `repository`, `jobs`, `graph`,
`history`, `review`, `analytics`, `ai`, `persistence`, `config`) and no cross-package shortcuts. Only the parts with a
real independent scaling need are separate deployables: `analysis-worker` (Kafka consumer group, scales with
partition count) and `miner` (I/O-bound crawler).

## Consequences

- One process to run, debug and test for the single-repo flow; the Reactor-style overhead is avoided.
- Module boundaries are conventions, so they must be enforced by tests (ArchUnit is already a test dependency) and
  reviewed per phase.
- `analysis-core` is the only shared code between `backend` and `analysis-worker`; sharing anything else is a smell.
