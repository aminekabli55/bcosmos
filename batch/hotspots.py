"""Silver → ranked files per repository with visible component scores (spec v2 section 6.3).

```
risk = w1·norm(complexity) + w2·norm(churn) + w3·norm(dependency_impact) + w4·norm(open_findings)
```

Normalization is percentile rank within the repository, so a file is risky *relative to its own repository* and a
large repository does not simply outrank a small one. Every component's raw value, rank and contribution is written
next to the score, because the spec requires the contributions to be always visible and the screen calls the result a
triage heuristic rather than a measurement.

Two components are not what the formula names, and both say so in the output:

* **complexity** is a proxy: the number of distinct rules the file triggered. The event stream carries findings, not
  parser metrics, so there is no cyclomatic complexity to normalize. The column is `complexity_proxy` and the UI
  labels it as a proxy.
* **dependency impact** is **not measured** until the relationship table of Phase 8 exists. Its weight is 0,
  `dependency_measured` is false, and the score is divided by the sum of the *measured* weights so it still runs from
  0 to 1 — the divisor travels with the row (`weight_sum_measured`) instead of being an invisible constant.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pyspark.sql import DataFrame, Window, functions as F  # noqa: E402

from common import paths, triples  # noqa: E402
from common.sessions import build_session  # noqa: E402

#: Weights of the four components; configurable per run and stored with the output.
DEFAULT_WEIGHTS: dict[str, float] = {
    "complexity": 0.25,
    "churn": 0.35,
    "dependency": 0.0,
    "open_findings": 0.40,
}

OUTPUT_COLUMNS: tuple[str, ...] = (
    "repository_id",
    "file_path",
    "score",
    "rank_in_repository",
    "churn",
    "churn_score",
    "churn_contribution",
    "complexity_proxy",
    "complexity_score",
    "complexity_contribution",
    "open_findings",
    "open_findings_score",
    "open_findings_contribution",
    "dependency_measured",
    "weight_sum_measured",
    "latest_commit_hash",
    "weights",
)


def hotspots(silver: DataFrame, depths: DataFrame | None = None, weights: dict[str, float] | None = None) -> DataFrame:
    """One row per (repository, file), scored and ranked."""

    resolved_weights = dict(DEFAULT_WEIGHTS if weights is None else weights)
    resolved_depths = depths if depths is not None else triples.first_parent_depth(triples.first_parent_edges(silver))

    after = silver.filter(F.col("side") == "AFTER")
    activity = (
        after.groupBy("repository_id", "file_path")
        .agg(
            F.countDistinct("commit_hash").alias("churn"),
            F.countDistinct(F.when(F.col("has_finding"), F.col("rule_id"))).alias("complexity_proxy"),
            F.countDistinct(F.when(F.col("has_finding"), F.col("fingerprint"))).alias("open_findings"),
        )
    )

    # The findings of the most recently analysed revision of each file: the deepest commit the corpus observed for
    # it. `open_findings` above counts what was seen anywhere in the window, which is a different (and also useful)
    # number; the two are distinct columns and the UI shows the latest revision.
    latest_revision = _latest_revision_findings(after, resolved_depths)

    scored = (
        activity.join(latest_revision, on=["repository_id", "file_path"], how="left")
        .withColumn("open_findings", F.coalesce(F.col("latest_open_findings"), F.lit(0)))
        .drop("latest_open_findings")
        .withColumn(
            "churn_score",
            F.percent_rank().over(Window.partitionBy("repository_id").orderBy("churn")),
        )
        .withColumn(
            "complexity_score",
            F.percent_rank().over(Window.partitionBy("repository_id").orderBy("complexity_proxy")),
        )
        .withColumn(
            "open_findings_score",
            F.percent_rank().over(Window.partitionBy("repository_id").orderBy("open_findings")),
        )
    )

    weight_sum_measured = resolved_weights["complexity"] + resolved_weights["churn"] + resolved_weights["open_findings"]

    contribution = lambda column, weight: (F.col(column) * F.lit(weight))  # noqa: E731 - a one-line local helper
    measured = (
        scored.withColumn("churn_contribution", contribution("churn_score", resolved_weights["churn"]))
        .withColumn("complexity_contribution", contribution("complexity_score", resolved_weights["complexity"]))
        .withColumn(
            "open_findings_contribution",
            contribution("open_findings_score", resolved_weights["open_findings"]),
        )
    )

    return (
        measured.withColumn(
            "score",
            F.round(
                (F.col("churn_contribution") + F.col("complexity_contribution") + F.col("open_findings_contribution"))
                / F.lit(weight_sum_measured),
                6,
            ),
        )
        .withColumn(
            "rank_in_repository",
            F.row_number().over(
                Window.partitionBy("repository_id").orderBy(F.col("score").desc(), F.col("file_path").asc())
            ),
        )
        .withColumn("dependency_measured", F.lit(resolved_weights["dependency"] > 0))
        .withColumn("weight_sum_measured", F.lit(round(weight_sum_measured, 6)))
        .withColumn("weights", F.lit(_weights_as_text(resolved_weights)))
        .select(*OUTPUT_COLUMNS)
    )


def _latest_revision_findings(after: DataFrame, depths: DataFrame) -> DataFrame:
    """`(repository_id, file_path, latest_commit_hash, latest_open_findings)`."""

    with_depth = after.join(
        triples.depth_lookup(depths),
        on=(F.col("repository_id") == F.col("depth_repository_id"))
        & (F.col("commit_hash") == F.col("depth_commit_hash")),
        how="left",
    )

    latest = (
        with_depth.groupBy("repository_id", "file_path")
        .agg(
            F.max("resolved_depth").alias("latest_depth"),
            F.max(F.col("occurred_at")).alias("latest_occurred_at"),
        )
    )

    # The latest revision is the deepest commit observed for the file. A file whose parent chain is unknown (a single
    # commit corpus with no parent events) falls back to its newest event time, and `latest_depth` is null only when
    # no depth could be resolved at all.
    is_latest = (F.col("resolved_depth") == F.col("latest_depth")) | (
        F.col("latest_depth").isNull() & (F.col("occurred_at") == F.col("latest_occurred_at"))
    )
    return (
        with_depth.join(latest, on=["repository_id", "file_path"], how="inner")
        .filter(is_latest)
        .groupBy("repository_id", "file_path")
        .agg(
            F.max("commit_hash").alias("latest_commit_hash"),
            F.countDistinct(F.when(F.col("has_finding"), F.col("fingerprint"))).alias("latest_open_findings"),
        )
    )


def _weights_as_text(weights: dict[str, float]) -> str:
    return ",".join(f"{key}={weights[key]}" for key in sorted(weights))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Rank files per repository by a four-component triage heuristic.")
    parser.add_argument("--warehouse", default=None)
    parser.add_argument("--master", default=None)
    parser.add_argument("--weight-complexity", type=float, default=DEFAULT_WEIGHTS["complexity"])
    parser.add_argument("--weight-churn", type=float, default=DEFAULT_WEIGHTS["churn"])
    parser.add_argument("--weight-dependency", type=float, default=DEFAULT_WEIGHTS["dependency"])
    parser.add_argument("--weight-open-findings", type=float, default=DEFAULT_WEIGHTS["open_findings"])
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-hotspots", master=args.master)

    weights = {
        "complexity": args.weight_complexity,
        "churn": args.weight_churn,
        "dependency": args.weight_dependency,
        "open_findings": args.weight_open_findings,
    }
    silver = spark.read.parquet(paths.silver_path(args.warehouse))
    result = hotspots(silver, weights=weights)
    result.write.mode("overwrite").parquet(paths.gold_path("hotspots", args.warehouse))

    print(f"hotspots written: rows={result.count()} weights={_weights_as_text(weights)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
