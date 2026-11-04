"""The dataset card is generated: these tests pin what it must say, including its limitations."""

from __future__ import annotations

from batch.load_gold import build_summary
from common.card import KNOWN_LIMITATIONS, render_dataset_card


def _summary() -> dict:
    """A hand-written summary, with `repositories` as the list of rows the renderer prints."""

    return {
        "dataset_version": "v20260504-ab12cd34",
        "generated_at": "2026-05-04T12:00:00+00:00",
        "triples": 7,
        "rules": 3,
        "splits": [
            {"split": "train", "triples": 5, "repositories": 1, "rules": 2, "snippet_changed": 4,
             "introductions_unobserved": 1, "max_changed_lines": 30},
            {"split": "test", "triples": 2, "repositories": 1, "rules": 1, "snippet_changed": 2,
             "introductions_unobserved": 0, "max_changed_lines": 30},
        ],
        "repositories_without_license": 1,
        "rules_covered": [{"rule_id": "EmptyCatchBlock", "triples": 5}],
        "max_changed_lines": 30,
        "snippet_changed": 6,
        "introductions_unobserved": 1,
        "drilldown_commits": 50,
        "pipeline_stats": {"silver_rows": 120, "rejected_rows": 4, "rejected_unsupported_schema": 4},
        "repositories": [
            {"split": "train", "repository_id": 1, "repository_name": "demo", "license_spdx": "Apache-2.0",
             "triples": 5},
            {"split": "test", "repository_id": 2, "repository_name": "other", "license_spdx": None, "triples": 2},
        ],
    }


def test_the_card_reports_the_loaded_numbers():
    card = render_dataset_card(_summary())

    assert "`v20260504-ab12cd34`" in card
    assert "| Triples | 7 |" in card
    assert "| Repositories | 2 |" in card
    assert "| Rules covered | 3 |" in card
    assert "| train | 5 | 1 | 2 | 4 | 1 |" in card
    assert "Apache-2.0" in card
    assert "Licence unknown for **1** of 2 repositories." in card
    assert "| silver_rows | 120 |" in card
    assert "| rejected_unsupported_schema | 4 |" in card
    assert "max changed lines: `30`" in card


def test_the_card_states_every_known_limitation():
    card = render_dataset_card(_summary())
    for limitation in KNOWN_LIMITATIONS:
        assert limitation in card
    assert "Snippets, not files" in card
    assert "Commits, not days" in card


def test_the_loader_summary_is_what_the_renderer_reads(spark):
    """The card is rendered from `load_gold.build_summary`'s own output, not from a look-alike.

    A hand-made summary once carried `repositories` as a list of rows while the loader carried an int; every card
    test passed and the first real load crashed with `'int' object is not iterable`. This builds the summary with the
    loader's function and renders it, so a shape change on either side fails here.
    """

    splits = spark.createDataFrame(
        [("train", 7, 1, 3, 6, 1, 30)],
        "split string, triples long, repositories int, rules int, snippet_changed long, "
        "introductions_unobserved long, max_changed_lines int",
    )
    repositories = spark.createDataFrame(
        [("train", 1, "demo", "Apache-2.0", 7)],
        "split string, repository_id long, repository_name string, license_spdx string, triples long",
    )
    triple_rows = spark.createDataFrame([("EmptyCatchBlock",)], "rule_id string")

    summary = build_summary(
        "v20260504-ab12cd34",
        "2026-05-04T12:00:00+00:00",
        {"agg_dataset": splits, "agg_dataset_repository": repositories},
        drilldown_commits=50,
        pipeline_stats={"silver_rows": 120},
        triple_rows=triple_rows,
    )
    card = render_dataset_card(summary)

    assert "| Triples | 7 |" in card
    assert "| Repositories | 1 |" in card
    assert "| train | demo | Apache-2.0 | 7 |" in card
    assert "Licence unknown for **0** of 1 repositories." in card
    assert "| EmptyCatchBlock | 1 |" in card
    assert "| silver_rows | 120 |" in card


def test_an_empty_dataset_still_renders():
    summary = _summary()
    summary.update({"triples": 0, "splits": [], "repositories": [], "rules_covered": [], "pipelines_stats": {}})

    card = render_dataset_card(summary)

    assert "| Triples | 0 |" in card
    assert "_None._" in card
    assert "| Splits | none |" in card
