"""Bronze → deduplicated, validated silver (spec v2 section 6.1).

Silver does three things and nothing else:

1. **Decides what is readable.** A row whose envelope this package does not understand, or whose finding is missing
   a field the contract declares, is *rejected* — it is written to `<silver>/_rejected` with a `rejection_reason`
   instead of being dropped. A pipeline that silently discards malformed input cannot tell "no findings" from
   "the events were malformed", and the difference matters when the corpus is the product.
2. **Deduplicates.** Kafka is at-least-once, so the same event can arrive twice. The key is the one Postgres
   upserts on (spec v2 section 5.3) plus the finding identity inside the file: a redelivery of the same event, or
   the same commit analysed by two workers, collapses to one row. The last delivery wins (`ingested_at`).
3. **Keeps the non-finding rows.** A file that failed to parse produced no findings but is still a fact: it is what
   the parse-failure rate and the "never mark a finding RESOLVED when the file did not parse" rule (section 4.2)
   are computed from.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pyspark.sql import DataFrame, Window, functions as F  # noqa: E402
from pyspark.sql.types import StringType  # noqa: E402

from common import paths, schema  # noqa: E402
from common.sessions import build_session  # noqa: E402

#: Fields every row needs before it can be aggregated at all, whatever its side.
REQUIRED_COLUMNS: tuple[str, ...] = (
    "repository_id",
    "commit_hash",
    "file_path",
    "side",
    "analyzer_version",
    "ruleset_hash",
    "parse_status",
)

#: Everything that identifies one finding inside one analysed revision. Line numbers are deliberately *not* the only
#: identity: the fingerprint is (section 4.2), and the start line keeps two findings of the same rule in one file
#: apart when the fingerprints are equal.
DEDUPLICATION_KEY: tuple[str, ...] = (
    "repository_id",
    "commit_hash",
    "file_path",
    "side",
    "analyzer_version",
    "ruleset_hash",
    "rule_id",
    "fingerprint",
    "start_line",
    "parse_status",
)

#: The rejection reasons, in the order they are tested; the first match wins, so the reason names the root cause.
REJECTION_REASONS: tuple[str, ...] = (
    "unsupported_schema",
    "missing_required_field",
    "invalid_side",
    "invalid_parse_status",
    "invalid_severity",
    "invalid_line_range",
    "missing_fingerprint",
)

SEVERITY_RANK = {name: len(schema.SEVERITIES) - index for index, name in enumerate(schema.SEVERITIES)}

#: The silver columns, explicitly: bronze without the internal `understood` flag, plus what every aggregate reads.
#: `tests/conftest.py` builds its fixture frames from this tuple, so a column added here cannot be missing there.
SILVER_COLUMNS: tuple[str, ...] = tuple(
    name for name in schema.BRONZE_COLUMNS if name != "understood"
) + ("has_finding", "severity_rank")


def clean(bronze: DataFrame) -> tuple[DataFrame, DataFrame]:
    """Splits raw bronze rows into `(silver, rejected)`, then deduplicates the silver side.

    The rejected frame keeps every bronze column plus `rejection_reason`, so a rejected row can be traced back to its
    event id and correlation id.
    """

    annotated = bronze.withColumn("rejection_reason", _rejection_reason())
    valid = annotated.filter(F.col("rejection_reason").isNull()).drop("rejection_reason")
    rejected = annotated.filter(F.col("rejection_reason").isNotNull())
    return deduplicate(_enrich(valid)), rejected


def deduplicate(rows: DataFrame) -> DataFrame:
    """Keeps one row per `DEDUPLICATION_KEY`, the most recently ingested one."""

    window = Window.partitionBy(*DEDUPLICATION_KEY).orderBy(
        F.col("ingested_at").desc_nulls_last(), F.col("event_id").asc_nulls_first()
    )
    return (
        rows.withColumn("_delivery_rank", F.row_number().over(window))
        .filter(F.col("_delivery_rank") == 1)
        .drop("_delivery_rank")
    )


def rejections_by_reason(rejected: DataFrame) -> DataFrame:
    """`(rejection_reason, rows)`, the shape `load_gold` stores so the UI can show what was thrown away and why."""

    return rejected.groupBy("rejection_reason").agg(F.count(F.lit(1)).alias("rows"))


def _enrich(valid: DataFrame) -> DataFrame:
    """Adds the columns silver and everything above it reads, in a fixed order."""

    severity_rank = F.lit(None).cast("int")
    for name, rank in SEVERITY_RANK.items():
        severity_rank = F.when(F.col("severity") == name, F.lit(rank)).otherwise(severity_rank)

    return valid.drop("understood").withColumn("has_finding", F.col("rule_id").isNotNull()).withColumn(
        "severity_rank", severity_rank
    ).select(*SILVER_COLUMNS)


def _rejection_reason():
    """The first reason this row cannot be trusted, or null."""

    is_finding = F.col("rule_id").isNotNull()
    return (
        F.when(~F.col("understood"), F.lit("unsupported_schema"))
        .when(_any_required_is_null(), F.lit("missing_required_field"))
        .when(~F.col("side").isin(*schema.SIDES), F.lit("invalid_side"))
        .when(~F.col("parse_status").isin(*schema.PARSE_STATUSES), F.lit("invalid_parse_status"))
        .when(is_finding & (~F.col("severity").isin(*schema.SEVERITIES)), F.lit("invalid_severity"))
        .when(
            is_finding
            & (
                F.col("start_line").isNull()
                | F.col("end_line").isNull()
                | (F.col("start_line") < 1)
                | (F.col("end_line") < F.col("start_line"))
            ),
            F.lit("invalid_line_range"),
        )
        .when(is_finding & F.col("fingerprint").isNull(), F.lit("missing_fingerprint"))
        .otherwise(F.lit(None).cast(StringType()))
    )


def _any_required_is_null():
    condition = F.lit(False)
    for column in REQUIRED_COLUMNS:
        condition = condition | F.col(column).isNull()
    return condition


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Clean and deduplicate bronze findings into silver.")
    parser.add_argument("--warehouse", default=None)
    parser.add_argument("--master", default=None)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-silver-clean", master=args.master)

    bronze = spark.read.parquet(paths.bronze_path(args.warehouse))
    silver, rejected = clean(bronze)

    silver.write.mode("overwrite").partitionBy("repository_id").parquet(paths.silver_path(args.warehouse))
    rejected.write.mode("overwrite").parquet(f"{paths.silver_path(args.warehouse)}_rejected")

    kept = silver.count()
    print(f"silver written: kept={kept} rejected={rejected.count()} path={paths.silver_path(args.warehouse)}")
    rejections_by_reason(rejected).orderBy(F.col("rows").desc()).show(truncate=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
