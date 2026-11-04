"""The streaming job itself: partitioned Parquet, a checkpoint, and a restart that must not duplicate.

The stream runs on the file source (`--input-dir`), which is the same `parse_events` + `bronze_rows` path the Kafka
source uses, so this exercises the parts a batch call cannot: the sink, the partitioning and the checkpoint.
"""

from __future__ import annotations

import json
import time
from pathlib import Path

from streaming.findings_to_bronze import bronze_stream, file_source, write_bronze
from tests.conftest import envelope


def _wait_for_partitions(bronze_path: Path, timeout: float = 120.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if any(bronze_path.rglob("*.parquet")):
            return
        time.sleep(1)
    raise AssertionError(f"no parquet file appeared under {bronze_path} within {timeout}s")


def _event_file(input_dir: Path, name: str, event: dict) -> None:
    input_dir.mkdir(parents=True, exist_ok=True)
    (input_dir / name).write_text(json.dumps(event), encoding="utf-8")


def test_the_stream_writes_partitioned_parquet_and_resumes_from_its_checkpoint(spark, tmp_path):
    input_dir = tmp_path / "events"
    bronze_path = tmp_path / "bronze"
    checkpoint = tmp_path / "checkpoint"

    _event_file(input_dir, "events.json", envelope())

    query = write_bronze(
        bronze_stream(file_source(spark, str(input_dir))),
        str(bronze_path),
        str(checkpoint),
        trigger_seconds=1,
    )
    try:
        query.processAllAvailable()
        _wait_for_partitions(bronze_path)
    finally:
        query.stop()

    first_run = spark.read.parquet(str(bronze_path))
    assert first_run.count() == 1
    assert first_run.collect()[0]["fingerprint"] == "fingerprint-1"

    # The partition layout is part of the contract: a query can prune by repository and by day. The sink's own
    # metadata log (`_spark_metadata`, what makes exactly-once sink writes possible) is not a partition.
    partitions = sorted(
        str(path.relative_to(bronze_path)) for path in bronze_path.iterdir() if not path.name.startswith("_")
    )
    assert partitions == ["repository_id=1"]
    assert (bronze_path / "repository_id=1" / "event_date=2026-05-04").is_dir()

    # Restarting with the same checkpoint must not re-read the file it already processed.
    resumed = write_bronze(
        bronze_stream(file_source(spark, str(input_dir))),
        str(bronze_path),
        str(checkpoint),
        trigger_seconds=1,
    )
    try:
        resumed.processAllAvailable()
        time.sleep(2)
    finally:
        resumed.stop()

    assert spark.read.parquet(str(bronze_path)).count() == 1
