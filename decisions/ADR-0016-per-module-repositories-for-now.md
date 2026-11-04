# ADR 0016 — Keep one Git repository per module for now; no root Maven multi-module build

- **Status:** Accepted (records the layout in force; refines ADR 0014)
- **Date:** 2026-10-12
- **Phase:** 0
- **Supersedes:** the "implemented" reading of ADR 0014; ADR 0014 stays as the intended end state

## Context

ADR 0014 wants one repository `bcosmos`, but the workspace already has eight repositories with their own Bitbucket
remotes and real history (`backend` lives under `aminekabli/backend`). Merging them rewrites remotes and history, so
it is a destructive migration that deserves its own plan — while Phase 0 needs a build that actually runs.

## Decision

For now: **one Git repository per module**, no root `pom.xml`, and no Maven reactor. `analysis-core` is consumed as
`com.bcosmos:analysis-core:0.0.1-SNAPSHOT` from the local Maven repository, which means `mvn install` in
`analysis-core` must be run before building `backend` or `analysis-worker` (the README states this as a prerequisite
step).

The monorepo migration (ADR 0014) becomes a separate task: create the `bcosmos` repository, import the module
histories, add the root `pom.xml` with modules `backend`, `analysis-core`, `analysis-worker`, `miner`, then retire
the old remotes.

## Consequences

- Builds work today with the Java modules where they are and history intact.
- `mvn verify` is run per module; there is no single root command yet, and a fresh clone needs the extra
  `analysis-core` install step.
- CI (when it exists) is per repository; the migration task will consolidate it.
- `README.md` and `AGENTS.md` live at the workspace root and are **not version-controlled** until the monorepo
  exists — this is a known gap of this ADR, not an oversight.
