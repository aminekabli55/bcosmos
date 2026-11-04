# ADR 0007 — LLM access only through a provider-neutral `LlmClient`

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (implemented in Phase 2)
- **Source:** spec v2 §2.2

## Context

The project must compare several hosted providers and later add a fine-tuned model, per feature (explain a finding,
suggest a fix, summarise a commit). Letting controllers or services call provider SDKs directly would spread
provider details and make comparison impossible.

## Decision

Business code talks only to `LlmClient` (`chat`, `structured`, `embed`) in `backend/ai/gateway`. Provider
adapters (Gemini first, then one more; OpenAI-compatible HTTP is acceptable) are selected through `ModelRegistry`,
and every call records tokens, latency and estimated cost in `llm_model`/usage rows. API keys come from environment
variables only — never from the database, files or the browser.

## Consequences

- Model comparison, fallback and cost accounting are implemented once, not per feature.
- Provider-specific quirks (JSON mode, tool calling, embedding dimensions) are confined to adapters, and tests use
  WireMock fakes instead of real API calls.
- A vendor outage or key rotation is a configuration change, not a code change.
