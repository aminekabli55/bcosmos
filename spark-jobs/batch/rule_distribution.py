"""Silver → findings per rule and severity per repository, per KLOC (spec v2 section 6.1).

The per-KLOC denominator needs a decision, because a repository is analysed many times and summing the line count of
every analysed revision would grow the denominator with the number of commits instead of with the amount of code.
The denominator here is the line count of every **distinct file content** observed for a repository
(`(file_path, content_hash)` pairs, summing `line_count`). Re-analysing a commit therefore does not inflate it, while
a file that was actually rewritten counts once per distinct revision it had — which is the closest honest reading of
"lines of code measured" from an event stream that carries no file inventory.

Parse failures are part of the output on purpose: a repository whose files do not parse is not a repository with
zero findings, and a chart that hides that is wrong.
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
    "rule_id",
    "severity",
    "findings",
    "files_affected",
    "measured_lines",
    "findings_per_kloc",
    "parse_failures",
)


def rule_distribution(silver: DataFrame) -> DataFrame:
    """One row per (repository, rule, severity)."""

    findings = (
        silver.filter(F.col("has_finding"))
        .groupBy("repository_id", "rule_id", "severity")
        .agg(
            F.count(F.lit(1)).alias("findings"),
            F.countDistinct("file_path").alias("files_affected"),
        )
    )

    measured = measured_lines(silver)
    parse_failures = (
        silver.filter(F.col("parse_status") == "FAILED")
        .groupBy("repository_id")
        .agg(F.count(F.lit(1)).alias("parse_failures"))
    )

    return (
        findings.join(measured, on="repository_id", how="left")
        .join(parse_failures, on="repository_id", how="left")
        .withColumn("parse_failures", F.coalesce(F.col("parse_failures"), F.lit(0)))
        # A repository that never reported a line count has no measurement, not a missing value: the column is
        # NOT NULL in `agg_rule_distribution`, and `findings_per_kloc` is the column that stays null.
        .withColumn("measured_lines", F.coalesce(F.col("measured_lines"), F.lit(0)))
        .withColumn(
            "findings_per_kloc",
            F.when(
                F.col("measured_lines") > 0,
                F.round(F.col("findings") * F.lit(1000.0) / F.col("measured_lines"), 4),
            ),
        )
        .select(*OUTPUT_COLUMNS)
    )


def measured_lines(silver: DataFrame) -> DataFrame:
    """`(repository_id, measured_lines)` from distinct file contents that reported a line count."""

    return (
        silver.filter(F.col("line_count").isNotNull() & (F.col("line_count") > 0))
        .select("repository_id", "file_path", "content_hash", "line_count")
        .distinct()
        .groupBy("repository_id")
        .agg(F.sum("line_count").alias("measured_lines"))
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Findings per rule and severity, per KLOC.")
    parser.add_argument("--warehouse", default=None)
    parser.add_argument("--master", default=None)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-rule-distribution", master=args.master)

    silver = spark.read.parquet(paths.silver_path(args.warehouse))
    result = rule_distribution(silver)
    result.write.mode("overwrite").parquet(paths.gold_path("rule_distribution", args.warehouse))

    print(f"rule_distribution written: rows={result.count()} path={paths.gold_path('rule_distribution', args.warehouse)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
