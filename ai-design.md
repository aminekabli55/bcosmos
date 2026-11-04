# AI design (Phase 2 onward)

Companion to spec v2 section 7. This page says how the AI layer is actually built, what it will and will not do,
and where the moving parts live.

## One interface

```java
public interface LlmClient {
    ChatResult chat(ChatRequest request);                          // free text
    <T> StructuredResult<T> structured(StructuredRequest<T> request); // validated JSON, one retry
    EmbedResult embed(EmbedRequest request);                        // vectors (Phase 3)
}
```

Business code names a registry model id — or nothing, to use the default — and never a provider, an endpoint or a
credential (ADR 0007, [ADR 0019](decisions/ADR-0019-ai-gateway.md)).

## What happens on one call

| Step | Where | Notes |
|---|---|---|
| 1. Refuse if the AI layer is off | `LlmGateway.requireEnabled` via the `AiEnablement` port | the stored switch (`app_setting`), falling back to `bcosmos.ai.enabled` ([ADR 0020](decisions/ADR-0020-stored-ai-switch.md)); nothing is sent anywhere |
| 2. Resolve the model and check its opt-in | `ModelRegistry.requireUsable` | a disabled row is a 409, not a call |
| 3. Redact the prompt | `SecretRedactor` | configured keys plus known credential shapes |
| 4. Send, with retries and a budget | `GeminiAdapter` / `OpenAiCompatibleAdapter` behind Resilience4j | retryable failures only, backoff, budget checked before each attempt; a 429's own wait is honoured ([ADR 0040](decisions/ADR-0040-rate-limit-backoff.md)) |
| 5. Validate structured output | `JsonSchemaValidator` | schema sent in the prompt; violations retried once with the errors attached |
| 6. Record usage | `JpaUsageRecorder` | own transaction, written for failures too |
| 7. Log | `LlmGateway` | model, attempts, tokens, latency, error code — never source code or credentials |

## Rate limits

A rate limit is the one provider failure that says when it will serve us again, so it is treated as its own case
twice over.

**One call** ([ADR 0040](decisions/ADR-0040-rate-limit-backoff.md)): HTTP 429 becomes `rate-limited` instead of
`provider-error`, `RetryWait` reads the wait out of the `Retry-After` header or Google's `RetryInfo.retryDelay` detail,
and the retry waits it when it fits inside `bcosmos.ai.timeout`. When it does not fit, the call fails *now*, naming the
wait it could not honour, and nothing further is sent — a timeout would hide both the cause and the length of the
wait. Nothing else changes: a 5xx keeps the exponential backoff, and a rejected credential or prompt is never
retried.

**One sequence** ([ADR 0041](decisions/ADR-0041-rate-limit-patience-for-a-sequence.md)): the index build is dozens of
embedding calls over minutes, so a per-call wait that fails the flow would make it impossible to finish on a
rate-limited tier. `RateLimitPatience` therefore wraps each batch: it sleeps what the provider asked for and calls
again with a fresh budget, while the build stays inside `bcosmos.retrieval.rate-limit-patience` (default `2m`) and at
most ten windows. Only `EmbeddingService` uses it — an explanation or a suggestion is a single answer somebody is
waiting for, and those still fail fast.

