# ADR 0024 — Mined repository analysis policy: commit events only, reject on-demand runs

- **Status:** Accepted (Phase 4)
- **Date:** 2026-10-14
- **Related:** [ADR 0011](ADR-0011-analyze-commit-blobs.md) (blob analysis), [ADR 0023](ADR-0023-corpus-ingest-and-mined-repositories.md) (mined repositories), spec v2 sections 5.1, 8 and 10

## Context

Registered local repositories have a `canonical_local_path` on the operator's machine and can be analysed on-demand via `POST /api/repositories/{id}/analyses`. In contrast, mined repositories (`source_type = MINED`) have no local path owned by the backend (ADR 0023) and exist for corpus mining.

If an operator or script calls `POST /api/repositories/{id}/analyses` for a `MINED` repository, the system cannot execute a filesystem-based full walk because no working tree exists.

## Decision

1. **Analysis of mined repositories is driven exclusively by commit discovery events (`repo.commit.discovered.v1`).**
2. **`POST /api/repositories/{id}/analyses` explicitly refuses `MINED` repositories with HTTP 422 Unprocessable Entity.**
   A clear RFC 9457 problem detail explains:
   `"Repository {id} is a MINED repository and cannot be analysed on-demand; mined repositories are analysed via commit discovery events"`.
3. **`MinedRepositoryAnalysisNotSupportedException` is introduced in `backend`**, handled cleanly by `ApiExceptionHandler`.

## Consequences

- Clear separation between local single-repo analysis and streaming corpus mining.
- No null pointer errors or invalid path operations in the backend runner.
- The UI and API report clear, actionable feedback to callers.
