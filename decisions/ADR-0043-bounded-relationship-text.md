# ADR 0043 — Relationship text is bounded at the extractor and at the entity, not pushed into a search engine

- **Status:** Accepted (post-Phase 11 correctness fix)
- **Date:** 2026-10-19
- **Related:** [ADR 0031](ADR-0031-code-cosmos-graph-and-snapshot-history.md) (the graph these edges feed),
  [ADR 0002](ADR-0002-postgres-pgvector.md) (Postgres is the store), [ADR 0033](ADR-0033-typescript-adapter.md)
  (a second adapter writing the same edge model), spec v2 §4.1

## Context

Run 54 of a real 458-file Java repository ended `FAILED` with

```
ANALYSIS_ERROR: could not execute statement [... value too long for type character varying(500)]
[insert into relationship (... target_reference_name ...)]
```

The column is `relationship.target_reference_name varchar(500)` (changeset 0021). The value came from
`JavaRelationshipExtractor`, which built every `CALLS` edge as

```java
call.getScope().map(s -> s.toString() + "." + call.getNameAsString()).orElse(call.getNameAsString())
```

so the *printed receiver expression* became the "reference name". A fluent chain therefore stored the whole chain:
the longest in that repository was 824 characters
(`http.cors(...).csrf(...).sessionManagement(...)....` in `SecurityConfig.java`), and a second candidate reached
811 (`...stream().map(field -> { ... })`). The `varchar(500)` limit was simply the first place that absurd value
became fatal: the insert aborted the file's transaction, and with it the run.

Nothing in the failure is about storage capacity or query shape. Postgres stored the same graph for smaller
repositories without complaint; Elasticsearch or Redis would have accepted the 824-character string and left the
model — a column documented as a *name* holding a source expression — broken.

## Decision

1. **The extractor records a name, not an expression.** `CALLS` edges are `methodName` or
   `receiver.methodName`, and the receiver is included only when it has a name of its own: a simple name
   (`repository`), `this`, `super`, or a field path (`System.out`). A receiver that is itself an expression — a
   fluent chain, a lambda, a cast, a parenthesised expression — contributes nothing, so the edge is stored as
   `csrf`, `permitAll`, `save`. The same helper serves method bodies and constructors.
2. **The entity is the last guard.** `RelationshipEntity` bounds both text columns to the declared 500 characters
   in its constructor: `targetReferenceName` keeps its **suffix** (the method name is the only part cross-file
   resolution reads), `evidenceFile` keeps its **prefix** (a path stays meaningful from the start). A future
   adapter that writes an unexpectedly long value degrades to a shorter row instead of failing a run.
3. **No new store and no schema change.** The columns keep their declared width, the graph queries and BFS stay in
   Postgres, and there is no migration. `relationship` remains a relational table because every reader —
   `GraphService`, hotspots, history, the Code Cosmos screen — traverses it by `run_id`, `source_symbol_id` and
   `target_symbol_id`, not by free text.

## Alternatives considered

- **Widen the column to `text`.** Removes this failure only; the stored value stays a meaningless 824-character
  expression, and any adapter can keep growing it. It also weakens a deliberate bound without fixing the writer.
- **Truncate only at persistence.** Would have kept the giant values in the graph and made resolution depend on
  which 500 characters survived. The tail-preserving rule fixes resolution, but the name is still wrong.
- **Elasticsearch / Redis for edges.** A second datastore to keep consistent with Postgres, for data that is small,
  strongly relational and traversed by foreign key. It addresses neither the cause nor the symptom.

## Consequences

- Reference names in the graph become short and human-readable (`repository.save` instead of a printed builder
  chain). Simple-name resolution is unchanged because `GraphService` already matches on the last dot segment.
- No data migration: existing rows keep their long values, and the graph still works; only new runs produce the
  canonical form.
- The bound lives in one place per layer (extractor + entity) and is covered by tests on both sides.
- **Validation:** `cd analysis-core && ./mvnw test` is green (78 tests, including the fluent-chain case); the same
  repository that produced 824-character references yields a maximum of 89 after the change (458 files parsed);
  the entity bound was exercised with a 1412-character reference and a 1011-character path, storing 500 characters
  with the `.save` suffix and the `src/deep/` prefix preserved. `cd backend && ./mvnw test` could not run in this
  checkout for an unrelated reason (the AI test sources do not compile against the current AI main sources — 68
  errors in six `ai/**` test files), so the backend entity test was not executed by surefire here.
