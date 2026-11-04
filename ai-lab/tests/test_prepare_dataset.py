"""pytest tests for ai-lab/finetune/prepare_dataset.py (Phase 7)."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict, List

import pytest

# Ensure the ai-lab root is importable
AI_LAB = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_LAB))

from finetune.prepare_dataset import (
    SYSTEM_PROMPT,
    format_evidence,
    prepare_dataset,
    triple_to_messages,
    triple_to_sft_row,
)


# ── Fixtures ──────────────────────────────────────────────────────────────────

SAMPLE_TRIPLE: Dict[str, Any] = {
    "id": "triple-001",
    "repository_id": 1,
    "repo_name": "apache/commons-lang",
    "file_path": "src/main/java/StrTokenizer.java",
    "rule_id": "EmptyCatchBlock",
    "start_line": 420,
    "before_snippet": "try {\n    return (StrTokenizer) super.clone();\n} catch (CloneNotSupportedException e) {\n    // empty\n}",
    "retrieved_evidence": {
        "enclosing_symbol": "public Object clone()",
        "rule_description": "Empty Catch Block finds instances where an exception is caught, but nothing is done.",
        "callers": ["StrTokenizer.cloneReset()"],
    },
    "ground_truth_diff": "@@ -422,2 +422,2 @@\n-} catch (CloneNotSupportedException e) {\n-    // empty\n+} catch (CloneNotSupportedException e) {\n+    throw new UnsupportedOperationException(e);\n }",
    "expected_fix": "throw new UnsupportedOperationException(e);",
}

MINIMAL_TRIPLE: Dict[str, Any] = {
    "id": "triple-min",
    "rule_id": "UnusedPrivateMethod",
    "ground_truth_diff": "",
    "expected_fix": "",
}


# ── format_evidence ───────────────────────────────────────────────────────────

def test_format_evidence_includes_enclosing_symbol():
    text = format_evidence(SAMPLE_TRIPLE["retrieved_evidence"])
    assert "public Object clone()" in text


def test_format_evidence_includes_rule_description():
    text = format_evidence(SAMPLE_TRIPLE["retrieved_evidence"])
    assert "Empty Catch Block" in text


def test_format_evidence_includes_callers():
    text = format_evidence(SAMPLE_TRIPLE["retrieved_evidence"])
    assert "StrTokenizer.cloneReset()" in text


def test_format_evidence_empty_returns_fallback():
    text = format_evidence({})
    assert "no additional context" in text


# ── triple_to_messages ────────────────────────────────────────────────────────

def test_triple_to_messages_returns_three_roles():
    messages = triple_to_messages(SAMPLE_TRIPLE)
    roles = [m["role"] for m in messages]
    assert roles == ["system", "user", "assistant"]


def test_triple_to_messages_system_is_constant():
    messages = triple_to_messages(SAMPLE_TRIPLE)
    assert messages[0]["content"] == SYSTEM_PROMPT


def test_triple_to_messages_user_contains_rule_id():
    messages = triple_to_messages(SAMPLE_TRIPLE)
    assert "EmptyCatchBlock" in messages[1]["content"]


def test_triple_to_messages_user_contains_before_snippet():
    messages = triple_to_messages(SAMPLE_TRIPLE)
    assert "CloneNotSupportedException" in messages[1]["content"]


def test_triple_to_messages_assistant_is_valid_json():
    messages = triple_to_messages(SAMPLE_TRIPLE)
    parsed = json.loads(messages[2]["content"])
    assert "proposed_diff" in parsed
    assert "summary" in parsed


def test_triple_to_messages_assistant_diff_matches_ground_truth():
    messages = triple_to_messages(SAMPLE_TRIPLE)
    parsed = json.loads(messages[2]["content"])
    assert parsed["proposed_diff"] == SAMPLE_TRIPLE["ground_truth_diff"]


def test_triple_to_messages_minimal_triple_does_not_crash():
    messages = triple_to_messages(MINIMAL_TRIPLE)
    assert len(messages) == 3


# ── triple_to_sft_row ─────────────────────────────────────────────────────────

def test_triple_to_sft_row_has_id_and_messages():
    row = triple_to_sft_row(SAMPLE_TRIPLE)
    assert row["id"] == "triple-001"
    assert isinstance(row["messages"], list)
    assert len(row["messages"]) == 3


def test_triple_to_sft_row_has_rule_id():
    row = triple_to_sft_row(SAMPLE_TRIPLE)
    assert row["rule_id"] == "EmptyCatchBlock"


# ── prepare_dataset ───────────────────────────────────────────────────────────

def test_prepare_dataset_writes_jsonl_files(tmp_path: Path):
    counts = prepare_dataset(
        train_path=None,  # uses bundled fallback
        val_path=None,
        output_dir=tmp_path,
        val_fraction=0.5,
        seed=0,
    )
    train_file = tmp_path / "train.jsonl"
    val_file = tmp_path / "val.jsonl"
    assert train_file.exists(), "train.jsonl not created"
    assert val_file.exists(), "val.jsonl not created"
    assert counts["train"] + counts["val"] > 0


def test_prepare_dataset_train_rows_are_valid_json(tmp_path: Path):
    prepare_dataset(train_path=None, val_path=None, output_dir=tmp_path, seed=1)
    with open(tmp_path / "train.jsonl", "r", encoding="utf-8") as f:
        for line in f:
            row = json.loads(line)
            assert "messages" in row
            assert len(row["messages"]) == 3


def test_prepare_dataset_stratified_by_rule(tmp_path: Path):
    """With 2 triples per rule and val_fraction=0.25, each rule keeps 1 in train."""
    # Build a custom split with 2 items for the same rule
    two_triples = {
        "items": [
            {**SAMPLE_TRIPLE, "id": "t1"},
            {**SAMPLE_TRIPLE, "id": "t2"},
            {**MINIMAL_TRIPLE, "id": "t3", "rule_id": "EmptyCatchBlock"},
            {**MINIMAL_TRIPLE, "id": "t4", "rule_id": "EmptyCatchBlock"},
        ]
    }
    custom_path = tmp_path / "two_per_rule.json"
    custom_path.write_text(json.dumps(two_triples), encoding="utf-8")
    counts = prepare_dataset(
        train_path=custom_path,
        val_path=None,
        output_dir=tmp_path / "out",
        val_fraction=0.25,
        seed=42,
    )
    assert counts["train"] >= 1, f"Expected train >= 1, got {counts}"
    assert counts["val"] >= 1, f"Expected val >= 1, got {counts}"


def test_prepare_dataset_deterministic_split(tmp_path: Path):
    """Same seed → same split."""
    a = tmp_path / "a"
    b = tmp_path / "b"
    counts_a = prepare_dataset(train_path=None, val_path=None, output_dir=a, seed=7)
    counts_b = prepare_dataset(train_path=None, val_path=None, output_dir=b, seed=7)
    assert counts_a == counts_b
    lines_a = (a / "train.jsonl").read_text(encoding="utf-8")
    lines_b = (b / "train.jsonl").read_text(encoding="utf-8")
    assert lines_a == lines_b


def test_prepare_dataset_custom_train_file(tmp_path: Path):
    """Explicit train split path is used instead of the fallback."""
    custom_split = {
        "dataset_version": "v-test",
        "items": [SAMPLE_TRIPLE, MINIMAL_TRIPLE],
    }
    custom_path = tmp_path / "custom_split.json"
    custom_path.write_text(json.dumps(custom_split), encoding="utf-8")

    counts = prepare_dataset(
        train_path=custom_path,
        val_path=None,
        output_dir=tmp_path / "out",
        val_fraction=0.5,
        seed=0,
    )
    assert counts["train"] + counts["val"] == 2
