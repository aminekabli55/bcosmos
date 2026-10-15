"""Silver: what is rejected, what is deduplicated, and what a redelivery must not do."""

from __future__ import annotations

from batch.silver_clean import REJECTION_REASONS, SILVER_COLUMNS, clean, deduplicate, rejections_by_reason
from tests.conftest import envelope, events_frame, finding


def _clean(rows):
    silver, rejected = clean(rows)
    return silver.collect(), rejected


def test_a_valid_batch_keeps_every_row_and_rejects_nothing(spark):
    rows = events_frame(spark, [envelope()])
    silver, rejected = _clean(rows)

    assert rejected.count() == 0
    assert tuple(silver[0].asDict().keys()) == SILVER_COLUMNS
    assert silver[0]["has_finding"] is True
    assert silver[0]["severity_rank"] == 3  # MEDIUM in the CRITICAL > ... > INFO scale


def test_a_redelivery_of_the_same_event_becomes_one_row(spark):
    """At-least-once delivery: the same event twice is one finding, not two."""

    event = envelope()
    rows = events_frame(spark, [event, event])
    silver, rejected = _clean(rows)

    assert len(silver) == 1
    assert rejected.count() == 0


def test_two_deliveries_keep_the_most_recently_ingested_row(spark):
    rows = events_frame(spark, [envelope(), envelope()])
    newest = deduplicate(rows).orderBy(rows["ingested_at"].desc()).collect()
    assert len(newest) == 1
    assert newest[0]["fingerprint"] == "fingerprint-1"


def test_unsupported_schema_is_rejected_with_its_reason(spark):
    rows = events_frame(spark, [envelope(schema_version=99)])
    silver, rejected = _clean(rows)

    assert silver == []
    reasons = {row["rejection_reason"] for row in rejected.collect()}
    assert reasons == {"unsupported_schema"}


def test_a_finding_without_a_severity_is_rejected(spark):
    rows = events_frame(spark, [envelope(findings=[finding(severity="BLOCKER")])])
    _, rejected = _clean(rows)
    assert rejected.collect()[0]["rejection_reason"] == "invalid_severity"


def test_a_finding_with_a_backwards_line_range_is_rejected(spark):
    rows = events_frame(spark, [envelope(findings=[finding(start_line=20, end_line=10)])])
    _, rejected = _clean(rows)
    assert rejected.collect()[0]["rejection_reason"] == "invalid_line_range"


def test_a_finding_without_a_fingerprint_is_rejected(spark):
    rows = events_frame(spark, [envelope(findings=[finding(fingerprint=None)])])
    _, rejected = _clean(rows)
    assert rejected.collect()[0]["rejection_reason"] == "missing_fingerprint"


def test_an_unknown_side_is_rejected(spark):
    rows = events_frame(spark, [envelope(side="SIDEWAYS")])
    _, rejected = _clean(rows)
    assert rejected.collect()[0]["rejection_reason"] == "invalid_side"


def test_an_unknown_parse_status_is_rejected(spark):
    rows = events_frame(spark, [envelope(parse_status="MAYBE")])
    _, rejected = _clean(rows)
    assert rejected.collect()[0]["rejection_reason"] == "invalid_parse_status"


def test_a_missing_required_field_is_rejected(spark):
    event = envelope()
    event["payload"]["filePath"] = None  # the field has to exist; its value may not be empty
    rows = events_frame(spark, [event])
    _, rejected = _clean(rows)
    assert rejected.collect()[0]["rejection_reason"] == "missing_required_field"


def test_a_failed_parse_with_no_findings_is_kept_not_rejected(spark):
    rows = events_frame(spark, [envelope(parse_status="FAILED", findings=[])])
    silver, rejected = _clean(rows)

    assert rejected.count() == 0
    assert len(silver) == 1
    assert silver[0]["has_finding"] is False
    assert silver[0]["parse_status"] == "FAILED"


def test_every_rejection_reason_is_reachable(spark):
    """The reason list is the documentation: every entry has to be produced by a real input somewhere in this file."""

    assert set(REJECTION_REASONS) == {
        "unsupported_schema",
        "missing_required_field",
        "invalid_side",
        "invalid_parse_status",
        "invalid_severity",
        "invalid_line_range",
        "missing_fingerprint",
    }


def test_rejections_are_counted_per_reason(spark):
    rows = events_frame(
        spark,
        [
            envelope(schema_version=99),
            envelope(findings=[finding(severity="BLOCKER")]),
            envelope(schema_version=99),
        ],
    )
    _, rejected = _clean(rows)
    counts = {row["rejection_reason"]: row["rows"] for row in rejections_by_reason(rejected).collect()}
    assert counts == {"unsupported_schema": 2, "invalid_severity": 1}
