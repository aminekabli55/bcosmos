"""`rule_distribution` and `quality_trends` against hand-computed fixtures.

Every expected number below is written out by hand from the fixture rows, not read back from the job: that is the
only way a fixture test says anything about the calculation.
"""

from __future__ import annotations

from datetime import date, datetime, timezone

from batch.quality_trends import quality_trends
from batch.rule_distribution import rule_distribution
from tests.conftest import silver_frame, silver_row

MONDAY = datetime(2026, 5, 4, 10, 0, tzinfo=timezone.utc)
NEXT_MONDAY = datetime(2026, 5, 11, 10, 0, tzinfo=timezone.utc)


def _fixture(spark):
    """Two repositories, three commits, two rules, and one file analysed twice with different content.

    * repository 1: 100 lines (content A) and 300 lines (content B) of the same file, 2 + 1 findings,
    * repository 2: one file of 200 lines with one finding and one parse failure,
    * the two weeks are 2026-05-04 and 2026-05-11.
    """

    rows = [
        silver_row(repository_id=1, commit_hash="c1", file_path="A.java", content_hash="A", line_count=100,
                   rule_id="EmptyCatchBlock", fingerprint="fp-1", occurred_at=MONDAY),
        silver_row(repository_id=1, commit_hash="c1", file_path="A.java", content_hash="A", line_count=100,
                   rule_id="UnusedPrivateField", fingerprint="fp-2", severity="LOW", occurred_at=MONDAY),
        silver_row(repository_id=1, commit_hash="c2", file_path="A.java", content_hash="B", line_count=300,
                   rule_id="EmptyCatchBlock", fingerprint="fp-3", occurred_at=NEXT_MONDAY),
        silver_row(repository_id=2, commit_hash="d1", file_path="B.java", content_hash="C", line_count=200,
                   rule_id="EmptyCatchBlock", fingerprint="fp-4", occurred_at=MONDAY),
        silver_row(repository_id=2, commit_hash="d1", file_path="B.java", content_hash="C", line_count=200,
                   rule_id=None, severity=None, fingerprint=None, start_line=None, end_line=None, snippet=None,
                   has_finding=False, severity_rank=None, parse_status="FAILED", occurred_at=MONDAY),
    ]
    return silver_frame(spark, rows)


def test_rule_distribution_counts_and_per_kloc(spark):
    """Findings and the KLOC denominator, computed by hand:

    * repository 1: 3 findings over 400 measured lines (100 + 300) → 3 * 1000 / 400 = 7.5 per KLOC,
      `EmptyCatchBlock` alone 2 findings → 5.0 per KLOC,
    * repository 2: 1 finding over 200 lines → 5.0 per KLOC, and one parse failure.
    """

    rows = {
        (row["repository_id"], row["rule_id"]): row for row in rule_distribution(_fixture(spark)).collect()
    }

    empty_catch_1 = rows[(1, "EmptyCatchBlock")]
    assert empty_catch_1["severity"] == "MEDIUM"
    assert empty_catch_1["findings"] == 2
    assert empty_catch_1["files_affected"] == 1
    assert empty_catch_1["measured_lines"] == 400
    assert empty_catch_1["findings_per_kloc"] == 5.0
    assert empty_catch_1["parse_failures"] == 0

    unused_1 = rows[(1, "UnusedPrivateField")]
    assert unused_1["findings"] == 1
    assert unused_1["measured_lines"] == 400
    assert unused_1["findings_per_kloc"] == 2.5

    empty_catch_2 = rows[(2, "EmptyCatchBlock")]
    assert empty_catch_2["findings"] == 1
    assert empty_catch_2["measured_lines"] == 200
    assert empty_catch_2["findings_per_kloc"] == 5.0
    assert empty_catch_2["parse_failures"] == 1


def test_re_analysing_the_same_content_does_not_inflate_the_denominator(spark):
    """The same file content delivered twice is one measurement, not two."""

    rows = silver_frame(
        spark,
        [
            silver_row(repository_id=7, file_path="A.java", content_hash="A", line_count=100, fingerprint="fp-1"),
            silver_row(repository_id=7, file_path="A.java", content_hash="A", line_count=100, fingerprint="fp-1",
                       commit_hash="other", event_id="event-2"),
        ],
    )
    result = rule_distribution(rows).collect()
    assert len(result) == 1
    assert result[0]["measured_lines"] == 100
    assert result[0]["findings"] == 2  # two observations of the same finding at two commits are two findings


def test_no_measured_lines_means_a_null_density_not_a_zero(spark):
    rows = silver_frame(
        spark,
        [silver_row(repository_id=9, line_count=0, fingerprint="fp-1")],
    )
    result = rule_distribution(rows).collect()
    assert result[0]["measured_lines"] == 0
    assert result[0]["findings_per_kloc"] is None


def test_quality_trends_bucket_by_utc_week(spark):
    """Repository 1 has 2 findings in the 2026-05-04 week and 1 in the 2026-05-11 week."""

    rows = {
        (row["repository_id"], row["bucket_start"]): row for row in quality_trends(_fixture(spark)).collect()
    }

    first_week = rows[(1, date(2026, 5, 4))]
    assert first_week["findings"] == 2
    assert first_week["measured_lines"] == 100
    assert first_week["density_per_kloc"] == 20.0
    assert first_week["parse_failures"] == 0

    second_week = rows[(1, date(2026, 5, 11))]
    assert second_week["findings"] == 1
    assert second_week["measured_lines"] == 300
    assert second_week["density_per_kloc"] == round(1000 / 300, 4)

    repository_two = rows[(2, date(2026, 5, 4))]
    assert repository_two["findings"] == 1
    assert repository_two["parse_failures"] == 1
    assert repository_two["files_analysed"] == 1
