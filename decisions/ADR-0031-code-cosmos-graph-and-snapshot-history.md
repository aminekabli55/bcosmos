# ADR 0031 — Code Cosmos graph architecture and snapshot history comparison

- **Status:** Accepted (Phase 8)
- **Date:** 2026-10-15
- **Related:** [ADR 0012](ADR-0012-language-adapter-and-pmd.md) (LanguageAdapter abstraction in analysis-core),
  [ADR 0021](ADR-0021-rag-and-validated-suggestions.md) (RAG and validated suggestions),
  [ADR 0024](ADR-0024-mined-repo-analysis-policy.md) (Mined repository policy),
  [ADR 0028](ADR-0028-spark-gold-layer-and-analytics-api.md) (Spark gold layer and analytics API),
  spec v2 sections 3.2, 3.3, 4.1, 4.2, 8 and 9

## Context

Phase 8 introduces the visual architecture and historical delta capabilities of BCosmos:
1. **Symbol Call Graph & Dependencies:** Moving beyond flat lists of findings to understanding relationships between symbols (`CALLS`, `EXTENDS`, `IMPLEMENTS`, `USES_TYPE`).
2. **Blast Radius & Impact Analysis:** Quantifying the direct and transitive blast radius of making changes to a symbol.
3. **Architectural Hotspots:** Combining empirical quality findings with structural centrality (in-degree) into a deterministic score.
4. **Snapshot Comparison & Historical Invariants:** Tracking whether issues are new, resolved, persistent, or uncertain across commits.

Several key architectural decisions were required:
- How to extract and persist relationships safely without executing untrusted build tools or compilers.
- How to keep graph queries bounded and responsive when repositories have thousands of classes.
- How to ensure historical comparison strictly respects parsing and rule-configuration invariants (spec §4.2).
- How to guarantee visual excellence in Code Cosmos while maintaining accessibility (WCAG).

## Decision

**1. Language-agnostic relationship extraction in `analysis-core`.**
Relationship extraction is added as a first-class operation on `LanguageAdapter`:
```java
List<Relationship> extractRelationships(T file);
```
In Java, `JavaRelationshipExtractor` parses the JavaParser compilation unit AST to extract inheritance (`EXTENDS`, `IMPLEMENTS`), type usage (`USES_TYPE`), and method invocations (`CALLS`). Target symbols within the same repository run are resolved to their target `symbol_id` during persistence. External dependencies (e.g. `java.util.*`, `org.slf4j.*`) are preserved with `status: UNRESOLVED` and confidence `0.5`, giving full boundary visibility without polluting internal call chains.

**2. Bounded Graph API with BFS traversal and server limits.**
Unbounded graph endpoints risk returning megabytes of JSON and crashing browser layouts on large projects.
`GET /api/analyses/{id}/graph` enforces a configurable node limit (default 200, max 500). When truncating, the server prioritizes top hotspot symbols and performs a bounded breadth-first expansion. The response includes `truncated: boolean`, `totalNodes`, `totalEdges`, and `resolutionRate`, which the frontend displays in an informative banner.

**3. Deterministic hotspot heuristic formula.**
Hotspots are ranked using a visible, explainable formula:
$$\text{score} = (\text{findings} \times 10) + (\text{highSeverity} \times 20) + (\text{inDegree} \times 5) + (\text{lineCount} \times 0.05)$$
The exact formula components are returned by `GET /api/analyses/{id}/hotspots` and rendered directly in the UI. Central classes with many inbound callers and multiple defects rank highest, directing code review attention to the highest-leverage components.

**4. Strict transition safeguards (§4.2).**
When comparing two snapshot runs via `GET /api/analyses/compare?from={from}&to={to}`:
- A finding absent in the target snapshot is marked `RESOLVED` **only if** the target file was parsed successfully (`ParseStatus.OK`) and the rule was active.
- If the target file failed to parse (`ParseStatus.FAILED`), the resolution cannot be verified: the transition is marked `UNCERTAIN` (confidence 0.5) with the reason: *"Target file failed to parse; cannot safely confirm if finding was resolved."*
- Findings are tracked via deterministic SHA-256 fingerprints (`sha256(rule + snippet)`).

**5. Interactive Cytoscape.js canvas with accessible table dual-mode.**
The `/cosmos` screen renders a 2D interactive canvas using Cytoscape.js with force-directed (`cose`), concentric, and hierarchical layouts, styled with custom colors per symbol kind and dashed amber styling for unresolved external edges.
To ensure full compliance with WCAG accessibility guidelines, the screen includes a toggle to an **Accessible Table** view with sortable columns, keyboard navigation, and semantic HTML markup.

## Consequences

- **Safety Invariant Maintained:** Rule 4 is honored; analyzed code is never executed, and parse failures never produce false claims of resolved defects.
- **Explainability:** Hotspot scores and snapshot transitions provide human-readable explanations instead of uninterpretable scores.
- **Performant Navigation:** Server bounds and canvas fallback guarantee rapid load times and fluid interaction even on repositories with hundreds of thousands of lines of code.
