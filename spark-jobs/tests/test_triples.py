"""The fix-triple rules and the first-parent distances, with hand-computed expectations.

The fixture is a three-commit chain of one file:

| revision | content | finding `fp-1` (`EmptyCatchBlock`) |
|---|---|---|
| `r0` (root) | `v0` | absent |
| `r1` | `v1` | present |
| `r2` | `v2` | absent |

so `fp-1` is introduced at depth 1 and resolved at depth 2, i.e. `commits_until_resolved = 1`, and the introduction
is observable because the root of the window (`r0`) did not have it.
"""

from __future__ import annotations

from batch.time_to_fix import time_to_fix
from common import triples
from tests.conftest import silver_frame, silver_row

CATCH_SNIPPET = "} catch (Exception e) {\n}"
NEW_RULE_SNIPPET = "private int unused;"


def _chain(spark, overrides: dict[int, dict] | None = None):
    """The five events of the fixture above; `overrides` replaces individual rows by index."""

    rows = [
        silver_row(side="AFTER", commit_hash="r0", parent_hash=None, content_hash="v0", rule_id=None,
                   fingerprint=None, snippet=None, has_finding=False, severity=None, start_line=None, end_line=None),
        silver_row(side="AFTER", commit_hash="r1", parent_hash="r0", content_hash="v1", fingerprint="fp-1",
                   snippet=CATCH_SNIPPET, start_line=10, end_line=11),
        silver_row(side="BEFORE", commit_hash="r0", parent_hash=None, content_hash="v0", rule_id=None,
                   fingerprint=None, snippet=None, has_finding=False, severity=None, start_line=None, end_line=None),
        silver_row(side="AFTER", commit_hash="r2", parent_hash="r1", content_hash="v2", rule_id=None,
                   fingerprint=None, snippet=None, has_finding=False, severity=None, start_line=None, end_line=None),
        silver_row(side="BEFORE", commit_hash="r1", parent_hash=None, content_hash="v1", fingerprint="fp-1",
                   snippet=CATCH_SNIPPET, start_line=10, end_line=11),
    ]
    for index, replacement in (overrides or {}).items():
        rows[index] = replacement
    return silver_frame(spark, rows)


def test_first_parent_depth_is_the_distance_to_the_observed_root(spark):
    rows = silver_frame(
        spark,
        [
            silver_row(side="AFTER", commit_hash="c3", parent_hash="c2", fingerprint="fp"),
            silver_row(side="AFTER", commit_hash="c2", parent_hash="c1", fingerprint="fp"),
            silver_row(side="AFTER", commit_hash="c1", parent_hash=None, fingerprint="fp"),
        ],
    )
    depths = {
        row["commit_hash"]: row["depth"]
        for row in triples.first_parent_depth(triples.first_parent_edges(rows)).collect()
    }
    assert depths == {"c1": 0, "c2": 1, "c3": 2}


def test_a_resolved_finding_becomes_one_triple(spark):
    result = triples.fix_triples(_chain(spark)).collect()

    assert len(result) == 1
    triple = result[0]
    assert triple["repository_id"] == 1
    assert triple["file_path"] == "src/main/java/com/example/Sample.java"
    assert triple["commit_hash"] == "r2"
    assert triple["parent_hash"] == "r1"
    assert triple["rule_id"] == "EmptyCatchBlock"
    assert triple["fingerprint"] == "fp-1"
    assert triple["before_snippet"] == CATCH_SNIPPET
    # The finding's symbol is gone from the later revision, so the "after code" is empty and the diff is a deletion
    # of the two snippet lines.
    assert triple["after_snippet"] is None
    assert triple["changed_lines"] == 2
    assert triple["snippet_changed"] is True
    assert triple["commits_until_resolved"] == 1
    assert triple["introduction_observed"] is True
    assert "-} catch (Exception e) {" in triple["diff"]


def test_a_finding_that_is_still_there_is_not_a_triple(spark):
    """`r2` still reports `fp-1`: nothing was fixed."""

    rows = _chain(
        spark,
        {
            3: silver_row(side="AFTER", commit_hash="r2", parent_hash="r1", content_hash="v2", fingerprint="fp-1",
                          snippet=CATCH_SNIPPET, start_line=10, end_line=11),
        },
    )
    assert triples.fix_triples(rows).collect() == []


