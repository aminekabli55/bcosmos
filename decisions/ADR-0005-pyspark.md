# ADR 0005 — PySpark for the Spark jobs

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (used from Phase 5)
- **Source:** spec v2 §2.2

## Context

Spark jobs can be written in Python, Scala or Java. The team is one person who also writes the evaluation and
fine-tuning code.

## Decision

`spark-jobs/` is **PySpark** (`pyspark==4.2.0`), sharing the Python tooling, packaging and CI conventions of
`ai-lab/`. Product code stays Java.

## Consequences

- Dataset building shares code and mental model with `ai-lab` (pandas/pyarrow on the gold layer).
- No compile step for the jobs; correctness relies on pytest fixtures with tiny local data.
- Mixed-language repo: Python uses a venv per module (`spark-jobs/.venv`, `ai-lab/.venv`) with pinned
  `requirements.txt`; Java modules use Maven.
