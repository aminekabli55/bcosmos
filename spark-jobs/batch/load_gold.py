"""Gold Parquet → Postgres `agg_*` (spec v2 section 6.1), plus the generated dataset card.

This is the only job that writes to Postgres, and it is the reason the backend never queries Spark: every chart reads
a table this job filled, together with the `dataset_version` and `generated_at` of the snapshot it came from.

The load is a **snapshot replacement**: each `agg_*` table is truncated and rewritten, so the API always sees one
consistent set of rows for one dataset version instead of a mix of runs. The tables stay owned by Liquibase
(`truncate=true` never drops them), which is why these frames have to match changeset 0018 exactly — a mismatch fails
loudly at insert time instead of silently writing nulls.

The operational `repository` table is read (id, name, licence) so the dataset card can report licences per
repository. That is a read of a dimension by a Spark job; the direction that must never exist is the backend querying
Spark.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pyspark.sql import DataFrame, SparkSession, Window, functions as F  # noqa: E402

from common import card, dataset, paths, triples  # noqa: E402
from common.sessions import build_session  # noqa: E402

#: Who produced the snapshot; part of the `gold_run` uniqueness key.
SOURCE = "spark"

#: Drill-down rows are capped to this many commits per repository (the most recently analysed ones), so Postgres does
#: not become a second, unbounded copy of the event stream. The number reaches the dataset card and the API.
DEFAULT_DRILLDOWN_COMMITS = 50

POSTGRES_DRIVER = "org.postgresql.Driver"

#: The tables this job fills, in write order.
GOLD_TABLES: tuple[str, ...] = (
    "gold_run",
    "agg_pipeline_stat",
    "agg_rule_distribution",
    "agg_quality_trend",
    "agg_time_to_fix",
    "agg_hotspot",
    "agg_finding",
    "agg_dataset",
    "agg_dataset_repository",
)


def jdbc_options(args: argparse.Namespace) -> dict[str, str]:
    """Connection options from the same environment variables the backend uses."""

    host = args.db_host or os.environ.get("BCOSMOS_DB_HOST", "localhost")
    port = args.db_port or os.environ.get("BCOSMOS_DB_PORT", "5432")
    name = args.db_name or os.environ.get("BCOSMOS_DB_NAME", "bcosmos")
    return {
        "url": args.jdbc_url or f"jdbc:postgresql://{host}:{port}/{name}",
        "user": os.environ.get("BCOSMOS_DB_USER", "bcosmos"),
        "password": os.environ.get("BCOSMOS_DB_PASSWORD", "bcosmos"),
        "driver": POSTGRES_DRIVER,
    }


def read_repository_dimension(spark: SparkSession, options: dict[str, str]) -> DataFrame:
    """`(repository_id, repository_name, license_spdx)` from the operational table."""

    return (
        spark.read.format("jdbc")
        .options(**options)
        .option("query", "SELECT id AS repository_id, name AS repository_name, license_spdx FROM repository")
        .load()
    )


def drilldown_findings(silver: DataFrame, depths: DataFrame, commit_limit: int, dataset_version: str) -> DataFrame:
    """The findings behind the aggregates, limited to the most recent `commit_limit` commits per repository."""

    commits = (
        silver.select("repository_id", "commit_hash")
        .distinct()
        .alias("commit")
        .join(
            triples.depth_lookup(depths).alias("depth"),
            on=[
                F.col("commit.repository_id") == F.col("depth.depth_repository_id"),
                F.col("commit.commit_hash") == F.col("depth.depth_commit_hash"),
            ],
            how="left",
        )
        # A commit whose parent and children are both outside the corpus has no depth; it still has findings and must
        # not disappear from the drill-down.
        .withColumn("depth", F.coalesce(F.col("resolved_depth"), F.lit(0)))
        .select(
            F.col("commit.repository_id").alias("repository_id"),
            F.col("commit.commit_hash").alias("commit_hash"),
            F.col("depth.resolved_depth").alias("depth"),
        )
        .withColumn(
            "commit_rank",
            F.row_number().over(
                Window.partitionBy("repository_id").orderBy(F.col("depth").desc_nulls_last(),
                                                           F.col("commit_hash").asc())
            ),
        )
        .filter(F.col("commit_rank") <= commit_limit)
        .select("repository_id", "commit_hash")
    )

    return (
        silver.filter(F.col("has_finding"))
        .alias("silver")
        .join(commits.alias("recent"), on=["repository_id", "commit_hash"], how="inner")
        .select(F.col("silver.*"))
        .select(
            F.lit(dataset_version).alias("dataset_version"),
            F.current_timestamp().alias("generated_at"),
            "repository_id",
            "commit_hash",
            "file_path",
            "side",
            "rule_id",
            "severity",
            "symbol",
            "fingerprint",
            F.col("start_line").cast("int").alias("start_line"),
            F.col("end_line").cast("int").alias("end_line"),
            F.col("line_count").cast("int").alias("line_count"),
            "occurred_at",
            "snippet",
        )
        .distinct()
    )


def build_tables(
    spark: SparkSession,
    silver: DataFrame,
    dataset_version: str,
    warehouse: str | None,
    drilldown_commits: int,
    repository_dimension: DataFrame,
) -> dict[str, DataFrame]:
    """Every `agg_*` frame, keyed by table name. One place, so the loader and its test agree on the shape."""

    generated = F.current_timestamp()

    def stamped(frame: DataFrame) -> DataFrame:
        return frame.withColumn("dataset_version", F.lit(dataset_version)).withColumn("generated_at", generated)

    triple_rows = _read_dataset(spark, warehouse, dataset_version)

    tables: dict[str, DataFrame] = {}

    tables["agg_rule_distribution"] = stamped(
        _read_gold(spark, warehouse, "rule_distribution").select(
            "repository_id",
            "rule_id",
            "severity",
            "findings",
            "files_affected",
            "measured_lines",
            "findings_per_kloc",
            "parse_failures",
        )
    )
    tables["agg_quality_trend"] = stamped(
        _read_gold(spark, warehouse, "quality_trends").select(
            "repository_id",
            "bucket_start",
            "findings",
            "files_analysed",
            "measured_lines",
            "density_per_kloc",
            "parse_failures",
        )
    )
    tables["agg_time_to_fix"] = stamped(
        _read_gold(spark, warehouse, "time_to_fix").select(
            "repository_id",
            "rule_id",
            F.col("commits_until_resolved").cast("int").alias("commits_until_resolved"),
            "resolved_pairs",
            "observed_introductions",
        )
    )
    tables["agg_hotspot"] = stamped(
        _read_gold(spark, warehouse, "hotspots").select(
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
    )

    depths = triples.first_parent_depth(triples.first_parent_edges(silver))
    tables["agg_finding"] = drilldown_findings(silver, depths, drilldown_commits, dataset_version)

    # The dataset card: counts per split, per repository (with its licence) and per rule.
    splits = triple_rows.groupBy("split").agg(
        F.count(F.lit(1)).alias("triples"),
        F.countDistinct("repository_id").cast("int").alias("repositories"),
        F.countDistinct("rule_id").cast("int").alias("rules"),
        F.sum(F.when(F.col("snippet_changed"), F.lit(1)).otherwise(F.lit(0))).alias("snippet_changed"),
        F.sum(F.when(~F.col("introduction_observed"), F.lit(1)).otherwise(F.lit(0))).alias("introductions_unobserved"),
        F.max("max_changed_lines").cast("int").alias("max_changed_lines"),
    )
    tables["agg_dataset"] = stamped(
        splits.select(
            "split",
            "triples",
            "repositories",
            "rules",
            "snippet_changed",
            "introductions_unobserved",
            "max_changed_lines",
            F.lit("max_changed_lines filter; see docs/dataset-card.md").alias("filters"),
        )
    )

    tables["agg_dataset_repository"] = stamped(
        triple_rows.groupBy("split", "repository_id")
        .agg(F.count(F.lit(1)).alias("triples"))
        .join(
            repository_dimension.select("repository_id", "repository_name", "license_spdx"),
            on="repository_id",
            how="left",
        )
        .select("split", "repository_id", "repository_name", "license_spdx", "triples")
    )

    tables["agg_pipeline_stat"] = _pipeline_stats(
        spark, silver, dataset_version, warehouse, triple_rows, drilldown_commits
    )

    tables["gold_run"] = stamped(
        _single_row(
            spark,
            source=SOURCE,
            repository_count=triple_rows.select("repository_id").distinct().count(),
            triple_count=triple_rows.count(),
            notes=f"drill-down limited to the most recent {drilldown_commits} commits per repository",
        )
    )

    return {name: tables[name] for name in GOLD_TABLES}


def write_tables(tables: dict[str, DataFrame], options: dict[str, str]) -> dict[str, int]:
    """Truncates and rewrites each table; returns the row count actually written per table."""

    written: dict[str, int] = {}
    for name in GOLD_TABLES:
        frame = tables[name]
        rows = frame.count()
        (
            frame.write.mode("overwrite")
            .option("truncate", "true")
            .format("jdbc")
            .options(**options)
            .option("dbtable", name)
            .save()
        )
        written[name] = rows
    return written


def build_summary(
    dataset_version: str,
    generated_at: str,
    tables: dict[str, DataFrame],
    drilldown_commits: int,
    pipeline_stats: dict[str, int],
    triple_rows: DataFrame,
) -> dict:
    """Plain Python values for the card; bounded by construction (splits, repositories, top rules).

    The keys are the ones `common.card.render_dataset_card` reads, and `repositories` is the **list of rows** (the
    card prints a table of them and counts them by length), not a count: a hand-made summary in a test once supplied
    a list while this function supplied an int, and nothing failed until a real load tried to render the card.
    `tests/test_card.py::test_the_loader_summary_is_what_the_renderer_reads` now builds the summary here and renders
    it there, so the two agree by test rather than by hope.
    """

    split_rows = [row.asDict() for row in tables["agg_dataset"].orderBy("split").collect()]
    repository_rows = [
        row.asDict()
        for row in tables["agg_dataset_repository"]
        .orderBy(F.col("triples").desc(), F.col("repository_id").asc())
        .collect()
    ]
    rule_rows = [
        row.asDict()
        for row in triple_rows.groupBy("rule_id")
        .agg(F.count(F.lit(1)).alias("triples"))
        .orderBy(F.col("triples").desc(), F.col("rule_id").asc())
        .limit(50)
        .collect()
    ]

    return {
        "dataset_version": dataset_version,
        "generated_at": generated_at,
        "triples": sum(int(row.get("triples") or 0) for row in split_rows),
        "repositories": repository_rows,
        "rules": triple_rows.select("rule_id").distinct().count(),
        "splits": split_rows,
        "repositories_without_license": sum(1 for row in repository_rows if not row.get("license_spdx")),
        "rules_covered": rule_rows,
        "max_changed_lines": max([int(row.get("max_changed_lines") or 0) for row in split_rows] or [0]),
        "snippet_changed": sum(int(row.get("snippet_changed") or 0) for row in split_rows),
        "introductions_unobserved": sum(int(row.get("introductions_unobserved") or 0) for row in split_rows),
        "drilldown_commits": drilldown_commits,
        "pipeline_stats": pipeline_stats,
    }


def _pipeline_stats(
    spark: SparkSession,
    silver: DataFrame,
    dataset_version: str,
    warehouse: str | None,
    triple_rows: DataFrame,
    drilldown_commits: int,
) -> DataFrame:
    """Named counts, including what silver threw away and why (`agg_pipeline_stat`)."""

    stats: dict[str, int] = {
        # The drill-down cap travels as a stat so the API can tell the screen how far "drill down to records"
        # really reaches, instead of the screen assuming it covers everything.
        "drilldown_commits": int(drilldown_commits),
        "silver_rows": silver.count(),
        "finding_rows": silver.filter(F.col("has_finding")).count(),
        "repositories": silver.select("repository_id").distinct().count(),
        "commits_observed": silver.select("repository_id", "commit_hash").distinct().count(),
        "files_observed": silver.select("repository_id", "file_path", "content_hash").distinct().count(),
        "parse_failures": silver.filter(F.col("parse_status") == "FAILED").count(),
        "triples": triple_rows.count(),
    }

    rejected = _read_rejected(spark, warehouse)
    if rejected is not None:
        stats["rejected_rows"] = rejected.count()
        for row in rejected.groupBy("rejection_reason").agg(F.count(F.lit(1)).alias("rows")).collect():
            stats[f"rejected_{row['rejection_reason']}"] = int(row["rows"])

    frame = spark.createDataFrame(
        [(key, int(value)) for key, value in sorted(stats.items())], "stat_key string, stat_value long"
    )
    return (
        frame.withColumn("dataset_version", F.lit(dataset_version))
        .withColumn("generated_at", F.current_timestamp())
        .select("dataset_version", "generated_at", "stat_key", "stat_value")
    )


def _single_row(spark: SparkSession, **values) -> DataFrame:
    return spark.createDataFrame([tuple(values.values())], list(values.keys()))


def _read_gold(spark: SparkSession, warehouse: str | None, name: str) -> DataFrame:
    return spark.read.parquet(paths.gold_path(name, warehouse))


def _read_dataset(spark: SparkSession, warehouse: str | None, dataset_version: str) -> DataFrame:
    return spark.read.parquet(paths.dataset_path("fix_triples", dataset_version, warehouse))


def _read_rejected(spark: SparkSession, warehouse: str | None) -> DataFrame | None:
    """The rejected silver rows when `silver_clean` wrote them; `None` when it did not run."""

    try:
        return spark.read.parquet(f"{paths.silver_path(warehouse)}_rejected")
    except Exception:
        # A missing rejection directory means silver was written by a run that did not record rejections. The stats
        # then simply do not claim a number for them.
        return None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Load the gold tables into Postgres and write the dataset card.")
    parser.add_argument("--warehouse", default=None)
    parser.add_argument("--master", default=None)
    parser.add_argument("--jdbc-url", default=None)
    parser.add_argument("--db-host", default=None)
    parser.add_argument("--db-port", default=None)
    parser.add_argument("--db-name", default=None)
    parser.add_argument("--drilldown-commits", type=int, default=DEFAULT_DRILLDOWN_COMMITS)
    parser.add_argument("--dataset-card", default=None, help="write the generated dataset card to this path")
    parser.add_argument("--dataset-version", default=None, help="override the derived version (tests only)")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-load-gold", master=args.master)

    silver = spark.read.parquet(paths.silver_path(args.warehouse))
    version = args.dataset_version or dataset.dataset_version(silver)
    options = jdbc_options(args)

    triple_rows = _read_dataset(spark, args.warehouse, version)
    repository_dimension = read_repository_dimension(spark, options)

    tables = build_tables(spark, silver, version, args.warehouse, args.drilldown_commits, repository_dimension)
    gold_run = tables["gold_run"].select("generated_at").collect()[0]["generated_at"].isoformat()
    pipeline_stats = {row["stat_key"]: int(row["stat_value"]) for row in tables["agg_pipeline_stat"].collect()}

    written = write_tables(tables, options)
    print(f"gold loaded: dataset_version={version} url={options['url']}")
    for name in GOLD_TABLES:
        print(f"  {name}: {written[name]} rows")

    if args.dataset_card:
        summary = build_summary(version, gold_run, tables, args.drilldown_commits, pipeline_stats, triple_rows)
        card_path = Path(args.dataset_card)
        card_path.parent.mkdir(parents=True, exist_ok=True)
        card_path.write_text(card.render_dataset_card(summary), encoding="utf-8")
        print(f"dataset card written: {card_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
