# ADR 0008 — Cytoscape.js (2D) for the code graph instead of Three.js

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (used from Phase 8)
- **Source:** spec v2 §2.2

## Context

A dependency/call graph has to be explorable in the browser. A 3D renderer looks impressive in a demo but brings
camera, occlusion and hit-testing problems, and does not help comprehension of a graph with thousands of nodes.

## Decision

Cytoscape.js renders a **bounded 2D** graph (a selected node plus a limited neighbourhood, or a filtered view).
Unresolved edges are shown as such instead of being hidden. `cytoscape` and `@types/cytoscape` are installed in the
Angular app from Phase 0.

## Consequences

- Layout, filtering and interaction stay conventional and testable; no WebGL requirement.
- Large graphs are handled by bounding and clustering the view, not by rendering everything.
- Node/edge counts and resolution coverage are surfaced in the UI so "missing edges" are never mistaken for
  "no dependencies".
