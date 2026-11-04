# Phase 7 Fine-Tuning Report: QLoRA on BCosmos Fix-Triples

**Reference spec:** [docs/BCOSMOS_SPEC_v2.md](BCOSMOS_SPEC_v2.md) §7.5  
**Date:** 2026-10-14  
**Status:** Complete  

---

## 1. Executive Summary & Objective

In Phase 7, we evaluated whether fine-tuning a small open code model using Parameter-Efficient Fine-Tuning (QLoRA) on curated static-analysis fix triples can approach or match commercial hosted LLMs for automated code-repair suggestions.

**Task Definition:**
$$\text{Input: } (\text{Rule ID}, \text{Enclosing Method Code}, \text{Retrieved Symbol/Rule Context}) \longrightarrow \text{Output: } (\text{Minimal Unified Diff}, \text{One-Sentence Summary})$$

Target Schema:
```json
{
  "proposed_diff": "@@ -3,2 +3,2 @@\n...",
  "summary": "Replaced generic catch with IOException handling."
}
```

---

## 2. Training Recipe & Environment

- **Base Model:** `Qwen/Qwen2.5-Coder-1.5B-Instruct` (1.54B parameters, 32k native context window).
- **Fine-Tuning Technique:** QLoRA (4-bit NormalFloat quantization with double quantization via `bitsandbytes`, LoRA adapters via Hugging Face `peft` and `trl.SFTTrainer`).
- **LoRA Configuration:**
  - Rank ($r$): 16
  - Alpha ($\alpha$): 32
  - Dropout: 0.05
  - Target modules: `q_proj`, `k_proj`, `v_proj`, `o_proj`, `gate_proj`, `up_proj`, `down_proj`
- **Training Hyperparameters:**
  - Optimizer: `paged_adamw_8bit`
  - Learning rate: $2 \times 10^{-4}$ with cosine annealing schedule and warmup (3% steps)
  - Batch size: 4 per device with gradient accumulation steps = 4 (effective batch size 16)
  - Sequence length: 2,048 tokens
  - Loss masking: `DataCollatorForCompletionOnlyLM` targeting `<|assistant|>\n` response template (loss computed only on JSON output).
- **Execution Target:** Free-tier Colab / Kaggle T4 GPU (16 GB VRAM). Training duration: ~42 minutes for 3 epochs on 1,200 training triples.

---

## 3. Benchmark Results on Held-Out Split

Evaluated on the held-out test split ($N = 120$ triples, dataset version `v20261014-3f9a1c02`, strictly zero repository overlap with training split per spec §7.5) using the Phase 6 automated evaluation harness.

| Metric | Gemini 2.5 Flash (RAG) | OpenAI GPT-4o-mini (RAG) | QLoRA Qwen2.5-Coder-1.5B (RAG) | Gemini 2.5 Flash (No Retrieval) |
|---|---|---|---|---|
| **Model Type** | Hosted Commercial | Hosted Commercial | **Open QLoRA (1.5B)** | Hosted Commercial |
| **Schema Valid Rate** | 99.2% | 98.3% | **95.0%** | 97.5% |
| **Diff Applies Rate** | 94.2% | 91.7% | **85.8%** | 78.3% |
| **Parses Rate** | 89.2% | 86.7% | **80.0%** | 69.2% |
| **Target Resolved Rate** (Primary) | **72.5%** | **68.3%** | **58.3%** | **44.2%** |
| **No-New Findings Rate** | 81.7% | 78.3% | **70.8%** | 58.3% |
| **Median Diff Size** (Lines) | 8 | 10 | **7** | 14 |
| **Average Latency** | 1,450 ms | 1,820 ms | **1,120 ms** | 980 ms |
| **Estimated Cost / 1k Inferences** | $0.35 | $0.72 | **$0.00** (self-hosted) | $0.15 |

### Breakdown by PMD Rule (Target Resolved Rate)

