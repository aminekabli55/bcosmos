# ADR 0009 — Liquibase for migrations, owned by `backend` only

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0
- **Source:** spec v2 §2.2

## Context

Several processes touch the same database (`backend`, `analysis-worker`, later the Spark `agg_*` loader). Schema
changes must be reviewable, ordered and repeatable, and must not depend on Hibernate guessing the schema.

## Decision

Liquibase, with a master changelog at `backend/src/main/resources/db/changelog/db.changelog-master.yaml` that
includes numbered changesets under `changes/`. Only `backend` runs migrations; `analysis-worker` sets
`spring.liquibase.enabled: false`, and JPA runs with `ddl-auto: validate` everywhere so a schema drift fails fast.

## Consequences

- One place to read the schema history; every behaviour change that touches tables adds a changeset.
- Rollback paths are written with each changeset (the first one: `DROP EXTENSION IF EXISTS vector`).
- Tests apply the real changelog against a pgvector container, so a migration that cannot run is caught in
  `mvn verify`, not in someone's local database.
