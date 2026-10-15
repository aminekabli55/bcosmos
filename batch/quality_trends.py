"""Silver → finding density over time per repository (spec v2 section 6.1).

The time axis is `occurredAt`, the moment the revision was analysed, and **not** the commit date: the findings event
carries no commit timestamp, and inventing one from the commit hash is not possible. Every place this number is shown
has to say "over the window in which those revisions were analysed", which is what the analytics screen labels the
axis with. Using the ingestion time is also the only choice that stays correct when a corpus is mined months after
the commits were written.

The bucket is a UTC week. `line_count` is summed over distinct file contents per bucket, the same denominator rule as
`rule_distribution`, so a week with heavy re-analysis is not a week with more code.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pyspark.sql import DataFrame, functions as F  # noqa: E402

from common import paths  # noqa: E402
from common.sessions import build_session  # noqa: E402

OUTPUT_COLUMNS: tuple[str, ...] = (
    "repository_id",
    "bucket_start",
    "findings",
    "files_analysed",
    "measured_lines",
    "density_per_kloc",
    "parse_failures",
)


def quality_trends(silver: DataFrame) -> DataFrame:
    """One row per (repository, UTC week in which the revisions were analysed)."""

    bucketed = silver.withColumn("bucket_start", F.date_trunc("week", F.col("occurred_at")).cast("date"))

    findings = (
        bucketed.filter(F.col("has_finding"))
        .groupBy("repository_id", "bucket_start")
        .agg(
            F.count(F.lit(1)).alias("findings"),
            F.countDistinct("file_path").alias("files_analysed"),
        )
    )

    measured = (
        bucketed.filter(F.col("line_count").isNotNull() & (F.col("line_count") > 0))
        .select("repository_id", "bucket_start", "file_path", "content_hash", "line_count")
        .distinct()
        .groupBy("repository_id", "bucket_start")
        .agg(F.sum("line_count").alias("measured_lines"))
    )

    parse_failures = (
        bucketed.filter(F.col("parse_status") == "FAILED")
        .groupBy("repository_id", "bucket_start")
        .agg(F.count(F.lit(1)).alias("parse_failures"))
    )

    return (
        measured.join(findings, on=["repository_id", "bucket_start"], how="full")
        .join(parse_failures, on=["repository_id", "bucket_start"], how="full")
        .fillna({"findings": 0, "files_analysed": 0, "parse_failures": 0, "measured_lines": 0})
        .withColumn(
            "density_per_kloc",
            F.when(
                F.col("measured_lines") > 0,
                F.round(F.col("findings") * F.lit(1000.0) / F.col("measured_lines"), 4),
            ),
        )
        .select(*OUTPUT_COLUMNS)
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Finding density per repository per UTC week.")
    parser.add_argument("--warehouse", default=None)
    parser.add_argument("--master", default=None)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-quality-trends", master=args.master)

    silver = spark.read.parquet(paths.silver_path(args.warehouse))
    result = quality_trends(silver)
    result.write.mode("overwrite").parquet(paths.gold_path("quality_trends", args.warehouse))

    print(f"quality_trends written: rows={result.count()} path={paths.gold_path('quality_trends', args.warehouse)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
