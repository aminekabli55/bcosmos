# ADR 0042 — One cosmic design system, owned by the shared stylesheet

- **Status:** Accepted (UI pass after Phase 11)
- **Date:** 2026-10-17
- **Related:** [ADR 0028](ADR-0028-spark-gold-layer-and-analytics-api.md) (the analytics screens this restyles),
  [ADR 0031](ADR-0031-code-cosmos-graph-and-snapshot-history.md) (the graph screen), spec v2 §7 (dashboard)

## Context

The product worked but the interface did not read as one product. Walking the running app showed three concrete
causes, not a matter of taste:

1. **Two token vocabularies.** `styles.scss` defined `--surface`, `--text`, `--muted`, `--border`; the Benchmarks
   screen (Phase 6) and the Commit Review screen (Phase 9) were written against `--surface-0/1`, `--text-primary`,
   `--text-muted`, `--bg-surface` and `--bg-card`, which were never defined. A missing custom property makes the
   declaration invalid at computed-value time, so those screens silently lost their panel backgrounds, heading
   colours and borders — they looked "unstyled" because parts of them were.
2. **A top bar with nine destinations.** Every screen was reachable from one sticky pill row, which was cramped at
   laptop width and became an icon strip under 640 px. There was no hierarchy between observing, analysing and
   configuring.
3. **Motion was incidental.** A page fade and one pulsing status dot were the whole animation vocabulary; cards,
   metrics, tables and navigation had no entrance, hover or focus feedback. The only "cosmos" cue was a handful of
   static star dots, and several screens still carried light-theme leftovers (white pill backgrounds, dark-green
   diff text on a dark surface).

## Decision

1. **One token layer, with the legacy names aliased.** `frontend/src/styles.scss` is the single source of truth for
   surfaces, text, severity, shape, depth, type and easing. It also defines the previously missing names
   (`--surface-0/1`, `--text-primary`, `--text-muted`, `--bg-surface`, `--bg-card`, `--border-h/s/l`, `--accent-h`)
   as aliases, so screens written against either vocabulary resolve instead of silently dropping declarations. No
   screen keeps its own palette.
2. **The cosmos is the shell, not a background image.** `App` owns a fixed, `aria-hidden` backdrop of two tiled star
   fields with different drift speeds, three blurred nebulae, a coordinate grid and a moon; the Routed content
   floats above it on glass surfaces. The shell is a 264 px left rail (grouped navigation, orbital brand mark, a
   "read-only by design" moon phase) that collapses under 1080 px into a sticky icon bar. Repositories adds a
   purpose-built hero with a ringed planet, two orbiting satellites and live repository counts.
3. **Motion is a small, named vocabulary.** `rise-in` (cards, page sections), `aurora-sweep` (card hairline),
   `title-flow` (gradient headings), `orbit-spin` (brand rings, hero orbits), `planet-float`, `nebula-drift`,
   `star-drift`, `twinkle` and a button light sweep. Entrances are short (0.35–0.7 s) and easing is explicit
   (`--ease-out`, `--ease-spring`); the router fades each screen in. Every animation is disabled under
   `prefers-reduced-motion: reduce`, and no motion carries meaning that the static state does not show.
4. **Type is split by job.** Space Grotesk for display and numerals, Inter for prose and controls, JetBrains Mono
   for code, identifiers and fingerprints, loaded from Google Fonts with `display=swap`.
5. **No new client dependency and no JavaScript for decoration.** Stars, nebulae, the moon, planets, rings and
   satellites are CSS gradients, borders and transforms. Charts keep Chart.js and the graph keeps Cytoscape; the
   design system adds no bundle of its own beyond the shared stylesheet.
6. **Component styles stay scoped, except for a bug fix.** The one cross-cutting change outside `styles.scss` is
   removing `@use '../app.scss'` from `commit-review.scss`: it had been copying the entire shell stylesheet into
   that component's CSS (and, after the shell grew, over the 16 kB component-style budget). It referenced nothing
   from the module.

## Consequences

- The app has one look on every route, and a screen that forgets a token now fails visibly in review rather than
  rendering a transparent panel.
- The shell costs about 15 kB of CSS (3.98 kB transferred) and no image assets; the initial bundle stays at 88 kB
  transferred, well inside the 500 kB budget, and the production build is warning-free.
- The sidebar is the navigation contract: `app.spec.ts` asserts the link labels and now their workflow order
  (Observe → Analyse → Configure).
- Dense data screens keep their own layout rules (Cytoscape canvas, commit-review three-column workspace, analytics
  drill-down tables); only their palette, type and surface treatment come from the system.
- Hover and entrance motion is decorative. Reduced-motion users get the same hierarchy, and keyboard focus remains
  visible on links, buttons, table rows and form controls.
- **Not verified visually in this environment:** the browser panel would not composite (screenshots failed), so the
  pass was checked with computed layout and style assertions at 1440 px and 390 px (sidebar width, hero grid,
  token resolution, overflow, font and animation names) plus the unit and production builds, not with a pixel
  comparison.
