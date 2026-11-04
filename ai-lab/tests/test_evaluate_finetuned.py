"""Unit tests for evaluate_finetuned.py (Phase 7, spec v2 section 7.5)."""

from __future__ import annotations

import json
import urllib.error
from unittest.mock import MagicMock, patch
import pytest

from finetune.evaluate_finetuned import (
    call_model,
    chat_completion,
    push_to_leaderboard,
)
from eval.evaluator import EvaluationSampleResult, compute_condition_metrics


def test_chat_completion_success():
    fake_response = {
        "choices": [
            {"message": {"content": json.dumps({"proposed_diff": "diff", "summary": "fixed"})}}
        ],
        "usage": {"total_tokens": 150},
    }
    mock_resp = MagicMock()
    mock_resp.read.return_value = json.dumps(fake_response).encode("utf-8")
    mock_resp.__enter__.return_value = mock_resp

    with patch("urllib.request.urlopen", return_value=mock_resp):
        res = chat_completion(
            base_url="http://localhost:11434/v1",
            model_name="test-model",
            messages=[{"role": "user", "content": "hello"}],
        )
        assert res["choices"][0]["message"]["content"] == fake_response["choices"][0]["message"]["content"]
        assert res["usage"]["total_tokens"] == 150


def test_call_model_valid_json_completion():
    triple = {
        "id": "t-1",
        "rule_id": "EmptyCatchBlock",
        "file_path": "Foo.java",
        "start_line": 10,
        "before_snippet": "try {} catch(Exception e) {}",
        "retrieved_evidence": {"rule_description": "Catch block is empty"},
    }
    fake_completion = {
        "choices": [
            {
                "message": {
                    "content": json.dumps(
                        {"proposed_diff": "@@ -1,1 +1,1 @@\n-//\n+throw e;", "summary": "Rethrow exception"}
                    )
                }
            }
        ],
        "usage": {"total_tokens": 120},
    }

    with patch("finetune.evaluate_finetuned.chat_completion", return_value=fake_completion):
        comp = call_model(
            base_url="http://localhost:11434/v1",
            model_name="test-model",
            triple=triple,
            api_key="local",
            max_tokens=256,
            system_prompt="",
            timeout=10,
        )
        assert "proposed_diff" in comp
        assert comp["proposed_diff"] == "@@ -1,1 +1,1 @@\n-//\n+throw e;"
        assert comp["summary"] == "Rethrow exception"
        assert comp["tokens"] == 120
        assert comp["cost_usd"] == 0.0


def test_call_model_malformed_json_fallback():
    triple = {
        "id": "t-2",
        "rule_id": "UnusedPrivateMethod",
        "file_path": "Bar.java",
        "start_line": 20,
        "before_snippet": "private void unused() {}",
    }
    fake_completion = {
        "choices": [{"message": {"content": "Not a valid JSON response from model"}}],
        "usage": {"total_tokens": 80},
    }

    with patch("finetune.evaluate_finetuned.chat_completion", return_value=fake_completion):
        comp = call_model(
            base_url="http://localhost:11434/v1",
            model_name="test-model",
            triple=triple,
            api_key="local",
            max_tokens=256,
            system_prompt="",
            timeout=10,
        )
        assert comp["proposed_diff"] == ""
        assert "Not a valid JSON" in comp["summary"]
        assert comp["tokens"] == 80


def test_call_model_exception_handling():
    triple = {"id": "t-3", "rule_id": "EmptyCatchBlock"}
    with patch("finetune.evaluate_finetuned.chat_completion", side_effect=urllib.error.URLError("Connection refused")):
        comp = call_model(
            base_url="http://localhost:11434/v1",
            model_name="test-model",
            triple=triple,
            api_key="local",
            max_tokens=256,
            system_prompt="",
            timeout=10,
        )
        assert comp["proposed_diff"] == ""
        assert "ERROR: <urlopen error Connection refused>" in comp["summary"]
        assert comp["tokens"] == 0


def test_push_to_leaderboard_payload():
    results = [
        EvaluationSampleResult("1", "EmptyCatchBlock", True, True, True, True, True, 5, 500.0, 100, 0.0)
    ]
    metrics = compute_condition_metrics("QLoRA test", "test-model", "v1", "RAG", results)

    mock_resp = MagicMock()
    mock_resp.read.return_value = json.dumps({"id": 42}).encode("utf-8")
    mock_resp.__enter__.return_value = mock_resp

    posted_payload = {}

    def fake_urlopen(req, timeout=30):
        nonlocal posted_payload
        posted_payload = json.loads(req.data.decode("utf-8"))
        return mock_resp

    with patch("urllib.request.urlopen", side_effect=fake_urlopen):
        push_to_leaderboard(
            backend_url="http://localhost:8080",
            benchmark_id="bench-01",
            dataset_version="v20261014-3f9a1c02",
            condition_name="QLoRA test",
            model_name="test-model",
            prompt_version="v1",
            retrieval_strategy="RAG",
            metrics=metrics,
        )

    assert posted_payload["benchmarkId"] == "bench-01"
    assert posted_payload["datasetVersion"] == "v20261014-3f9a1c02"
    assert posted_payload["conditionName"] == "QLoRA test"
    assert posted_payload["targetResolvedRate"] == 1.0
    assert posted_payload["medianDiffSize"] == 5
