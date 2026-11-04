"""Silver → the fix-triple dataset, versioned and split by repository (spec v2 section 6.2).

The output carries the version, the split and the commit distance of every triple, and the job prints the counts the
dataset card is built from. Nothing about the numbers is estimated here: they are all computed from the rows that
were written.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pyspark.sql import DataFrame, functions as F  # noqa: E402

from common import dataset, paths, triples  # noqa: E402
from common.sessions import build_session  # noqa: E402

#: Upper bound on the changed lines of a triple (spec v2 section 6.2). A configurable default, stored with the
#: dataset so the card can state the filter it used.
DEFAULT_MAX_CHANGED_LINES = 30


def build_dataset(silver: DataFrame, dataset_version: str, max_changed_lines: int = DEFAULT_MAX_CHANGED_LINES) -> DataFrame:
    """The dataset rows: triples for one version, tagged with their repository's split."""

    return (
        dataset.with_split(triples.fix_triples(silver, max_changed_lines=max_changed_lines))
        .withColumn("dataset_version", F.lit(dataset_version))
        .withColumn("max_changed_lines", F.lit(max_changed_lines))
        .withColumn("generated_at", F.current_timestamp())
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Build the versioned fix-triple dataset.")
    parser.add_argument("--warehouse", default=None)
    parser.add_argument("--master", default=None)
    parser.add_argument("--max-changed-lines", type=int, default=DEFAULT_MAX_CHANGED_LINES)
    parser.add_argument("--dataset-version", default=None, help="override the derived version (tests only)")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-fix-triples", master=args.master)

    silver = spark.read.parquet(paths.silver_path(args.warehouse))
    version = args.dataset_version or dataset.dataset_version(silver)
    dataset_rows = build_dataset(silver, version, args.max_changed_lines)

    target = paths.dataset_path("fix_triples", version, args.warehouse)
    dataset_rows.write.mode("overwrite").parquet(target)

    print(f"fix_triples written: version={version} rows={dataset_rows.count()} path={target}")
    print("triples by split:")
    dataset_rows.groupBy("split").agg(F.count(F.lit(1)).alias("triples")).orderBy("split").show(truncate=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
