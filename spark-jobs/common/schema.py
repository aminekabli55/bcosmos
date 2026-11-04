"""The wire contract of BCosmos as Spark sees it (spec v2 section 5.2).

The envelope and payload schemas below are the *Python* mirror of the Java records in
`analysis-core/src/main/java/com/bcosmos/core/events`. They are declared explicitly rather than inferred, for three
reasons:

1. A schema that is inferred from one event silently changes shape when a producer changes; an explicit schema makes
   that a test failure instead of a wrong number in a chart.
2. `from_json` returns `null` for a field that is missing, so the explicit schema is what turns "absent" into a
   visible `null` that the silver job can count instead of a column that quietly does not exist.
3. The dataset card and every chart have to name the schema version they were built from (`schemaVersion`).

Adding a field to the Java record means adding it here **and** to `SILVER_REQUIRED_COLUMNS`/the tests that pin the
contract. `tests/test_bronze_rows.py` fails if the two drift apart.
"""

from __future__ import annotations

from pyspark.sql import Column, DataFrame, functions as F
from pyspark.sql.types import (
    ArrayType,
    IntegerType,
    LongType,
    StringType,
    StructField,
    StructType,
)

#: The `schemaVersion` this package understands. A newer producer must bump `EventEnvelope.SCHEMA_VERSION` and this
#: constant together; bronze keeps the row, silver counts it as `unsupported_schema`.
UNDERSTOOD_SCHEMA_VERSION = 1

#: The wire value of `EventType.FINDINGS`.
#:
#: Spec v2 section 5.2 shows this field as `"analysis.findings"`, but the producers write the Java enum name, because
#: `EventType` is serialized by Jackson's default (the enum name) and `analysis-core` deliberately has no Jackson
#: dependency to annotate it with. Rather than add a dependency to the shared module for a cosmetic string, the
#: contract is pinned to what the producers actually write: this constant, the Java test
#: `EventTypeWireFormatTest`, and the docs all say `FINDINGS`. A producer that changes it must change all three, and
#: until then this job counts the row as `unsupported_schema` rather than charting it as a finding.
FINDINGS_EVENT_TYPE = "FINDINGS"

#: One finding (spec v2 section 5.2, `FindingPayload`).
FINDING_SCHEMA = StructType(
    [
        StructField("ruleId", StringType()),
        StructField("severity", StringType()),
        StructField("message", StringType()),
        StructField("startLine", IntegerType()),
        StructField("endLine", IntegerType()),
        StructField("symbol", StringType()),
        StructField("fingerprint", StringType()),
        StructField("snippet", StringType()),
    ]
)

#: The `analysis.findings` payload (spec v2 section 5.2, `FindingsPayload`).
FINDINGS_PAYLOAD_SCHEMA = StructType(
    [
        StructField("commitHash", StringType()),
        StructField("parentHash", StringType()),
        StructField("filePath", StringType()),
        StructField("contentHash", StringType()),
        # The denominator of every per-KLOC figure (spec v2 section 6.1). 0 means "not measured", never "empty file".
        StructField("lineCount", IntegerType()),
        StructField("side", StringType()),
        StructField("analyzerVersion", StringType()),
        StructField("rulesetHash", StringType()),
        StructField("parseStatus", StringType()),
        StructField("findings", ArrayType(FINDING_SCHEMA)),
    ]
)

#: The event envelope (spec v2 section 5.2, `EventEnvelope`).
ENVELOPE_SCHEMA = StructType(
    [
        StructField("eventId", StringType()),
        StructField("eventType", StringType()),
        StructField("schemaVersion", IntegerType()),
        StructField("occurredAt", StringType()),
        StructField("correlationId", StringType()),
        StructField("repositoryId", LongType()),
        StructField("payload", FINDINGS_PAYLOAD_SCHEMA),
    ]
)

#: The flat shape bronze stores: one row per (event, finding), plus one row with null finding columns for an event
#: whose finding list is empty (a file that failed to parse still has to be counted).
#:
#: `understood` is not part of the wire contract: bronze records every delivered event and marks the ones this
#: package understands, so an unknown `schemaVersion` is visible data rather than a dropped message.
BRONZE_COLUMNS: tuple[str, ...] = (
    "repository_id",
    "event_id",
    "event_type",
    "schema_version",
    "occurred_at",
    "event_date",
    "correlation_id",
    "commit_hash",
    "parent_hash",
    "file_path",
    "content_hash",
    "line_count",
    "side",
    "analyzer_version",
    "ruleset_hash",
    "parse_status",
    "rule_id",
    "severity",
    "message",
    "start_line",
    "end_line",
    "symbol",
    "fingerprint",
    "snippet",
    "understood",
    "ingested_at",
)

#: Bronze is partitioned by repository and by the UTC day the event was received, so a re-run can rewrite one day of
#: one repository and a query can prune (spec v2 section 6.1).
BRONZE_PARTITION_COLUMNS: tuple[str, ...] = ("repository_id", "event_date")

#: The sides a payload can declare.
SIDES: tuple[str, ...] = ("BEFORE", "AFTER")

#: The severity scale of spec v2 section 4.3, in the order the normalizer emits.
SEVERITIES: tuple[str, ...] = ("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")

#: `parseStatus` values of spec v2 section 5.2.
PARSE_STATUSES: tuple[str, ...] = ("OK", "FAILED")


