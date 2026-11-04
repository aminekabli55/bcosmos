# ADR 0030 — QLoRA fine-tuning, evaluation, and leaderboard integration

- **Status:** Accepted (Phase 7)
- **Date:** 2026-10-14
- **Related:** [ADR 0006](ADR-0006-python-scope.md) (Python only in ai-lab & spark-jobs),
  [ADR 0007](ADR-0007-llm-client-gateway.md) (LLM access only through LlmClient gateway),
  [ADR 0013](ADR-0013-hosted-llm-apis.md) (Hosted LLM APIs),
  [ADR 0021](ADR-0021-rag-and-validated-suggestions.md) (RAG and validated suggestions),
  [ADR 0028](ADR-0028-spark-gold-layer-and-analytics-api.md) (Spark gold tables and dataset version),
  spec v2 sections 7.4, 7.5 and 10

## Context

Phase 7 investigates domain adaptation through fine-tuning: training a lightweight open code model on BCosmos fix-triples produced by the Spark lakehouse (Phase 5) and comparing its suggestion quality objectively against hosted commercial models on the benchmark harness (Phase 6).

Several architectural choices were required:
1. Training environment and compute budget: how to train without requiring expensive local GPU hardware.
2. SFT dataset formatting: how fix triples map into chat-template instructions matching the prompt shape in `analysis-core`.
3. Serving abstraction: how the fine-tuned model integrates into the existing BCosmos runtime without changing backend core abstractions.
4. Evaluation protocol: ensuring fair, reproducible comparison between hosted frontier models and parameter-efficient fine-tuned models.

## Decision

**1. QLoRA with 4-bit NormalFloat quantization on free Colab/Kaggle GPUs.**
Training is performed via `ai-lab/finetune/finetune_qlora.ipynb` (generated from `generate_notebook.py`) using Hugging Face `transformers`, `peft`, and `trl.SFTTrainer`. Using QLoRA ($r=16, \alpha=32$, target linear projections) allows training a 1.5B parameter model (`Qwen/Qwen2.5-Coder-1.5B-Instruct`) in ~42 minutes within 16 GB VRAM on a free T4 GPU.

**2. Strict split isolation by repository.**
In accordance with spec §6.2 and §7.5, the training split contains fix triples only from repositories in the training bucket. Evaluation strictly uses the held-out test split (`v20261014-3f9a1c02`) where zero repositories overlap. `prepare_dataset.py` formats each triple into standardized system/user/assistant turns and masks prompt tokens so loss is computed solely on the assistant's JSON completion.

**3. Model serving through standard OpenAI-compatible HTTP interface.**
To adhere to ADR 0007 and ADR 0013, the fine-tuned model is served via an OpenAI-compatible endpoint (e.g. `vllm` or `llama.cpp` server) and registered in `llm_model` with `provider: OPENAI_COMPATIBLE` and `kind: FINETUNED`. No custom Python-to-Java bridges or native bindings are introduced into `backend`. In line with spec §10, the model is registered with `enabled: false` by default, requiring explicit operator enablement.

**4. Reuse of Phase 6 automated evaluation metrics.**
`evaluate_finetuned.py` directly leverages `ai-lab/eval/evaluator.py`. The fine-tuned model is measured on the identical 7 metrics: `schema_valid_rate`, `diff_applies_rate`, `parses_rate`, `target_resolved_rate` (primary), `no_new_findings_rate`, `median_diff_size`, and `avg_latency_ms`. Results are POSTed to `/api/benchmarks` and displayed on the `/benchmarks` leaderboard alongside hosted conditions.

## Consequences

- **Reproducibility:** Anyone can reproduce the training and evaluation workflow using the self-contained notebook and dataset preparation script.
- **Tighter Diffs:** Fine-tuning on human fix commits trains the model to generate concise, minimal patches (median 7 lines vs 8–10 lines for commercial models).
- **Honest Limitations Stated:** While the fine-tuned 1.5B model beats the un-retrieved baseline (58.3% vs 44.2%), it does not surpass commercial frontier models with RAG (72.5% for Gemini 2.5 Flash). The results are documented transparently in `docs/fine-tuning-report.md`.
