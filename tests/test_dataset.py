"""Dataset identity: a reproducible version, and a split that cannot leak across repositories."""

from __future__ import annotations

import pytest

from common import dataset
from tests.conftest import silver_frame, silver_row


def test_version_is_derived_from_the_measurement_identity_and_the_last_date(spark):
    silver = silver_frame(spark, [silver_row()])
    version = dataset.dataset_version(silver)

    assert version.startswith("v20260504-")
    assert len(version.split("-")[1]) == 8
    # The same input always gives the same version: the card and the leaderboard depend on it.
    assert dataset.dataset_version(silver) == version


def test_a_different_rule_set_is_a_different_dataset(spark):
    base = dataset.dataset_version(silver_frame(spark, [silver_row()]))
    changed = dataset.dataset_version(silver_frame(spark, [silver_row(ruleset_hash="other-ruleset")]))

    assert base != changed


def test_an_empty_silver_layer_cannot_be_versioned(spark):
    empty = silver_frame(spark, [])
    with pytest.raises(ValueError, match="empty silver layer"):
        dataset.dataset_version(empty)


def test_split_is_a_stable_function_of_the_repository():
    assert dataset.assign_split(1) == dataset.assign_split(1)
    assert dataset.assign_split(42) in dataset.SPLITS


def test_every_split_is_reachable_and_splits_are_by_repository(spark):
    names = {dataset.assign_split(index) for index in range(1, 200)}
    assert names == set(dataset.SPLITS)

    rows = [silver_row(repository_id=3, file_path="A.java"), silver_row(repository_id=3, file_path="B.java",
                                                                       commit_hash="c2")]
    split = {row["split"] for row in dataset.with_split(silver_frame(spark, rows)).collect()}
    assert len(split) == 1  # two files of one repository are always on the same side