def parse_instant(column: Column) -> Column:
    """ISO-8601 text (what the Java producers write) as a timestamp, in UTC.

    Deliberately **without** an explicit pattern: Java's `Instant.toString()` emits the shortest fraction that keeps
    the value, so a producer on one machine writes `...:24Z`, on another `...:24.123Z`, and on a third
    `...:24.123456789Z`. A pattern can only pin one width -- `[.SSS]` parsed the millisecond form and returned null
    for the microsecond and nanosecond ones, which silently emptied `occurred_at` and `event_date` (and with them
    every per-week bucket and the dataset version) as soon as a real producer rather than a fixture wrote the
    events. Spark's pattern-free parser reads the whole ISO-8601 family, converts a `+01:00` offset to UTC, and
    returns null (ANSI is off, see `sessions.build_session`) for a value it cannot read, which is the `null` silver
    counts.

    `tests/test_bronze.py::test_java_instant_precisions_are_all_readable` pins one value per width.
    """

    return F.to_timestamp(column)


def parse_events(source: DataFrame, value_column: str = "value") -> DataFrame:
    """Parses the raw envelope JSON into the envelope columns plus the payload columns.

    `source` is either the `value` column of a Kafka stream or the `value` column of a text file read; both are bytes
    or text, which is why the cast happens here. A message that is not valid JSON, or whose `payload` is not a
    findings payload, yields nulls rather than an exception: bronze keeps it and silver counts it.
    """

    envelope = F.from_json(F.col(value_column).cast("string"), ENVELOPE_SCHEMA)
    occurred_at = parse_instant(envelope.getField("occurredAt"))
    return source.select(
        envelope.getField("schemaVersion").alias("schema_version"),
        envelope.getField("eventId").alias("event_id"),
        envelope.getField("eventType").alias("event_type"),
        occurred_at.alias("occurred_at"),
        F.to_date(occurred_at).alias("event_date"),
        envelope.getField("correlationId").alias("correlation_id"),
        envelope.getField("repositoryId").alias("repository_id"),
        # `understood` is decided here, while the payload still exists: the explode below replaces it with one row
        # per finding, and "a payload parsed at all" is a property of the event, not of a finding row.
        envelope.getField("payload").alias("payload"),
        _understood_condition(envelope).alias("understood"),
    )


def bronze_rows(parsed: DataFrame) -> DataFrame:
    """Flattens parsed events to the `BRONZE_COLUMNS` grain: one row per finding, one row per empty finding list.

    `explode_outer` is what makes a failed parse visible. It keeps the event when `findings` is an empty array or
    null, and then every finding column is null while `parse_status` still says `FAILED`. A plain `explode` would drop
    exactly the rows the parse-failure rate is computed from.
    """

    exploded = parsed.select(
        "repository_id",
        "event_id",
        "event_type",
        "schema_version",
        "occurred_at",
        "event_date",
        "correlation_id",
        "understood",
        F.col("payload.commitHash").alias("commit_hash"),
        F.col("payload.parentHash").alias("parent_hash"),
        F.col("payload.filePath").alias("file_path"),
        F.col("payload.contentHash").alias("content_hash"),
        F.col("payload.lineCount").alias("line_count"),
        F.col("payload.side").alias("side"),
        F.col("payload.analyzerVersion").alias("analyzer_version"),
        F.col("payload.rulesetHash").alias("ruleset_hash"),
        F.col("payload.parseStatus").alias("parse_status"),
        F.explode_outer("payload.findings").alias("finding"),
    )

    return exploded.select(
        F.col("repository_id").cast("long").alias("repository_id"),
        "event_id",
        "event_type",
        "schema_version",
        "occurred_at",
        "event_date",
        "correlation_id",
        "commit_hash",
        "parent_hash",
        "file_path",
        "content_hash",
        F.col("line_count").cast("int").alias("line_count"),
        "side",
        "analyzer_version",
        "ruleset_hash",
        "parse_status",
        F.col("finding.ruleId").alias("rule_id"),
        F.col("finding.severity").alias("severity"),
        F.col("finding.message").alias("message"),
        F.col("finding.startLine").cast("int").alias("start_line"),
        F.col("finding.endLine").cast("int").alias("end_line"),
        F.col("finding.symbol").alias("symbol"),
        F.col("finding.fingerprint").alias("fingerprint"),
        F.col("finding.snippet").alias("snippet"),
        "understood",
        F.current_timestamp().alias("ingested_at"),
    )


def _understood_condition(envelope: Column) -> Column:
    """Whether this row is an `analysis.findings.v1` event at all, i.e. whether an envelope was read.

    Deliberately limited to the envelope: the version, the event type, the repository, the revision and a payload that
    parsed. A value inside the payload (`side`, `parse_status`, a missing file path) is **silver's** business, and
    folding those checks in here would report every malformed value as "unsupported schema" — the one diagnosis that
    tells an operator to look at the producer instead of at the message.

    The checks run against the parsed envelope itself, not against the flattened columns: this is evaluated in the
    same projection that creates those columns.
    """

    payload = envelope.getField("payload")
    return (
        (envelope.getField("schemaVersion") == F.lit(UNDERSTOOD_SCHEMA_VERSION))
        & (envelope.getField("eventType") == F.lit(FINDINGS_EVENT_TYPE))
        & payload.isNotNull()
        & envelope.getField("repositoryId").isNotNull()
        & payload.getField("commitHash").isNotNull()
    )