def test_a_new_finding_of_the_same_rule_blocks_the_triple(spark):
    """The change traded one `EmptyCatchBlock` for another: spec v2 section 6.2 excludes it."""

    rows = _chain(
        spark,
        {
            3: silver_row(side="AFTER", commit_hash="r2", parent_hash="r1", content_hash="v2", fingerprint="fp-9",
                          snippet=CATCH_SNIPPET, start_line=40, end_line=41),
        },
    )
    assert triples.fix_triples(rows).collect() == []


def test_an_unchanged_file_is_not_a_triple(spark):
    """Same content hash on both sides: the file did not change, whatever the findings say."""

    rows = _chain(spark, {3: silver_row(side="AFTER", commit_hash="r2", parent_hash="r1", content_hash="v1",
                                        rule_id=None, fingerprint=None, snippet=None, has_finding=False,
                                        severity=None, start_line=None, end_line=None)})
    assert triples.fix_triples(rows).collect() == []


def test_a_parse_failure_on_either_side_is_not_a_triple(spark):
    rows = _chain(spark, {4: silver_row(side="BEFORE", commit_hash="r1", content_hash="v1", fingerprint="fp-1",
                                        snippet=CATCH_SNIPPET, parse_status="FAILED")})
    assert triples.fix_triples(rows).collect() == []


def test_a_change_larger_than_the_limit_is_not_a_triple(spark):
    """The one-line default makes the two-line deletion too large; the same pair passes at three."""

    assert triples.fix_triples(_chain(spark), max_changed_lines=1).collect() == []
    assert len(triples.fix_triples(_chain(spark), max_changed_lines=3).collect()) == 1


def test_the_after_side_uses_the_same_symbols_snippet(spark):
    """When the symbol survives the fix, the triple pairs its two snippets and counts the changed line."""

    rows = _chain(
        spark,
        {
            3: silver_row(side="AFTER", commit_hash="r2", parent_hash="r1", content_hash="v2", rule_id="UnusedImports",
                          fingerprint="fp-other", snippet="private int unused;", symbol="com.example.Sample.test"),
        },
    )
    result = triples.fix_triples(rows).collect()

    assert len(result) == 1
    assert result[0]["after_snippet"] == "private int unused;"
    # Both snippet lines are removed and one is added: `changed_lines` counts removed plus added lines.
    assert result[0]["changed_lines"] == 3
    assert "+private int unused;" in result[0]["diff"]
    assert "-} catch (Exception e) {" in result[0]["diff"]


def test_snippet_diff_counts_removals_and_additions():
    diff, changed = triples.snippet_diff("a\nb", None)
    assert changed == 2
    assert "-a" in diff

    diff, changed = triples.snippet_diff("a\nb", "a\nb")
    assert (diff, changed) == ("", 0)

    diff, changed = triples.snippet_diff("a\nb\nc", "a\nC")
    assert changed == 3
    assert "+C" in diff


def test_time_to_fix_is_a_histogram_plus_the_unobserved_row(spark):
    result = {(row["rule_id"], row["commits_until_resolved"]): row for row in time_to_fix(_chain(spark)).collect()}

    assert set(result) == {("EmptyCatchBlock", 1)}
    histogram_row = result[("EmptyCatchBlock", 1)]
    assert histogram_row["resolved_pairs"] == 1
    assert histogram_row["observed_introductions"] == 1


def test_an_introduction_at_the_root_is_flagged_as_a_lower_bound(spark):
    """`fp-1` already exists at the root of the window, so the distance is a lower bound, flagged and counted."""

    rows = _chain(
        spark,
        {
            0: silver_row(side="AFTER", commit_hash="r0", parent_hash=None, content_hash="v0", fingerprint="fp-1",
                          snippet=CATCH_SNIPPET),
        },
    )
    triples_rows = triples.fix_triples(rows).collect()
    assert triples_rows[0]["commits_until_resolved"] == 2
    assert triples_rows[0]["introduction_observed"] is False

    result = time_to_fix(rows).collect()
    assert len(result) == 1
    assert result[0]["commits_until_resolved"] == 2
    assert result[0]["resolved_pairs"] == 1
    # The flag is what tells a chart to leave this row out of a median.
    assert result[0]["observed_introductions"] == 0
