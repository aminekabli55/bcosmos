"""Dataset preparation for QLoRA fine-tuning (Phase 7, spec v2 section 7.5).

Reads fix-triple splits (produced by the Phase 5 Spark ``fix_triples`` job or the
``ai-lab/datasets/`` directory), formats each triple as a supervised prompt/completion
pair, and writes ``train.jsonl`` / ``val.jsonl`` that any Hugging Face SFT trainer
can consume directly.

Prompt format (instruction-following, chat template compatible)
───────────────────────────────────────────────────────────────
<|system|>
You are BCosmos, an expert Java code-quality assistant. Your task is to produce a
**minimal unified diff** that resolves the reported PMD finding, plus a one-sentence
explanation. Respond ONLY with valid JSON matching the schema.

<|user|>
## Finding
Rule   : {rule_id}
File   : {file_path} (line {start_line})
Message: {rule_description}

## Code before change
```java
{before_snippet}
```

## Retrieved context
{retrieved_evidence_text}

<|assistant|>
{"proposed_diff": "...", "summary": "..."}

The JSON schema is enforced by the SFT trainer through the ``response_template``
field so only the assistant turn is in the loss.
"""

from __future__ import annotations

import json
import random
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

REPO_ROOT = Path(__file__).resolve().parents[1]

SYSTEM_PROMPT = (
    "You are BCosmos, an expert Java code-quality assistant. "
    "Produce a **minimal unified diff** that resolves the reported PMD finding, "
    "plus a one-sentence explanation. "
    "Respond ONLY with valid JSON: {\"proposed_diff\": \"<unified diff>\", \"summary\": \"<one sentence>\"}."
)


def format_evidence(evidence: Dict[str, Any]) -> str:
    """Renders retrieved_evidence into a readable text block."""
    parts: List[str] = []
    if enclosing := evidence.get("enclosing_symbol"):
        parts.append(f"Enclosing symbol: `{enclosing}`")
    if rule_desc := evidence.get("rule_description"):
        parts.append(f"Rule description: {rule_desc}")
    callers = evidence.get("callers") or []
    if callers:
        parts.append("Known callers: " + ", ".join(f"`{c}`" for c in callers))
    callees = evidence.get("callees") or []
    if callees:
        parts.append("Known callees: " + ", ".join(f"`{c}`" for c in callees))
    similar_chunks = evidence.get("similar_chunks") or []
    for chunk in similar_chunks[:3]:
        parts.append(f"Similar code:\n```java\n{chunk}\n```")
    return "\n".join(parts) if parts else "(no additional context)"


def triple_to_messages(triple: Dict[str, Any]) -> List[Dict[str, str]]:
    """Converts one fix-triple to a list of role/content messages (chat format)."""
    rule_id: str = triple.get("rule_id", "UnknownRule")
    file_path: str = triple.get("file_path", "Unknown.java")
    start_line: int = triple.get("start_line", 0)
    before_snippet: str = triple.get("before_snippet", "")
    evidence: Dict[str, Any] = triple.get("retrieved_evidence") or {}
    ground_truth_diff: str = triple.get("ground_truth_diff", "")
    summary_hint: str = triple.get("expected_fix", "Minimal patch applied.")

    user_content = (
        f"## Finding\n"
        f"Rule   : {rule_id}\n"
        f"File   : {file_path} (line {start_line})\n"
        f"Message: {evidence.get('rule_description', 'See rule documentation.')}\n"
        f"\n## Code before change\n```java\n{before_snippet}\n```\n"
        f"\n## Retrieved context\n{format_evidence(evidence)}"
    )

    # Assistant response is the target output — must match the JSON schema.
    completion_summary = (
        f"Remove or fix the {rule_id} violation: {summary_hint}" if summary_hint else f"Resolved {rule_id} violation."
    )
    assistant_content = json.dumps(
        {"proposed_diff": ground_truth_diff, "summary": completion_summary},
        ensure_ascii=False,
    )

    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_content},
        {"role": "assistant", "content": assistant_content},
    ]


