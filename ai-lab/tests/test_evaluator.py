"""Tests for evaluation harness metric calculation and condition ranking."""

from __future__ import annotations

import pytest
from eval.evaluator import (
    apply_patch,
    compute_condition_metrics,
    evaluate_sample_completion,
    rank_conditions,
    EvaluationSampleResult,
)


def test_apply_patch_success():
    source = "line 1\nline 2\nline 3\n"
    diff = "@@ -2,1 +2,1 @@\n-line 2\n+line 2 modified\n"
    patched = apply_patch(source, diff)
    assert patched is not None
    assert "line 2 modified" in patched
    assert "line 1" in patched
    assert "line 3" in patched


def test_evaluate_sample_completion_valid_fix():
    sample = {
        "id": "t-1",
        "rule_id": "EmptyCatchBlock",
        "before_snippet": "try {\n    doSomething();\n} catch (Exception e) {\n    // empty\n}\n",
        "expected_fix": "throw new RuntimeException(e);"
    }
    completion = {
        "summary": "Fix empty catch block",
        "proposed_diff": "@@ -3,2 +3,2 @@\n-} catch (Exception e) {\n-    // empty\n+} catch (Exception e) {\n+    throw new RuntimeException(e);\n }\n",
        "latency_ms": 1100.0,
        "tokens": 250,
        "cost_usd": 0.0002
    }

    result = evaluate_sample_completion(sample, completion)
    assert result.schema_valid is True
    assert result.diff_applies is True
    assert result.parses is True
    assert result.target_resolved is True
    assert result.no_new_findings is True
    assert result.diff_size > 0


def test_evaluate_sample_completion_invalid_schema():
    sample = {"id": "t-2", "rule_id": "EmptyCatchBlock"}
    completion = {"invalid": "missing proposed_diff"}

    result = evaluate_sample_completion(sample, completion)
    assert result.schema_valid is False
    assert result.target_resolved is False


def test_compute_condition_metrics():
    results = [
        EvaluationSampleResult("1", "EmptyCatchBlock", True, True, True, True, True, 4, 1000.0, 300, 0.001),
        EvaluationSampleResult("2", "EmptyCatchBlock", True, True, True, False, False, 2, 1200.0, 320, 0.001),
        EvaluationSampleResult("3", "UnusedPrivateMethod", True, True, True, True, True, 6, 800.0, 280, 0.001),
        EvaluationSampleResult("4", "UnusedPrivateMethod", True, False, False, False, False, 0, 900.0, 200, 0.001),
    ]

    metrics = compute_condition_metrics("Test Condition", "model-a", "v1", "RAG", results)
    assert metrics.sample_size == 4
    assert metrics.schema_valid_rate == 1.0
    assert metrics.diff_applies_rate == 0.75
    assert metrics.target_resolved_rate == 0.5
    assert metrics.median_diff_size == 3
    assert "EmptyCatchBlock" in metrics.by_rule
    assert metrics.by_rule["EmptyCatchBlock"]["resolvedRate"] == 0.5


def test_rank_conditions():
    r1 = [EvaluationSampleResult("1", "R", True, True, True, True, True, 2, 100, 100, 0.01)]
    r2 = [EvaluationSampleResult("1", "R", True, True, True, False, False, 2, 100, 100, 0.01)]

    m1 = compute_condition_metrics("Better Condition", "m1", "v1", "RAG", r1)
    m2 = compute_condition_metrics("Worse Condition", "m2", "v1", "NONE", r2)

    ranked = rank_conditions([m2, m1])
    assert ranked[0].condition_name == "Better Condition"
    assert ranked[1].condition_name == "Worse Condition"
