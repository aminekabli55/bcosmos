# ADR 0013 — All LLMs through hosted APIs, no local model required

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (used from Phase 2)
- **Source:** spec v2 §2.2

## Context

The development machine has no GPU and limited RAM, and the local stack already runs Postgres, Kafka and Spark.
Comparing several models is a core goal, and having one runnable local model would not deliver that.

## Decision

Inference happens exclusively through hosted APIs (Gemini free tier first, plus at least one more provider). A
fine-tuned model from Phase 7 is either hosted remotely or reported on the leaderboard without a hosted endpoint;
nothing depends on a local GPU. Usage (tokens, latency, estimated cost) is recorded per call, prompts are cached by
`request_hash`, and every prompt passes through secret redaction before it leaves the machine.

## Consequences

- Model comparison and cost accounting are first-class; the free tier makes experimentation cheap.
- The demo depends on network access and on API keys being present in the environment; with AI switched off the
  analysis features keep working (an explicit done-when condition of Phase 2).
- Per-model budgets and small benchmark sets first are the guard rails against runaway cost.
