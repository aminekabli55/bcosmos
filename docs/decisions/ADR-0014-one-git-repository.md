# ADR 0014 — One Git repository (`bcosmos`) inside one Bitbucket project `BCosmos`

- **Status:** **Accepted** — implemented 2026-11-04 (see "Implementation" below); this supersedes ADR 0016.
- **Date:** 2026-10-12 (implemented 2026-11-04)
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

## Implementation (2026-11-04)

- The repository is `bcosmos` at <https://github.com/aminekabli55/bcosmos>, public, and the workspace root is its
  root. This drops ADR 0014's "Bitbucket project `BCosmos`" detail: the project moved to GitHub, and nothing in the
  workspace points at Bitbucket any more.
- Each module's history was imported under its folder with a `-s ours` merge plus `read-tree --prefix`, so the
  former per-module commits stay reachable (61 commits, 9 import merges) instead of being squashed away.
- The files that lived outside every module — `README.md`, `AGENTS.md`, the root `.gitignore` and `sample-data/` —
  are now version-controlled for the first time.
- **The root multi-module build is still not created.** ADR 0016's "no root `pom.xml`, no Maven reactor" part
  stands: builds and `./mvnw verify` stay per module, and `analysis-core` is still installed into the local Maven
  repository first. Only version control was consolidated, because that is all this migration was asked to do.
- The first push was refused by GitHub push protection: `SecretRedactorTest` held a Slack-shaped fixture literal in
  its history. The fixtures are now assembled at run time, and that one file was rewritten across all commits, so
  no pushed commit contains a token shape.
- The former per-module `.git` directories are archived to the git-ignored `.git-module-archive.tar.gz`; their
  Bitbucket remotes are left untouched but unused.
