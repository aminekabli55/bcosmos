"""Bronze: the wire contract, one row per finding, and one row per empty finding list."""

from __future__ import annotations

from common import schema
from common.schema import bronze_rows, parse_events
from tests.conftest import envelope, events_frame, finding


def test_bronze_columns_are_the_declared_ones(spark):
    rows = events_frame(spark, [envelope()])
    assert tuple(rows.columns) == schema.BRONZE_COLUMNS


def test_one_row_per_finding_and_the_envelope_is_flattened(spark):
    rows = events_frame(
        spark,
        [
            envelope(
                findings=[
                    finding(fingerprint="fp-1", rule_id="EmptyCatchBlock"),
                    finding(fingerprint="fp-2", rule_id="UnusedPrivateField", start_line=20, end_line=21),
                ]
            )
        ],
    )
    collected = {row["fingerprint"]: row for row in rows.collect()}

    assert set(collected) == {"fp-1", "fp-2"}
    assert collected["fp-1"]["repository_id"] == 1
    assert collected["fp-1"]["commit_hash"] == "c0ffee"
    assert collected["fp-1"]["line_count"] == 100
    assert collected["fp-1"]["understood"] is True
    assert collected["fp-1"]["event_date"].isoformat() == "2026-05-04"
    assert collected["fp-1"]["occurred_at"].isoformat().startswith("2026-05-04T10:00:00")


def test_java_instant_precisions_are_all_readable(spark):
    """Java's `Instant.toString()` picks the shortest fraction, so all four widths arrive in a real corpus.

    A pattern-pinned parser read the millisecond form and silently nulled the others, which emptied `occurred_at`
    and `event_date` -- and with them the weekly buckets and the dataset version -- on the first real run.
    """

    events = [
        envelope(event_id="no-fraction", occurred_at="2026-05-04T10:00:00Z"),
        envelope(event_id="millis", occurred_at="2026-05-04T10:00:00.123Z"),
        envelope(event_id="micros", occurred_at="2026-05-04T10:00:00.123456Z"),
        envelope(event_id="nanos", occurred_at="2026-05-04T10:00:00.123456789Z"),
        envelope(event_id="offset", occurred_at="2026-05-04T11:30:00+01:30"),
    ]
    collected = {row["event_id"]: row for row in events_frame(spark, events).collect()}

    assert set(collected) == {"no-fraction", "millis", "micros", "nanos", "offset"}
    for event_id in ("no-fraction", "millis", "micros", "nanos"):
        row = collected[event_id]
        assert row["occurred_at"] is not None, event_id
        assert row["occurred_at"].isoformat().startswith("2026-05-04T10:00:00"), event_id
        assert row["event_date"].isoformat() == "2026-05-04", event_id
    # An offset is the same instant as the UTC form of the same hour: 11:30+01:30 is 10:00Z.
    assert collected["offset"]["occurred_at"].isoformat().startswith("2026-05-04T10:00:00")


def test_a_parse_failure_keeps_its_row_without_findings(spark):
    """The empty finding list must survive: it is the only evidence the file did not parse."""

    rows = events_frame(spark, [envelope(parse_status="FAILED", findings=[])])
    collected = rows.collect()
    assert len(collected) == 1
    assert collected[0]["parse_status"] == "FAILED"
    assert collected[0]["rule_id"] is None
    assert collected[0]["understood"] is True


def test_a_parse_failure_with_null_findings_is_the_same_row(spark):
    rows = events_frame(spark, [envelope(parse_status="FAILED", findings=None)])
    assert rows.count() == 1
    assert rows.collect()[0]["rule_id"] is None


def test_an_unknown_schema_version_is_kept_but_not_understood(spark):
    rows = events_frame(spark, [envelope(schema_version=2)])
    collected = rows.collect()
    assert len(collected) == 1
    assert collected[0]["understood"] is False


def test_another_event_type_is_not_understood(spark):
    """`eventType` is the enum name the producers write; a different one is not a findings event."""

    rows = events_frame(spark, [envelope(event_type="COMMIT_DISCOVERED")])
    assert rows.collect()[0]["understood"] is False


def test_a_broken_message_becomes_a_row_with_nulls(spark):
    lines = spark.createDataFrame([("not json at all",)], "value string")
    rows = bronze_rows(parse_events(lines))
    collected = rows.collect()
    assert len(collected) == 1
    assert collected[0]["repository_id"] is None
    assert collected[0]["understood"] is False


def test_the_declared_schema_matches_the_java_contract():
    """The Python mirror of the Java payload, field by field: this is what the worker's contract test pins."""

    assert [field.name for field in schema.FINDINGS_PAYLOAD_SCHEMA.fields] == [
        "commitHash",
        "parentHash",
        "filePath",
        "contentHash",
        "lineCount",
        "side",
        "analyzerVersion",
        "rulesetHash",
        "parseStatus",
        "findings",
    ]
    assert [field.name for field in schema.FINDING_SCHEMA.fields] == [
        "ruleId",
        "severity",
        "message",
        "startLine",
        "endLine",
        "symbol",
        "fingerprint",
        "snippet",
    ]
    assert [field.name for field in schema.ENVELOPE_SCHEMA.fields] == [
        "eventId",
        "eventType",
        "schemaVersion",
        "occurredAt",
        "correlationId",
        "repositoryId",
        "payload",
    ]
    assert schema.FINDINGS_EVENT_TYPE == "FINDINGS"
