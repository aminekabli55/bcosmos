# ADR 0006 — Python only in `ai-lab` and `spark-jobs`

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0
- **Source:** spec v2 §2.2

## Context

Mixing languages inside one module makes builds, dependency management and review harder. At the same time the ML
ecosystem (pandas, pyarrow, notebooks, transformers/peft/trl) is Python-only.

## Decision

Python is allowed **only** in `ai-lab/` (datasets, evaluation harness, fine-tuning notebooks, prompts) and
`spark-jobs/` (streaming, batch, schemas). `backend`, `analysis-core`, `analysis-worker` and `miner` are Java 21.

## Consequences

- Clear boundary: Java produces the data (findings, metrics, Parquet), Python consumes it for ML work.
- The two Python modules get their own venvs and pinned requirements; nothing imports across the boundary at
  runtime, only through files/artifacts.
- Fine-tuning libraries are installed inside Colab/Kaggle notebooks only, keeping the local venv small.
