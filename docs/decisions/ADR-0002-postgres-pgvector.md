# ADR 0002 — PostgreSQL + pgvector is the canonical store

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0
- **Source:** spec v2 §2.2

## Context

The product needs relational data (runs, files, findings), graph edges (symbol relationships) and vector
embeddings for retrieval. Running a separate vector database or graph database adds another failure mode to an
already heavy local stack (Kafka + Spark + Postgres).

## Decision

One PostgreSQL instance with the **pgvector** extension is the canonical store. Embeddings live in
`code_chunk.embedding` with an HNSW index; relationships are ordinary rows with a `confidence` and
`resolution_status`.

## Consequences

- One backup, one connection pool, one migration tool (ADR 0009), and joins between findings and embeddings.
- The development and test images must be pgvector images, not plain `postgres` (`infrastructure/docker-compose.yml`
  and the three `TestcontainersConfiguration` classes use `pgvector/pgvector:pg17`).
- `CREATE EXTENSION IF NOT EXISTS vector` is the first Liquibase changeset, so a missing extension fails loudly at
  startup instead of at first use.
- Vector search quality is tuned by HNSW parameters later; if recall or latency becomes a problem, a dedicated
  vector store can be revisited without changing the domain model.
