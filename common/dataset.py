"""Dataset identity: the version every gold table names, and the train/validation/test split.

Both have to be deterministic and derivable from the data, because the dataset card, the gold tables and the
leaderboard of Phase 6 all refer to them and a re-run must reproduce the same ones.

* **Version** — `v<last event date>-<sha256 of the measurement identity>`. The measurement identity is the set of
  `(analyzer_version, ruleset_hash)` pairs in silver: change the rule set and the version changes, exactly as the
  `rulesetHash` identifies a single run.
* **Split** — by **repository**, never by commit or file (spec v2 section 6.2): a hash of the repository id picks the
  split, so two commits of one repository can never end up on both sides of the train/test boundary. The split is a
  pure function of the repository id, so it is stable across re-runs and across machines.
"""

from __future__ import annotations

import hashlib

from pyspark.sql import Column, DataFrame, functions as F
from pyspark.sql.types import StringType

#: Split names, in the order they are reported.
SPLITS: tuple[str, ...] = ("train", "validation", "test")

#: Share of repositories per split, in percent; the last one takes the remainder.
TRAIN_PERCENT = 80
VALIDATION_PERCENT = 10

#: The split of a pair of revisions must agree across every row of a repository, so the split column is derived from
#: the repository id alone.
_SPLIT_COLUMN = "split"


def dataset_version(silver: DataFrame) -> str:
    """The version of the dataset `silver` produces, e.g. `v20261014-3f9a1c02`."""

    identity = (
        silver.select("analyzer_version", "ruleset_hash")
        .distinct()
        .orderBy("analyzer_version", "ruleset_hash")
        .collect()
    )
    if not identity:
        raise ValueError("cannot version an empty silver layer: no analyzed revision is available")

    fingerprint_source = "|".join(f"{row['analyzer_version']}@{row['ruleset_hash']}" for row in identity)
    digest = hashlib.sha256(fingerprint_source.encode("utf-8")).hexdigest()[:8]

    last_date = silver.agg(F.max("event_date").alias("last_date")).collect()[0]["last_date"]
    if last_date is None:
        raise ValueError("cannot version a dataset without a single dated event")

    return f"v{last_date.strftime('%Y%m%d')}-{digest}"


def assign_split(repository_id: int) -> str:
    """The split of one repository: a stable function of its id, so re-running never moves a repository."""

    bucket = int(hashlib.sha256(str(repository_id).encode("utf-8")).hexdigest(), 16) % 100
    if bucket < TRAIN_PERCENT:
        return "train"
    if bucket < TRAIN_PERCENT + VALIDATION_PERCENT:
        return "validation"
    return "test"


def with_split(rows: DataFrame, repository_column: str = "repository_id") -> DataFrame:
    """Adds the `split` column, computed per repository rather than per row."""

    split_udf = F.udf(assign_split, StringType())
    return rows.withColumn(_SPLIT_COLUMN, split_udf(F.col(repository_column)))


def split_column() -> Column:
    return F.col(_SPLIT_COLUMN)
