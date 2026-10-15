"""Kafka `analysis.findings.v1` → Parquet bronze, checkpointed (spec v2 section 6.1).

At-least-once delivery plus a checkpointed sink is exactly what bronze should be: the raw, replayable record of what
arrived. Duplicates are the silver job's problem (it deduplicates on the same key Postgres upserts on), and the
`event_id` of every delivery is kept so a duplicate is auditable rather than invisible.

Two sources are supported, and they share the whole transform:

* `--bootstrap-servers` (default, the real pipeline): the Kafka topic.
* `--input-dir`: a directory of newline-delimited event JSON files, read as a stream. This is how the job is run
  end to end without a broker — the same `parse_events` + `bronze_rows` code, so what a test exercises is what the
  broker path runs.

Run inside the cluster (ADR 0017):

    docker compose --profile spark run --rm spark-submit \\
      --master spark://spark-master:7077 /opt/jobs/streaming/findings_to_bronze.py --duration-seconds 60
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

# A job started by `spark-submit <file>` has the file's own directory on `sys.path`, not its parent, so the shared
# `common` package needs one insertion. Importing the job as a module (pytest, another job) never runs this.
if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pyspark.sql import DataFrame, SparkSession  # noqa: E402
from pyspark.sql.streaming import StreamingQuery  # noqa: E402

from common import paths, schema  # noqa: E402
from common.sessions import build_session  # noqa: E402

DEFAULT_TOPIC = "analysis.findings.v1"
DEFAULT_TRIGGER_SECONDS = 20
DEFAULT_STARTING_OFFSETS = "earliest"


def kafka_source(
    spark: SparkSession,
    bootstrap_servers: str,
    topic: str,
    starting_offsets: str,
    max_offsets_per_trigger: int | None = None,
) -> DataFrame:
    """The raw Kafka stream: `key`, `value`, `topic`, `partition`, `offset`."""

    reader = (
        spark.readStream.format("kafka")
        .option("kafka.bootstrap.servers", bootstrap_servers)
        .option("subscribe", topic)
        .option("startingOffsets", starting_offsets)
        # A long session must not be dropped for idling between commits: a quiet corpus is normal.
        .option("kafka.session.timeout.ms", "60000")
        .option("failOnDataLoss", "false")
    )
    if max_offsets_per_trigger is not None:
        reader = reader.option("maxOffsetsPerTrigger", str(max_offsets_per_trigger))
    return reader.load()


def file_source(spark: SparkSession, input_dir: str, max_files_per_trigger: int | None = None) -> DataFrame:
    """A directory of `*.json` events read as a stream; each line is one envelope."""

    reader = spark.readStream.format("text").option("path", input_dir).option("maxFilesPerTrigger", max_files_per_trigger or 100)
    return reader.load()


def bronze_stream(source: DataFrame) -> DataFrame:
    """The parsed and flattened rows bronze stores. Pure: no I/O, so a test can call it on a batch DataFrame."""

    return schema.bronze_rows(schema.parse_events(source))


def write_bronze(
    rows: DataFrame,
    bronze_path: str,
    checkpoint: str,
    trigger_seconds: int = DEFAULT_TRIGGER_SECONDS,
    query_name: str = "findings-to-bronze",
) -> StreamingQuery:
    """Appends to Parquet, one micro-batch at a time, resumable from the checkpoint."""

    return (
        rows.writeStream.outputMode("append")
        .format("parquet")
        .option("path", bronze_path)
        .option("checkpointLocation", checkpoint)
        .partitionBy(*schema.BRONZE_PARTITION_COLUMNS)
        .trigger(processingTime=f"{trigger_seconds} seconds")
        .queryName(query_name)
        .start()
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Stream analysis.findings.v1 events into Parquet bronze.")
    parser.add_argument("--bootstrap-servers", default=os.environ.get("BCOSMOS_KAFKA_BOOTSTRAP", "localhost:9092"))
    parser.add_argument("--topic", default=DEFAULT_TOPIC)
    parser.add_argument("--starting-offsets", default=DEFAULT_STARTING_OFFSETS, choices=["earliest", "latest"])
    parser.add_argument("--input-dir", default=None, help="read newline-delimited JSON files instead of Kafka")
    parser.add_argument("--warehouse", default=None, help="warehouse root; defaults to BCOSMOS_SPARK_WAREHOUSE")
    parser.add_argument("--master", default=None, help="Spark master; defaults to the spark-submit one")
    parser.add_argument("--trigger-seconds", type=int, default=DEFAULT_TRIGGER_SECONDS)
    parser.add_argument("--max-offsets-per-trigger", type=int, default=None)
    parser.add_argument(
        "--duration-seconds",
        type=int,
        default=0,
        help="stop after this long (0 waits until the stream fails or the process is asked to stop)",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spark = build_session("bcosmos-findings-to-bronze", master=args.master)

    if args.input_dir:
        source = file_source(spark, args.input_dir)
    else:
        source = kafka_source(spark, args.bootstrap_servers, args.topic, args.starting_offsets,
                              args.max_offsets_per_trigger)

    query = write_bronze(
        bronze_stream(source),
        paths.bronze_path(args.warehouse),
        paths.checkpoint_path("findings-to-bronze", args.warehouse),
        trigger_seconds=args.trigger_seconds,
    )

    print(f"bronze stream started: query={query.name} bronze={paths.bronze_path(args.warehouse)}")
    if args.duration_seconds > 0:
        query.awaitTermination(args.duration_seconds)
        if query.exception() is not None:
            raise SystemExit(f"stream failed: {query.exception()}")
        print(f"bronze stream stopped after {args.duration_seconds}s: {query.lastProgress}")
    else:
        query.awaitTermination()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
