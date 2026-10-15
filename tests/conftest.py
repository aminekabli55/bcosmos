"""Shared fixtures: one Spark session for the suite, and small builders for events and silver rows.

The events a test feeds in are the real wire format (the envelope and payload keys are the ones
`FindingsEventWireContractTest` pins on the Java side), so the tests exercise `from_json` against the schema the
pipeline actually uses instead of against a hand-built DataFrame that could drift from it.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import pytest
from pyspark.sql import DataFrame, SparkSession
from pyspark.sql.types import (
    BooleanType,
    DateType,
    IntegerType,
    LongType,
    StringType,
    StructField,
    StructType,
    TimestampType,
)

from batch.silver_clean import SILVER_COLUMNS
from common.schema import bronze_rows, parse_events

#: Types of the silver columns; `silver_frame` needs them explicitly, because a column that is null in every fixture
#: row cannot be inferred.
SILVER_TYPES: dict[str, object] = {
    "repository_id": LongType(),
    "event_id": StringType(),
    "event_type": StringType(),
    "schema_version": IntegerType(),
    "occurred_at": TimestampType(),
    "event_date": DateType(),
    "correlation_id": StringType(),
    "commit_hash": StringType(),
    "parent_hash": StringType(),
    "file_path": StringType(),
    "content_hash": StringType(),
    "line_count": IntegerType(),
    "side": StringType(),
    "analyzer_version": StringType(),
    "ruleset_hash": StringType(),
    "parse_status": StringType(),
    "rule_id": StringType(),
    "severity": StringType(),
    "message": StringType(),
    "start_line": IntegerType(),
    "end_line": IntegerType(),
    "symbol": StringType(),
    "fingerprint": StringType(),
    "snippet": StringType(),
    "ingested_at": TimestampType(),
    "has_finding": BooleanType(),
    "severity_rank": IntegerType(),
}

ANALYZER_VERSION = "PMD 7.28.0"
RULESET_HASH = "ruleset-fixture"


@pytest.fixture(scope="session")
def spark() -> SparkSession:
    """A local session for the whole suite; the jobs are tested through their pure transforms."""

    session = (
        SparkSession.builder.appName("bcosmos-spark-tests")
        .master("local[2]")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.shuffle.partitions", "2")
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.sql.ansi.enabled", "false")
        .getOrCreate()
    )
    session.sparkContext.setLogLevel("ERROR")
    yield session
    session.stop()


def silver_row(**overrides) -> dict:
    """One silver row with sensible defaults, so a test only states what it is about."""

    occurred = datetime(2026, 5, 4, 10, 0, tzinfo=timezone.utc)
    row = {
        "repository_id": 1,
        "event_id": "event-1",
        "event_type": "FINDINGS",
        "schema_version": 1,
        "occurred_at": occurred,
        "event_date": occurred.date(),
        "correlation_id": "corr-1",
        "commit_hash": "c0ffee",
        "parent_hash": None,
        "file_path": "src/main/java/com/example/Sample.java",
        "content_hash": "content-1",
        "line_count": 100,
        "side": "AFTER",
        "analyzer_version": ANALYZER_VERSION,
        "ruleset_hash": RULESET_HASH,
        "parse_status": "OK",
        "rule_id": "EmptyCatchBlock",
        "severity": "MEDIUM",
        "message": "Empty catch block",
        "start_line": 10,
        "end_line": 12,
        "symbol": "com.example.Sample.test",
        "fingerprint": "fingerprint-1",
        "snippet": "} catch (Exception e) {\n}",
        "ingested_at": occurred,
        "has_finding": True,
        "severity_rank": 3,
    }
    row.update(overrides)
    return row


def silver_frame(spark: SparkSession, rows: list[dict]) -> DataFrame:
    """A silver DataFrame with the exact columns and types `silver_clean` produces."""

    schema = StructType([StructField(name, SILVER_TYPES[name], True) for name in SILVER_COLUMNS])
    return spark.createDataFrame([tuple(row[name] for name in SILVER_COLUMNS) for row in rows], schema)


#: `findings=None` cannot mean "no findings" — null is a value the contract has to allow, and an absent key and an
#: empty list are different messages. A sentinel keeps the builder able to express all three.
DEFAULT_FINDINGS = object()


def envelope(
    *,
    commit_hash: str = "c0ffee",
    parent_hash: str | None = None,
    file_path: str = "src/main/java/com/example/Sample.java",
    content_hash: str = "content-1",
    line_count: int = 100,
    side: str = "AFTER",
    parse_status: str = "OK",
    findings: list[dict] | None | object = DEFAULT_FINDINGS,
    repository_id: int = 1,
    occurred_at: str = "2026-05-04T10:00:00Z",
    event_type: str = "FINDINGS",
    schema_version: int = 1,
    event_id: str = "event-1",
) -> dict:
    """One `analysis.findings` envelope, exactly as the Java producer writes it."""

    return {
        "eventId": event_id,
        "eventType": event_type,
        "schemaVersion": schema_version,
        "occurredAt": occurred_at,
        "correlationId": "corr-1",
        "repositoryId": repository_id,
        "payload": {
            "commitHash": commit_hash,
            "parentHash": parent_hash,
            "filePath": file_path,
            "contentHash": content_hash,
            "lineCount": line_count,
            "side": side,
            "analyzerVersion": ANALYZER_VERSION,
            "rulesetHash": RULESET_HASH,
            "parseStatus": parse_status,
            "findings": [finding()] if findings is DEFAULT_FINDINGS else findings,
        },
    }


def finding(
    *,
    rule_id: str = "EmptyCatchBlock",
    severity: str = "MEDIUM",
    message: str = "Empty catch block",
    start_line: int = 10,
    end_line: int = 12,
    symbol: str = "com.example.Sample.test",
    fingerprint: str = "fingerprint-1",
    snippet: str = "} catch (Exception e) {\n}",
) -> dict:
    """One finding payload."""

    return {
        "ruleId": rule_id,
        "severity": severity,
        "message": message,
        "startLine": start_line,
        "endLine": end_line,
        "symbol": symbol,
        "fingerprint": fingerprint,
        "snippet": snippet,
    }


def events_frame(spark: SparkSession, events: list[dict]) -> DataFrame:
    """Reads events through the real parse path: one JSON document per row → `parse_events` → bronze rows."""

    lines = spark.createDataFrame([(json.dumps(event),) for event in events], "value string")
    return bronze_rows(parse_events(lines))


def write_parquet(df: DataFrame, path: Path) -> str:
    df.write.mode("overwrite").parquet(str(path))
    return str(path)
