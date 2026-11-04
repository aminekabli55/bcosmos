"""Evaluate a fine-tuned (or any OpenAI-compatible) model against the BCosmos held-out
test split, then optionally push results to the leaderboard (Phase 7, spec v2 §7.5).

The script reuses the Phase 6 ``Evaluator`` metrics engine so fine-tuned results are
directly comparable to hosted-model baselines on the same dataset version.

Typical usage (Colab / local vllm server):
    # Start a local inference server first:
    #   vllm serve ./outputs/bcosmos-merged --port 11434 --served-model-name bcosmos-finetuned

    python evaluate_finetuned.py \\
        --model-url http://localhost:11434/v1 \\
        --model-name bcosmos-finetuned \\
        --dataset ../datasets/test_split.json \\
        --condition-name "QLoRA Qwen2.5-Coder-1.5B (fine-tuned, RAG)" \\
        --dataset-version v20261014-3f9a1c02 \\
        --push-to http://localhost:8080

Limitations (documented in spec §7.5):
- Resolution of a static-analysis finding is a *necessary* but not *sufficient*
  condition for behavioral preservation.  No compile or test run is performed.
- The test split is small (≤ 200 triples).  Confidence intervals are wide.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT))

from eval.evaluator import (
    EvaluationSampleResult,
    compute_condition_metrics,
    evaluate_sample_completion,
    rank_conditions,
)


# ── OpenAI-compatible chat completion ────────────────────────────────────────

def chat_completion(
    base_url: str,
    model_name: str,
    messages: List[Dict[str, str]],
    max_tokens: int = 512,
    temperature: float = 0.0,
    api_key: str = "local",
    timeout: int = 120,
) -> Dict[str, Any]:
    """Calls an OpenAI-compatible /v1/chat/completions endpoint."""
    url = base_url.rstrip("/") + "/chat/completions"
    payload = json.dumps(
        {
            "model": model_name,
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def call_model(
    base_url: str,
    model_name: str,
    triple: Dict[str, Any],
    api_key: str,
    max_tokens: int,
    system_prompt: str,
    timeout: int,
) -> Dict[str, Any]:
    """Formats the triple into messages and calls the model; returns a completion dict."""
    from finetune.prepare_dataset import triple_to_messages  # type: ignore

    messages = triple_to_messages(triple)
    # For evaluation we drop the ground-truth assistant turn and let the model complete.
    messages = [m for m in messages if m["role"] != "assistant"]

    t0 = time.monotonic()
    try:
        response = chat_completion(
            base_url=base_url,
            model_name=model_name,
            messages=messages,
            max_tokens=max_tokens,
            temperature=0.0,
            api_key=api_key,
            timeout=timeout,
        )
    except Exception as exc:
        return {
            "proposed_diff": "",
            "summary": f"ERROR: {exc}",
            "latency_ms": (time.monotonic() - t0) * 1000,
            "tokens": 0,
            "cost_usd": 0.0,
        }

    latency_ms = (time.monotonic() - t0) * 1000
    choice = response["choices"][0]["message"]["content"] if response.get("choices") else ""
    usage = response.get("usage", {})
    tokens = usage.get("total_tokens", 0)

    # Parse JSON from the model output; fall back gracefully.
    proposed_diff = ""
    summary = ""
    try:
        parsed = json.loads(choice)
        proposed_diff = parsed.get("proposed_diff", "")
        summary = parsed.get("summary", "")
    except json.JSONDecodeError:
        summary = choice[:200]

    return {
        "proposed_diff": proposed_diff,
        "summary": summary,
        "latency_ms": latency_ms,
        "tokens": tokens,
        "cost_usd": 0.0,  # hosted locally; no API cost
    }


# ── Push to leaderboard ──────────────────────────────────────────────────────

def push_to_leaderboard(
    backend_url: str,
    benchmark_id: str,
    dataset_version: str,
    condition_name: str,
    model_name: str,
    prompt_version: str,
    retrieval_strategy: str,
    metrics: Any,
) -> None:
    """POSTs a BenchmarkRun to the BCosmos backend leaderboard."""
    payload = {
        "benchmarkId": benchmark_id,
        "datasetVersion": dataset_version,
        "conditionName": condition_name,
        "modelName": model_name,
        "promptVersion": prompt_version,
        "retrievalStrategy": retrieval_strategy,
        "sampleSize": metrics.sample_size,
        "schemaValidRate": metrics.schema_valid_rate,
        "diffAppliesRate": metrics.diff_applies_rate,
        "parsesRate": metrics.parses_rate,
        "targetResolvedRate": metrics.target_resolved_rate,
        "noNewFindingsRate": metrics.no_new_findings_rate,
        "medianDiffSize": metrics.median_diff_size,
        "avgLatencyMs": metrics.avg_latency_ms,
        "totalTokens": metrics.total_tokens,
        "estimatedCostUsd": metrics.estimated_cost_usd,
        "metricsJson": json.dumps(metrics.by_rule),
        "notes": f"Fine-tuned model evaluated on held-out set. Condition: {condition_name}.",
    }
    url = backend_url.rstrip("/") + "/api/benchmarks"
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        result = json.loads(resp.read().decode("utf-8"))
    print(f"[push] Recorded benchmark run id={result.get('id')} on leaderboard.")


# ── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate a fine-tuned model against the BCosmos held-out test split (Phase 7)"
    )
    parser.add_argument(
        "--model-url",
        default="http://localhost:11434/v1",
        help="Base URL of an OpenAI-compatible inference server (default: localhost:11434/v1).",
    )
    parser.add_argument(
        "--model-name",
        default="bcosmos-finetuned",
        help="Model name to pass in the API request.",
    )
    parser.add_argument(
        "--api-key",
        default="local",
        help="Bearer token for the inference server (use 'local' for vllm with no auth).",
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        default=REPO_ROOT / "datasets" / "test_split.json",
        help="Path to the held-out test split JSON.",
    )
    parser.add_argument(
        "--dataset-version",
        default="v20261014-3f9a1c02",
        help="Dataset version string (must match the leaderboard filter).",
    )
    parser.add_argument(
        "--condition-name",
        default="QLoRA Qwen2.5-Coder-1.5B (fine-tuned, RAG)",
        help="Human-readable condition label shown on the leaderboard.",
    )
    parser.add_argument(
        "--prompt-version",
        default="suggest-fix.v1",
        help="Prompt version identifier.",
    )
    parser.add_argument(
        "--retrieval-strategy",
        default="RAG",
        help="Retrieval strategy label (RAG | NONE | HYBRID).",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=512,
        help="Max tokens for model completion.",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=120,
        help="HTTP timeout per request (seconds).",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=REPO_ROOT / "finetune" / "finetuned_eval_report.json",
        help="Where to write the JSON evaluation report.",
    )
    parser.add_argument(
        "--push-to",
        default=None,
        metavar="BACKEND_URL",
        help="If set, POST the benchmark run to this BCosmos backend (e.g. http://localhost:8080).",
    )
    parser.add_argument(
        "--benchmark-id",
        default="ft-phase7-001",
        help="Unique benchmark run identifier.",
    )
    args = parser.parse_args()

    # Load dataset
    with open(args.dataset, "r", encoding="utf-8") as f:
        dataset = json.load(f)
    items = dataset.get("items", []) if isinstance(dataset, dict) else dataset
    print(f"Evaluating {len(items)} samples from {args.dataset}…")

    # Run inference + evaluate
    sample_results: List[EvaluationSampleResult] = []
    for i, triple in enumerate(items, 1):
        completion = call_model(
            base_url=args.model_url,
            model_name=args.model_name,
            triple=triple,
            api_key=args.api_key,
            max_tokens=args.max_tokens,
            system_prompt="",  # embedded in messages by prepare_dataset
            timeout=args.timeout,
        )
        result = evaluate_sample_completion(triple, completion)
        sample_results.append(result)
        sym = "✓" if result.target_resolved else "✗"
        print(f"  [{i}/{len(items)}] {triple.get('id','?')} {sym} "
              f"(resolved={result.target_resolved}, diff_applies={result.diff_applies})")

    # Compute aggregate metrics
    metrics = compute_condition_metrics(
        condition_name=args.condition_name,
        model_name=args.model_name,
        prompt_version=args.prompt_version,
        retrieval_strategy=args.retrieval_strategy,
        results=sample_results,
    )

    # Write JSON report
    args.output.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "benchmark_id": args.benchmark_id,
        "dataset_version": args.dataset_version,
        "condition": args.condition_name,
        "model": args.model_name,
        "metrics": metrics.to_dict(),
        "limitation": (
            "Resolution of a static-analysis finding is a necessary but not sufficient "
            "condition for behavioral preservation. No compile or runtime verification was performed."
        ),
    }
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"\nSaved evaluation report → {args.output}")

    # Print summary
    m = metrics
    print(f"\n─── Fine-tuned model leaderboard entry ───────────────────────────────")
    print(f"  Condition          : {m.condition_name}")
    print(f"  Model              : {m.model_name}")
    print(f"  Samples            : {m.sample_size}")
    print(f"  Target resolved    : {m.target_resolved_rate*100:.1f}%  ← primary metric")
    print(f"  No-new findings    : {m.no_new_findings_rate*100:.1f}%")
    print(f"  Diff applies       : {m.diff_applies_rate*100:.1f}%")
    print(f"  Parses             : {m.parses_rate*100:.1f}%")
    print(f"  Schema valid       : {m.schema_valid_rate*100:.1f}%")
    print(f"  Avg latency        : {m.avg_latency_ms:.0f} ms")
    print(f"  Total tokens       : {m.total_tokens}")
    print(f"─────────────────────────────────────────────────────────────────────")

    # Push to backend leaderboard
    if args.push_to:
        try:
            push_to_leaderboard(
                backend_url=args.push_to,
                benchmark_id=args.benchmark_id,
                dataset_version=args.dataset_version,
                condition_name=args.condition_name,
                model_name=args.model_name,
                prompt_version=args.prompt_version,
                retrieval_strategy=args.retrieval_strategy,
                metrics=metrics,
            )
        except urllib.error.URLError as exc:
            print(f"[push] WARNING: could not reach backend ({exc}). "
                  f"Run manually: POST {args.push_to}/api/benchmarks", file=sys.stderr)


if __name__ == "__main__":
    main()
