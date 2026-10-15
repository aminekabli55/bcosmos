"""Hotspot scores with hand-computed percentile ranks and contributions.

Fixture: one repository, three files, with deliberately distinct values so `percent_rank` is exactly 0, 0.5 and 1.

| file | commits (churn) | distinct rules (complexity proxy) | findings in the latest revision |
|---|---|---|---|
| `A.java` | 1 | 1 | 1 |
| `B.java` | 2 | 2 | 2 |
| `C.java` | 3 | 3 | 3 |

With the default weights (complexity 0.25, churn 0.35, open findings 0.40, dependency 0) the measured weight sum is
1.0, so each score equals the common percentile rank: 0, 0.5, 1.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from batch.hotspots import hotspots
from tests.conftest import silver_frame, silver_row

START = datetime(2026, 5, 4, 10, 0, tzinfo=timezone.utc)


def _fixture(spark):
    rows = []
    plan = {
        "A.java": [("a1", None, ["R1"])],
        "B.java": [("b1", None, ["R1"]), ("b2", "b1", ["R1", "R2"])],
        "C.java": [("c1", None, ["R1"]), ("c2", "c1", ["R1", "R2"]), ("c3", "c2", ["R1", "R2", "R3"])],
    }
    moment = START
    for file_path, revisions in plan.items():
        for commit_hash, parent_hash, rules in revisions:
            moment += timedelta(hours=1)
            for index, rule in enumerate(rules):
                rows.append(
                    silver_row(
                        repository_id=1,
                        file_path=file_path,
                        commit_hash=commit_hash,
                        parent_hash=parent_hash,
                        content_hash=f"{commit_hash}-content",
                        rule_id=rule,
                        fingerprint=f"{commit_hash}-fp-{index}",
                        occurred_at=moment,
                    )
                )
    return silver_frame(spark, rows)


def test_scores_ranks_and_contributions(spark):
    result = {row["file_path"]: row for row in hotspots(_fixture(spark)).collect()}

    assert set(result) == {"A.java", "B.java", "C.java"}

    assert result["A.java"]["churn"] == 1
    assert result["B.java"]["churn"] == 2
    assert result["C.java"]["churn"] == 3

    assert result["A.java"]["complexity_proxy"] == 1
    assert result["B.java"]["complexity_proxy"] == 2
    assert result["C.java"]["complexity_proxy"] == 3

    # Only the findings of the deepest observed revision count as open.
    assert result["A.java"]["open_findings"] == 1
    assert result["B.java"]["open_findings"] == 2
    assert result["C.java"]["open_findings"] == 3

    assert result["A.java"]["churn_score"] == 0.0
    assert result["B.java"]["churn_score"] == 0.5
    assert result["C.java"]["churn_score"] == 1.0
    assert result["C.java"]["complexity_score"] == 1.0
    assert result["C.java"]["open_findings_score"] == 1.0

    assert result["C.java"]["churn_contribution"] == 0.35
    assert result["C.java"]["complexity_contribution"] == 0.25
    assert result["C.java"]["open_findings_contribution"] == 0.40

    assert result["A.java"]["score"] == 0.0
    assert result["B.java"]["score"] == 0.5
    assert result["C.java"]["score"] == 1.0

    assert result["C.java"]["rank_in_repository"] == 1
    assert result["B.java"]["rank_in_repository"] == 2
    assert result["A.java"]["rank_in_repository"] == 3

    assert result["C.java"]["latest_commit_hash"] == "c3"
    assert result["B.java"]["latest_commit_hash"] == "b2"


def test_dependency_impact_is_reported_as_unmeasured(spark):
    result = hotspots(_fixture(spark)).collect()
    assert all(row["dependency_measured"] is False for row in result)
    assert all(row["weight_sum_measured"] == 1.0 for row in result)
    assert all("dependency=0.0" in row["weights"] for row in result)


def test_a_zero_weight_component_does_not_change_the_ranking(spark):
    """Dropping complexity renormalizes the score instead of shrinking it."""

    weights = {"complexity": 0.0, "churn": 0.5, "dependency": 0.0, "open_findings": 0.5}
    result = {row["file_path"]: row for row in hotspots(_fixture(spark), weights=weights).collect()}

    assert result["C.java"]["weight_sum_measured"] == 1.0
    assert result["C.java"]["complexity_contribution"] == 0.0
    assert result["C.java"]["score"] == 1.0
    assert result["A.java"]["score"] == 0.0


def test_ranking_is_per_repository(spark):
    """A file is risky relative to its own repository, so both repositories have a top-ranked file."""

    rows = list(_fixture(spark).collect())
    other = silver_row(repository_id=2, file_path="Z.java", commit_hash="z1", parent_hash=None,
                       content_hash="z1-content", rule_id="R1", fingerprint="z1-fp")
    frame = silver_frame(spark, rows + [other])

    result = {(row["repository_id"], row["file_path"]): row for row in hotspots(frame).collect()}
    assert result[(1, "C.java")]["rank_in_repository"] == 1
    assert result[(2, "Z.java")]["rank_in_repository"] == 1
    assert result[(2, "Z.java")]["score"] == 0.0
