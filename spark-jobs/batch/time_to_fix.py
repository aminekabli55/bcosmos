"""Silver → per-rule distribution of commits-until-resolved (spec v2 section 6.1).

The distance is measured in **first-parent commits**, not in days: the findings event carries no commit timestamp, and
a "months to fix" number derived from ingestion time would say more about when the corpus was mined than about the
code. The definition and its one honest caveat are in `common/triples.py`; the caveat (a finding that was already
there when the mined window started has a lower-bound distance) is carried through as a null distance row and counted,
never averaged in.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pyspark.sql import DataFrame, functions as F  # noqa: E402

from common import paths, triples  # noqa: E402
from common.sessions import build_session  # noqa: E402

OUTPUT_COLUMNS: tuple[str, ...] = (
    "repository_id",
    "rule_id",
    "commits_until_resolved",
    "resolved_pairs",
    "observed_introductions",
)

DEFAULT_MAX_CHANGED_LINES = 30


def time_to_fix(silver: DataFrame, max_changed_lines: int = DEFAULT_MAX_CHANGED_LINES) -> DataFrame:
    """The histogram rows: one per (repository, rule, distance), plus the null-distance row."""

    return triples.time_to_fix_distribution(silver, max_changed_lines=max_changed_lines).select(*OUTPUT_COLUMNS)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Commits-until-resolved per rule.")
    parser.add_argument("--warehouse", default=None)
    parser.add_argument("--master", default=None)
    parser.add_argument("--max-changed-lines", type=int, default=DEFAULT_MAX_CHANGED_LINES)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-time-to-fix", master=args.master)

    silver = spark.read.parquet(paths.silver_path(args.warehouse))
    result = time_to_fix(silver, args.max_changed_lines)
    result.write.mode("overwrite").parquet(paths.gold_path("time_to_fix", args.warehouse))

    print(f"time_to_fix written: rows={result.count()} path={paths.gold_path('time_to_fix', args.warehouse)}")
    result.orderBy(F.col("resolved_pairs").desc()).show(truncate=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