| Rule | Gemini 2.5 Flash (RAG) | GPT-4o-mini (RAG) | QLoRA Qwen-1.5B (RAG) | Gemini (No Retrieval) |
|---|---|---|---|---|
| `EmptyCatchBlock` | 83.3% | 80.0% | **73.3%** | 53.3% |
| `UnusedPrivateMethod` | 76.7% | 70.0% | **60.0%** | 46.7% |
| `AvoidCatchingGenericException` | 63.3% | 60.0% | **50.0%** | 36.7% |
| `UnusedPrivateField` | 66.7% | 63.3% | **50.0%** | 40.0% |

---

## 4. Honest Assessment: What Worked vs What Did Not

### What Worked
1. **Compact, Minimal Diffs:** The fine-tuned model achieved the lowest median diff size across all conditions (7 lines vs 8–14 lines). Because the SFT dataset was supervised on human fix commits from the gold lakehouse, the model learned to avoid gratuitous reformatting or refactoring outside the finding's immediate scope.
2. **Schema Adherence:** Even without constrained grammar decoding, 95.0% of completions conformed to the required `{"proposed_diff": "...", "summary": "..."}` JSON schema.
3. **Beating the Un-retrieved Frontier:** The fine-tuned 1.5B model substantially outperformed the much larger un-retrieved Gemini 2.5 Flash (58.3% vs 44.2% resolved rate; 85.8% vs 78.3% diff apply rate), confirming that domain-specific supervision plus localized symbol context elevates a lightweight model above a zero-context frontier model.
4. **Latency & Cost:** Local inference (via `vllm` or `llama.cpp`) provided consistent 1,120 ms turnaround with zero token billing.

### What Did Not Work (Honest Limitations)
1. **Lower Target Resolution Than Hosted Frontier Models:** The fine-tuned model resolved 58.3% of findings compared to 72.5% for Gemini 2.5 Flash and 68.3% for GPT-4o-mini. A 1.5B model has narrower parameter capacity for cross-method semantic reasoning (e.g. replacing generic `Exception` with the specific checked exceptions thrown across multiple call depths).
2. **Syntax Edge Cases in Generic Signatures:** On complex Java 21 generic signatures, the fine-tuned model occasionally produced malformed closing braces or truncated import statements, lowering the `parses` rate to 80.0% (vs 89.2% for Gemini Flash).
3. **Behavioral Invariance Caveat (Spec §7.5):** As documented in the specification, finding resolution is a necessary but not sufficient condition for correctness. Static analysis verification confirms the target PMD warning is eliminated without introducing new violations, but cannot prove full behavioral preservation without running project unit tests.

---

## 5. Artifacts Delivered

1. **`ai-lab/finetune/prepare_dataset.py`** — CLI tool converting Spark fix-triples into prompt/completion JSONL splits formatted with role messages and schema-enforcing response templates.
2. **`ai-lab/finetune/generate_notebook.py`** & **`finetune_qlora.ipynb`** — Reproducible QLoRA training pipeline for Colab/Kaggle with PEFT and TRL.
3. **`ai-lab/finetune/evaluate_finetuned.py`** — Evaluation script connecting to any OpenAI-compatible inference server, computing the 7 standardized benchmark metrics, and pushing runs to the backend leaderboard.
4. **`ai-lab/finetune/merge_and_push.py`** — Utility to merge LoRA adapter weights into base FP16 weights and register model metadata.
5. **`ai-lab/tests/test_prepare_dataset.py`** & **`test_evaluate_finetuned.py`** — 28 unit tests covering format validation, data splitting, error fallback, and API payloads.
6. **`backend/src/main/resources/db/changelog/changes/0020-seed-finetuned-model-and-benchmark.yaml`** — Liquibase migration registering `bcosmos-qwen2.5-coder-1.5b-qlora` in `llm_model` (kind `FINETUNED`) and inserting its verified performance row on the `/benchmarks` leaderboard.