**A bounded, resumable build instead** ([ADR 0044](decisions/ADR-0044-bounded-resumable-index-builds.md)): patience
alone could not save a 1,636-chunk run, because the whole build was all-or-nothing and the first suggestion paid for
all of it. Now every answered batch is stored immediately, a build skips chunks whose text is already stored, and
`bcosmos.retrieval.index-texts-per-call` (default `96`, just under a free tier's 100 texts/minute) caps how much one
request sends. The finding's own file is indexed first. The rest follows with later suggestions, and the evidence
notes say how much of the run is indexed so far.

**Structured calls** ([ADR 0045](decisions/ADR-0045-gemini-structured-calls-in-2026.md)): Gemini requires at least
one `contents` turn, so a system-only prompt earns HTTP 400 — the adapter adds a neutral user turn. The seeded
`gemini-2.5-flash` is now restricted to projects that used it before September 2026 and answers 404 for new ones,
so changeset `0028` registers `gemini-3.8-flash` (disabled until enabled in Settings, like every seeded model), and
the AI Review screen starts both selects on the newest enabled row of each kind. The per-attempt budget is 90 s and
the output cap 4096 tokens: a reasoning model on an 8k-token prompt can take longer than the old 30 s.

## Registry and providers

A provider is a row (`llm_provider`), not a constant: `code`, `label`, `protocol`, `base_url`, and a credential.
`protocol` is what selects the adapter — the vendor is only data, so DeepSeek, Groq, OpenRouter or a second OpenAI
account are inserts, not deployments ([ADR 0038](decisions/ADR-0038-provider-registry-in-the-database.md)).

| Protocol | Endpoint | Structured output |
|---|---|---|
| `GEMINI` | `/v1beta/models/{model}:generateContent`, key in the `x-goog-api-key` header | `responseMimeType: application/json` |
| `OPENAI_COMPATIBLE` | `/v1/chat/completions`, key in the `Authorization` header | `response_format: {"type":"json_object"}` |

The same applies to embeddings (`:batchEmbedContents` and `/v1/embeddings`). The adapter sets the provider's JSON
mode, but JSON mode only promises JSON — the schema verdict comes from this project's validator.

**Where the key comes from** is decided per row and reported as `credentialSource`:

| Source | Meaning |
|---|---|
| `STORED` | pasted in Settings, AES-256-GCM encrypted under the master key (the environment, or the dev key file of ADR 0039), the `code` as associated data |
| `ENVIRONMENT` | no stored key: the variable `api_key_env` names, read from the process environment (the Phase 2 behaviour) |
| `NOT_REQUIRED` | a local server (Ollama, vLLM) that takes no credential — `api_key_required = false` |
| `MISSING` | a credential is required and neither source has one: the call is refused with a 409 that says so |

`GET/POST /api/providers`, `PUT/DELETE /api/providers/{id}` maintain the rows (validation in `ProviderRegistry`:
404 unknown id, 409 while `llm_model` still references the row, 409 pasting a key when no master key is
configured). No response ever carries a key: only the source, the environment variable name and the last four
characters. The `code` never changes — logs, usage rows and the encryption's associated data all name it.

## Configuration

| Property | Environment variable | Meaning |
|---|---|---|
| `bcosmos.ai.enabled` | `BCOSMOS_AI_ENABLED` | global switch, and the **default** for the stored one; off in the global file, on in the dev profile. The Settings screen overrides it per deployment through `PUT /api/settings/ai` |
| `bcosmos.ai.timeout` | `BCOSMOS_AI_TIMEOUT` | total budget of one call, including retries |
| `bcosmos.ai.connect-timeout` | `BCOSMOS_AI_CONNECT_TIMEOUT` | how long to wait for a connection |
| `bcosmos.ai.max-attempts` | `BCOSMOS_AI_MAX_ATTEMPTS` | provider attempts, including the first |
| `bcosmos.ai.initial-backoff` | `BCOSMOS_AI_INITIAL_BACKOFF` | wait before the second attempt — unless the provider stated its own wait for a rate limit ([ADR 0040](decisions/ADR-0040-rate-limit-backoff.md)) |
| `bcosmos.ai.backoff-multiplier` | `BCOSMOS_AI_BACKOFF_MULTIPLIER` | how fast that wait grows |
| `bcosmos.ai.max-output-tokens` | `BCOSMOS_AI_MAX_OUTPUT_TOKENS` | default output cap |
| `bcosmos.ai.secret-key` | `BCOSMOS_AI_SECRET_KEY` | master key for credentials pasted in Settings (32 random bytes, base64: `openssl rand -base64 32`). Absent means no stored keys: pasting one is refused with 409 and the environment fallback keeps working; a malformed value fails at startup. **Wins over the key file** |
| `bcosmos.ai.secret-key-file` | `BCOSMOS_AI_SECRET_KEY_FILE` | where a locally kept master key lives. Empty in the global file; the dev profile points it at `${user.home}/.bcosmos/ai-secret.key` |
| `bcosmos.ai.generate-secret-key` | `BCOSMOS_AI_GENERATE_SECRET_KEY` | whether a missing or empty key file may be filled with a freshly generated key. `false` everywhere except the dev profile (ADR 0039) |

The master key is resolved in that order: the environment, then the file, then — only with generation on (the dev
profile) — a new 32-byte key written to the file on first start and reused by every later start. A deployment that
sets `BCOSMOS_AI_SECRET_KEY` never sees the file, and a profile that does not enable generation still refuses to
store a pasted key (ADR 0039).

Provider endpoints and credentials are **not** configuration any more — they are `llm_provider` rows seeded by
changeset `0025` and edited in Settings (the old `bcosmos.ai.providers.*` blocks are gone; the variable each row
falls back on lives in its `api_key_env` column).

`GET /api/settings/ai` reports the switch together with `propertyDefault`, `overridden` and `updatedAt`;
`PUT /api/settings/ai` stores it, and a body without `enabled` clears the override so the property decides again.
`GET/PUT /api/models` and `GET/POST/PUT/DELETE /api/providers` drive the registry beside it — together they are
everything the Settings screen needs.

## Data

| Table | What it holds |
|---|---|
| `llm_provider` | one endpoint: code, label, protocol (selects the adapter), base URL, and the credential — environment variable name, or encrypted key + four-character hint |
| `llm_model` | the registry: `provider_id`, model name, kind, `enabled`, `is_default`, context window, prices, notes; unique on `(provider_id, model_name)` |
| `llm_usage` | one row per call: tokens, latency, attempts, estimated cost, outcome, error code, prompt version |
| `app_setting` | settings the browser may change; so far one row, `ai.enabled`, which overrides the property |

`llm_usage.outcome` is one of `SUCCESS`, `SCHEMA_INVALID`, `PROVIDER_ERROR`, `TIMEOUT`. A call that was never sent
(AI off, no model selected) has no row, because there is no call to account for. `error_code` is what tells apart
the failures that share an outcome — `rate-limited` (HTTP 429, with the wait the provider asked for in the message)
against `provider-error` (a 5xx or a transport failure) is the pair that matters when deciding whether re-running is
worth anything ([ADR 0040](decisions/ADR-0040-rate-limit-backoff.md)).

## Prompts

Runtime templates are versioned files next to the schema they validate against:

```
backend/src/main/resources/prompts/explain-finding.v1.md
backend/src/main/resources/prompts/explain-finding.v1.json
```

`{{placeholder}}` values are filled by `PromptTemplate.render`, and a missing value fails instead of sending literal
braces to a model. The version (`explain-finding.v1`) is stored on the usage row, so "which prompt produced this"
is answerable after the fact. `ai-lab/prompts/` holds experiment and evaluation prompts — see its README for why the
two are separate.

## Prompt-injection posture

Code and comments are data, never instructions: the analysed snippet is wrapped inside explicit evidence markers, the
instructions say so, and a test asserts that a snippet containing "ignore previous instructions" ends up inside the
evidence block rather than in the instruction part of the prompt. A model has no shell, filesystem or network access,
and patches are never applied automatically.

## Retrieval (Phase 3, spec 7.2)

`Retriever` builds one evidence bundle for a finding from a token budget, in this order:

| Part of the bundle | Where it comes from |
|---|---|
| the enclosing declaration | the chunk whose symbol contains the finding's span |
| callers and callees | the symbol table, when the reference resolves |
| similar chunks | pgvector cosine distance over `code_chunk`, above a similarity floor |
| the rule description | `backend/src/main/resources/rules/rule-descriptions.properties` |

Every item is returned to the browser with file, line range, kind and similarity, so a reader can see what the model
was shown. The bundle reports `estimatedTokens`, `truncated`, `chunksConsidered` and the index state
(`chunks`, `reused`, `symbolsWithoutSource`); truncation and un-indexable symbols are shown, never hidden.

### Chunks and the index

`SymbolChunker` writes one `code_chunk` per method or class, with a header (qualified name, file, signature) and the
embedding produced through `LlmClient.embed()`. Indexing is scoped to one analysis run and memoized by the embedding
model and chunker version, so asking a second question about the same run reuses the vectors instead of paying for
them again.

## Suggestions and the verdict (Phase 3, spec 7.3)

`POST /api/findings/{id}/suggestions` sends the evidence bundle, the finding and the rule description to a chat
model, and stores the answer.

`PatchValidator` then computes a verdict from the answer, using the analysed revision — not the model's confidence:

| Check | How it is decided |
|---|---|
| `schemaOk` | the answer matched the JSON schema, like every structured call |
| `evidenceOk` | every citation names a file and line the run actually contains |
| `diffApplies` | `UnifiedDiff.parse` applies the patch in memory to the analysed source |
| `parses` | the patched source still parses |
| `targetFindingGone` | re-running the baseline rule set on the patched source no longer reports the finding |
| `newFindings[]` | findings the patch would introduce, each with its rule and line |

`notes` records what was **not** checked — notably that the patched file was parsed and analysed, not compiled.
`allChecksPassed` is convenience for the UI and never an approval.

## What a suggestion is not

- **It is not applied.** No code path writes a diff to a repository. Accept, reject and defer move the finding's
triage state; the patch stays a proposal on screen (spec 1.3).
- **It is not compiled.** There is no classpath, so a patch can pass every check and still not build; the UI says so.
- **It is not trusted because it sounds right.** A citation that does not exist fails `evidenceOk`, and the verdict
  is shown next to the answer rather than summarised into a badge.

## Screens

| Screen | Shows |
|---|---|
| AI Review (`/quality/:analysisId/findings/:findingId/review`) | the finding, the ask row (chat + embedding model), the summary and explanation, cited evidence, the verdict check by check, the proposed diff coloured line by line, the evidence bundle, and accept/reject/defer |
| Settings (`/settings`) | the AI switch, the provider registry (add/edit/delete an endpoint, paste a key that is encrypted on arrival and never echoed back, credential source + four-character hint per row) and the model registry — including registering any model id a provider spells and each row's capability (chat or embedding) |

## What this phase does not do

- No benchmark numbers: Phase 6 ranks models on a held-out set.
- No fine-tuning: Phase 7.
- No graph or history: Phase 8.
- No commit review: Phase 9.
