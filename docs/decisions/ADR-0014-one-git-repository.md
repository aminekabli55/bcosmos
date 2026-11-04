# ADR 0014 — One Git repository (`bcosmos`) inside one Bitbucket project `BCosmos`

- **Status:** **Deferred** — the intent below is not implemented; see ADR 0016 for the layout in force.
- **Date:** 2026-10-12
- **Phase:** 0
- **Source:** spec v2 §2.2

## Context

The project is one product with a shared library (`analysis-core`), one spec, one CI pipeline, and an AI agent that
needs to see all the code at once. Today the workspace holds one git repository per module
(`backend`, `frontend`, `miner`, `analysis-worker`, `analysis-core`, `ai-lab`, `spark-jobs`, `infrastructure`,
`docs`), each with its own Bitbucket remote, and `backend` already has history under `aminekabli/backend`.

## Decision

The intended end state is **one repository `bcosmos`** in the Bitbucket project `BCosmos`, containing `backend`,
`analysis-core`, `analysis-worker`, `miner`, `spark-jobs`, `ai-lab`, `frontend`, `infrastructure`, `docs` and
`sample-data`, with a root Maven multi-module build listing the four Java modules.

This is **not implemented yet**: merging the existing remotes rewrites history and is a deliberate migration, so
the spec's rule stands — do not create the root multi-module build until the layout is decided. ADR 0016 records
the layout actually in use.

## Consequences

- Until the migration, `analysis-core` is resolved from the local Maven repository: `mvn install` in `analysis-core`
  before building `backend` or `analysis-worker` (documented in the README).
- Cross-module changes are several commits in several repositories, so review and CI stay per module.
- The monorepo migration stays a planned, separate piece of work with its own plan, not a side effect of a feature
  phase.
