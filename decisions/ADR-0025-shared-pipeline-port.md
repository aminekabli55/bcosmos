# ADR 0025 — Shared analysis pipeline and the FileSource port in analysis-core

- **Status:** Accepted (Phase 4)
- **Date:** 2026-10-14
- **Related:** [ADR 0011](ADR-0011-analyze-commit-blobs.md) (blob analysis), [ADR 0012](ADR-0012-language-adapter.md) (LanguageAdapter), [ADR 0022](ADR-0022-event-contract-in-analysis-core.md) (analysis-core scope)

## Context

Both the backend (in Phase 1/3) and the `analysis-worker` (in Phase 4) parse and analyse Java files using PMD and JavaParser.
- In the backend, files are read from a registered local filesystem path.
- In `analysis-worker`, files are read either from local paths (when Kafka triggers a full run) or directly from Git blobs in JGit without checkout.

Duplicating the parsing, rule execution, fingerprinting, and normalization logic across modules would violate clean architecture and cause drift between local and streaming findings.

## Decision

1. **Introduce a Spring-free `CommitAnalysisPipeline` in `analysis-core/src/main/java/com/bcosmos/core/pipeline/`.**
   It takes a `LanguageAdapter` (e.g. `JavaAdapter`), normalizes findings, computes fingerprints, and returns a `CommitAnalysisResult`.
2. **Define the `FileSource` interface (Port):**
   ```java
   public interface FileSource {
       String read(String relativePath) throws IOException;
   }
   ```
3. **Implement concrete adapters:**
   - `LocalFileSource`: reads directly from a bounded directory path, enforcing path traversal checks.
   - `JGitBlobSource`: reads git objects at a specific commit hash using JGit `TreeWalk` and `ObjectLoader` without checking out files on disk.

## Consequences

- Zero code duplication for analysis and normalization between `backend` and `analysis-worker`.
- Complete isolation from filesystem assumptions: `CommitAnalysisPipeline` runs identically in unit tests, in-memory fixtures, Git blob walkers, or local directories.
- `analysis-core` remains free of Spring framework dependencies.
