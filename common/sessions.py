"""The one place a `SparkSession` is built.

Two deliberate settings:

* `spark.sql.session.timeZone=UTC` — every partition date and trend bucket is derived from a timestamp, and a job
  that buckets by the local zone of whichever machine ran it produces a different dataset card on every machine.
* `spark.sql.ansi.enabled=false` — this pipeline reads untrusted input (events written by other services, and
  previously by other versions of them). A malformed value has to become a `null` that the silver job counts, not an
  exception that kills the stream. The silver job is the layer that decides what is invalid.
"""

from __future__ import annotations

import os

from pyspark.sql import SparkSession

#: Default shuffle parallelism for a local run or a single-worker standalone cluster.
DEFAULT_SHUFFLE_PARTITIONS = 8


def build_session(
    app_name: str,
    master: str | None = None,
    shuffle_partitions: int | None = None,
) -> SparkSession:
    """Builds (or returns) the session for one job.

    `master` is left unset unless it is given here or by `BCOSMOS_SPARK_MASTER`, so a `spark-submit
    --master spark://…` run keeps the cluster the operator chose instead of silently falling back to `local`.
    """

    builder = (
        SparkSession.builder.appName(app_name)
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.sql.ansi.enabled", "false")
        .config("spark.sql.shuffle.partitions", str(_shuffle_partitions(shuffle_partitions)))
    )

    resolved_master = master or os.environ.get("BCOSMOS_SPARK_MASTER")
    if resolved_master:
        builder = builder.master(resolved_master)

    return builder.getOrCreate()


def _shuffle_partitions(explicit: int | None) -> int:
    if explicit is not None:
        return explicit
    raw = os.environ.get("BCOSMOS_SPARK_SHUFFLE_PARTITIONS")
    if raw is None:
        return DEFAULT_SHUFFLE_PARTITIONS
    try:
        value = int(raw)
    except ValueError as error:  # a typo in an env var must not become a silent default
        raise ValueError(f"BCOSMOS_SPARK_SHUFFLE_PARTITIONS must be an integer, was {raw!r}") from error
    if value < 1:
        raise ValueError("BCOSMOS_SPARK_SHUFFLE_PARTITIONS must be at least 1")
    return value
