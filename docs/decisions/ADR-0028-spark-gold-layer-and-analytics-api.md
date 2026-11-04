# ADR 0028 — Spark gold tables, the dataset version and the analytics API

- **Status:** Accepted (Phase 5)
- **Date:** 2026-10-14
- **Related:** [ADR 0004](ADR-0004-spark-analytics.md) (Spark for corpus analytics), [ADR 0005](ADR-0005-pyspark.md) (PySpark),
  [ADR 0009](ADR-0009-liquibase-migrations.md) (Liquibase owns the schema), [ADR 0017](ADR-0017-spark-in-docker.md)
  (Spark runs in Docker), spec v2 sections 6.1, 6.2, 6.3 and 9

## Context

Phase 5 computes the corpus aggregates in Spark and has to make them readable by an HTTP API. Three questions had to
be answered before the first line of the API was written: where an aggregate lives so that a request never waits for a
cluster, what identifies a snapshot so that two runs are never mixed, and how the drill-down ("every chart opens its
records") stays honest when the records are a streaming event log rather than a bounded table.

## Decision

**1. One Spark job writes nine `agg_*` tables; the backend only reads them.**

`spark-jobs/batch/load_gold.py` is the only component that writes to Postgres from the lakehouse. It loads
`gold_run`, `agg_pipeline_stat`, `agg_rule_distribution`, `agg_quality_trend`, `agg_time_to_fix`, `agg_hotspot`,
`agg_finding`, `agg_dataset` and `agg_dataset_repository` — defined by Liquibase changeset
`0018-create-analytics-gold.yaml` and **truncated and rewritten** on every run, so the API always sees one consistent
set of rows. The direction that must never exist is the backend querying Spark: a request that needed a cluster would
be a request that fails when the cluster is down, for a number that was already computed.

**2. The backend reads those tables with plain SQL, not JPA entities.**

`AnalyticsService` uses `JdbcTemplate` with named columns. The tables are written by a Python job and shaped by
Liquibase, so an entity per table would add a second, silently divergent definition of the same schema that nothing
but a test would catch. The integration test (`AnalyticsIntegrationTest`) runs every query against the real changelog
on a container, which is the check that actually covers the drift.

Every query filters on the newest `gold_run` (`ORDER BY generated_at DESC, id DESC LIMIT 1`) and passes its
`dataset_version` down as a bind parameter. Reading without a version would keep working after a later load while
mixing two runs into one answer.

**3. A snapshot is identified by `dataset_version`, and the version is derived from the measurement.**

`v<last event date>-<sha256 of the distinct (analyzer_version, ruleset_hash) pairs in silver>`, e.g.
`v20261014-3f9a1c02`. Change the rule set or the analyzer and the version changes, exactly as `rulesetHash` identifies
a single run. The same value is written on every aggregate row, so a chart can always say which dataset it describes.

**4. Splits are by repository, and the split is a function of the repository id.**

80 % train / 10 % validation / 10 % test from `sha256(repository_id) % 100`. Two commits of one repository can never
end up on both sides of the train/test boundary (spec v2 section 6.2), and a re-run never moves a repository.

**5. The drill-down is a bounded copy, and the bound travels with the data.**

`agg_finding` holds the findings of the most recent `--drilldown-commits` (default 50) commits per repository, not the
whole event stream: Postgres must not become a second, unbounded copy of Parquet. The cap is written to
`agg_pipeline_stat` as `drilldown_commits`, the overview endpoint reports it as `drilldownCommits`, and the screen
prints it — so "drill down to records" states how deep it reaches instead of implying it covers everything.

**6. The dataset card is generated from the rows the same run wrote, never hand-edited.**

`load_gold --dataset-card docs/dataset-card.md` renders `common/card.py` from the collected `agg_dataset`,
`agg_dataset_repository` and per-rule rows. A count in the card is by construction a count of a written row, and the
known limitations (snippets not files, commits not days, event time on the trend axis, per-distinct-content
denominators, unmeasured dependency impact, licences from the operational `repository` row) are part of the renderer
rather than something a caller may forget.

**7. A metric that is not measured says so in the data, not only in the UI.**

* `line_count`/`measured_lines` count **distinct file contents** per repository, so re-analysing a revision cannot
  inflate a per-KLOC denominator.
* The hotspot score publishes every component's raw value, within-repository percentile and contribution;
  `dependency_measured = false` and `weight_sum_measured` records the divisor actually used, so the score runs from 0
  to 1 even with the unmeasured weight at 0. Complexity is labelled a **proxy** (distinct rules fired).
* Time to fix is counted in **commits**, and a finding that is already present at the root of the mined window is
  written with a null distance, counted in `introductions_unobserved`, and excluded from the published mean.

## Consequences

- The Analytics screen works with the cluster down, and a load is atomic from the API's point of view.
- A new aggregate is three coordinated edits (Liquibase changeset, `GOLD_TABLES` in `load_gold.py`, `AnalyticsService`
  SQL) and the integration test fails loudly if one is missing.
- `agg_*` rows are a *derived* copy by design: dropping and reloading them never loses information, provided the
  Parquet warehouse and Postgres agree on the `dataset_version`.
- Charts can be honest without the screen having to invent caveats: the caveats are columns.