def triple_to_sft_row(triple: Dict[str, Any]) -> Dict[str, Any]:
    """Produces one JSONL row: messages + metadata for traceability."""
    return {
        "id": triple.get("id", "unknown"),
        "rule_id": triple.get("rule_id"),
        "repo_name": triple.get("repo_name"),
        "messages": triple_to_messages(triple),
    }


def load_split_file(path: Path) -> List[Dict[str, Any]]:
    """Loads a split JSON (list or dict with 'items' key)."""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, list):
        return data
    return data.get("items", [])


def prepare_dataset(
    train_path: Optional[Path],
    val_path: Optional[Path],
    output_dir: Path,
    val_fraction: float = 0.15,
    seed: int = 42,
) -> Dict[str, int]:
    """Formats and writes train.jsonl and val.jsonl.

    If ``val_path`` is None, a fraction of ``train_path`` is held out as
    validation.  Returns a dict with sample counts.
    """
    output_dir.mkdir(parents=True, exist_ok=True)

    if train_path is None or not train_path.exists():
        # Fall back to the bundled test split so this script is runnable
        # without a full Spark run.
        train_path = REPO_ROOT / "datasets" / "test_split.json"
        print(f"[prepare_dataset] No train split provided; using {train_path}")

    triples = load_split_file(train_path)

    if val_path is not None and val_path.exists():
        val_triples = load_split_file(val_path)
        train_triples = triples
    else:
        # Stratified split by rule_id to keep class proportions.
        rng = random.Random(seed)
        by_rule: Dict[str, List[Dict[str, Any]]] = {}
        for t in triples:
            by_rule.setdefault(t.get("rule_id", "?"), []).append(t)

        train_triples, val_triples = [], []
        for rule_triples in by_rule.values():
            rng.shuffle(rule_triples)
            n_val = max(1, round(len(rule_triples) * val_fraction))
            val_triples.extend(rule_triples[:n_val])
            train_triples.extend(rule_triples[n_val:])

    train_out = output_dir / "train.jsonl"
    val_out = output_dir / "val.jsonl"

    with open(train_out, "w", encoding="utf-8") as f:
        for triple in train_triples:
            f.write(json.dumps(triple_to_sft_row(triple), ensure_ascii=False) + "\n")

    with open(val_out, "w", encoding="utf-8") as f:
        for triple in val_triples:
            f.write(json.dumps(triple_to_sft_row(triple), ensure_ascii=False) + "\n")

    print(f"[prepare_dataset] Wrote {len(train_triples)} train rows → {train_out}")
    print(f"[prepare_dataset] Wrote {len(val_triples)} val rows   → {val_out}")
    return {"train": len(train_triples), "val": len(val_triples)}


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(
        description="Prepare SFT JSONL dataset for QLoRA fine-tuning (Phase 7)"
    )
    parser.add_argument(
        "--train",
        type=Path,
        default=None,
        help="Path to the training split JSON (produced by Spark fix_triples job). "
             "Defaults to ai-lab/datasets/test_split.json when absent.",
    )
    parser.add_argument(
        "--val",
        type=Path,
        default=None,
        help="Path to a separate validation split JSON. "
             "If omitted, 15%% of train is held out.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=REPO_ROOT / "finetune",
        help="Directory to write train.jsonl and val.jsonl (default: ai-lab/finetune/).",
    )
    parser.add_argument(
        "--val-fraction",
        type=float,
        default=0.15,
        help="Fraction of train to use as val when no separate val split is given.",
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed for splitting.")
    args = parser.parse_args()

    counts = prepare_dataset(
        train_path=args.train,
        val_path=args.val,
        output_dir=args.output_dir,
        val_fraction=args.val_fraction,
        seed=args.seed,
    )
    print(f"Done — {counts['train']} train, {counts['val']} val rows.")


if __name__ == "__main__":
    main()
