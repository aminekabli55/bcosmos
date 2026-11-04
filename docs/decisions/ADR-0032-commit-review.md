# ADR 0032 — Three-layer evidence-based commit review architecture

- **Status:** Accepted (Phase 9)
- **Date:** 2026-10-15
- **Related:** [ADR 0011](ADR-0011-analyze-commit-blobs.md) (Analyze commit blobs),
  [ADR 0019](ADR-0019-ai-gateway.md) (AI Gateway),
  [ADR 0020](ADR-0020-stored-ai-switch.md) (Stored AI switch),
  [ADR 0031](ADR-0031-code-cosmos-graph-and-snapshot-history.md) (Graph architecture and snapshot history),
  spec v2 sections 7.6, 8 and 9

## Context

Phase 9 implements the **Commit Review** capability of BCosmos (spec §7.6) designed for developers, code reviewers, and scrum masters.
Rather than providing unstructured, hallucinatory chatbot commentary on a git diff, BCosmos requires an **evidence-first review in three distinct layers**:

1. **Layer 1: Facts (no AI).** Empirical counts of files changed, finding transitions (`NEW`, `RESOLVED`, `PERSISTENT`, `UNCERTAIN`), affected dependents calculated from the code graph, and unresolved edges.
2. **Layer 2: Risk signals (explainable rules).** Explicitly labeled heuristic warnings (e.g., changes touching an architectural hotspot, quality regressions introduced, main production code modified with zero tests modified).
3. **Layer 3: AI summary (optional, selectable model).** Structured executive summary, "what to check" checklist, and "tests to run". Every claim must cite files and lines, and cited references must be verified to exist in the actual diff.

If AI is disabled or an LLM call fails, Layers 1 and 2 must continue to function completely.

## Decision

**1. Layered architecture in `CommitReviewService`.**
`CommitReviewService` computes the review in strict stages:
- **Git diff computation:** via JGit (`GitCommitService`), extracting modified file paths, classification into main (`src/main/`) vs test (`src/test/`), and unified diff text bounded at 25,000 characters. If no local repository directory exists, it falls back to mined records from `commit_record`.
- **Finding delta integration:** queries `finding_transition` records for snapshot runs matching the commit hashes.
- **Hotspot & blast radius inspection:** queries `GraphService.getHotspots()` to detect if any changed file corresponds to a high-scoring architectural hotspot ($\text{score} \ge 30$).
- **Risk signal evaluation:** applies transparent rules:
  - `HOTSPOT_MODIFIED` (`HIGH` severity)
  - `NEW_FINDINGS_INTRODUCED` (`CRITICAL` severity)
  - `MAIN_CHANGED_NO_TESTS` (`MEDIUM` severity, explicitly tagged `heuristic: true` per spec §7.6)
  - `HIGH_BLAST_RADIUS` (`MEDIUM` severity)
- **Optional AI summary with strict citation verification:** when AI is enabled and a model is requested, loads versioned prompt template `commit-review.v1.json` / `commit-review.v1.md`, sends a `StructuredRequest<AiReviewOutput>`, and strictly filters `whatToCheck` items to discard any hallucinated file references not present in the diff.

**2. Storage in `commit_review` table.**
Liquibase changeset `0023-create-commit-review.yaml` creates the `commit_review` table with foreign keys to `repository`, commit hashes, denormalized delta counters, JSONB columns for `facts_json`, `risk_signals_json`, `what_to_check_json`, `tests_to_run_json`, and optional `ai_summary` and `ai_model_name`.

**3. REST Endpoints (spec section 8).**
- `GET /api/repositories/{id}/commits?limit=30` — lists recent commits with author, date, and `reviewId` if a review already exists.
- `POST /api/repositories/{id}/commit-reviews` — accepts `{fromCommit, toCommit, modelId}` and returns 201 Created with the full three-layer review.
- `GET /api/commit-reviews/{id}` — retrieves an existing review by ID.

**4. Angular Commit Review screen (`/commit-reviews`).**
A dedicated standalone component (`CommitReview`) offering:
- Repository and commit pickers (allowing selection of base and head commits, or automatic parent commit detection).
- Model selector allowing `None (facts + signals only)` or any enabled chat model from `GET /api/models`.
- Facts summary card with metric pills for files changed, regressions, resolved findings, and affected dependents.
- Risk signal cards with severity color-coding (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`) and distinct heuristic badges.
- AI review panel with concise overview, verified "What to check" items with line numbers, and "Recommended tests".

## Consequences

- **Verifiability:** Reviewers are never shown hallucinations as facts; all risk signals are derived from AST and graph analysis, and AI suggestions are filtered against real diff contents.
- **Resilience:** Full utility is preserved when running offline or with AI disabled.
- **Traceability:** Reviews are permanently persisted and linked to commit history, enabling auditing and trend tracking over time.
