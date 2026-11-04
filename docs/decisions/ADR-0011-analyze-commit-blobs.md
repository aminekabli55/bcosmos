# ADR 0011 — Analyse files by commit blob (JGit), not by checkout

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (implemented in Phase 4)
- **Source:** spec v2 §2.2

## Context

Corpus ingestion reads repositories from elsewhere on the internet, so their content is untrusted input. Checking
out a working tree would place thousands of files on disk, expose hooks and build files, and make it easy to
accidentally execute something.

## Decision

`miner` and the analysis path read file contents **directly from Git objects** with JGit
(`7.8.0.202609011348-r`) at a specific commit. No checkout, no submodules, no hooks, nothing executed from the
analysed repository. Local registration (Phase 1) reads files through the same core, guarded by `SafePathValidator`
(configured roots, canonical paths, no symlink escape).

## Consequences

- Analysing N commits does not multiply disk usage, and hostile repositories cannot affect the machine.
- Per-commit analysis is reproducible: the same blob hash gives the same parse input.
- Anything requiring a build or resolved classpath (full JavaParser symbol resolution) stays out of scope and is
  reported as reduced coverage instead of being faked.
