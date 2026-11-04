# ADR 0038: Provider registry in the database

- **Status:** Accepted
- **Date:** 2026-10-16
- **Phase:** 11 (post-Phase 10 follow-up)
- **Related:** [ADR 0007](ADR-0007-llm-client-gateway.md), [ADR 0019](ADR-0019-ai-gateway.md), [ADR 0013](ADR-0013-hosted-llm-apis.md)

## Context

Since Phase 2 the set of callable providers was fixed at compile time: `LlmProvider` was a Java enum with two
values (`GEMINI`, `OPENAI_COMPATIBLE`), each with exactly one base URL and one API key, both read from configuration
(`bcosmos.ai.providers.*`). The adapter design was already provider-neutral — business code names a registry model
id, never a provider — but *which* providers exist, *where* they live and *which* key authenticates them was a
deployment decision.

That made three reasonable requests impossible:

1. "I want to use DeepSeek / Claude / Groq / OpenRouter / Ollama" — each required a new enum value, a new
   configuration block and a redeployment, even though every one of them speaks the OpenAI wire shape the existing
   adapter already speaks.
2. "I want to enter the model id and paste my own API key" — keys lived only in environment variables, so a user
   of the running application could not connect their own account without shell access to the server.
3. Two endpoints of the same protocol (say a personal OpenAI key and an Azure-ish gateway) could not coexist,
   because one base URL per enum value.

## Decision

**A provider is a row.** New table `llm_provider` (changeset `0025-create-llm-provider.yaml`):

| Column | Meaning |
|---|---|
| `code` | stable lowercase identifier (`gemini`, `deepseek`, `claude`); unique, immutable, and the associated data of the credential |
| `label` | display name for the screen |
| `protocol` | `GEMINI` or `OPENAI_COMPATIBLE` — **this** selects the adapter; the vendor is only data |
| `base_url` | the provider's documented API root; each protocol appends its own paths |
| `api_key_env` | environment variable read when no key is stored — the Phase 2 behaviour survives as a fallback |
| `api_key_ciphertext` | a pasted key, AES-256-GCM, base64 of `IV \|\| ciphertext \|\| tag` |
| `api_key_hint` | the last four characters, so the screen can say which key is in place |
| `api_key_required` | `false` for a local server (Ollama, vLLM) that takes no credential |

`llm_model` gains `provider_id` (FK, `RESTRICT`), the legacy `provider` enum column is dropped, and the unique
constraint becomes `(provider_id, model_name)`. Adding a model to a provider is then an ordinary registry row.

Supporting decisions:

- **Encryption with a master key from the environment.** `SecretCipher` reads a 32-byte base64 key from
  `BCOSMOS_AI_SECRET_KEY` (property `bcosmos.ai.secret-key`) and authenticates every ciphertext with the provider
  `code` as associated data, so a key moved to another row fails to decrypt instead of silently authenticating the
  wrong endpoint. The plaintext key is **never a column, never logged, never returned**; the API only ever reports
  `credentialSource` (`STORED`, `ENVIRONMENT`, `NOT_REQUIRED`, `MISSING`) and a four-character hint.
- **No master key means a refusal, not a downgrade.** With no key configured, storing a credential fails with 409
  and a message that says how to generate one; the environment fallback keeps working. A malformed key fails at
  startup, never at the first call.
- **REST CRUD at `GET/POST/PUT/DELETE /api/providers`**, paginated like every list endpoint, with validation in
  `ProviderRegistry` so the API and the tests share one rule set. A provider cannot be deleted while a model row
  references it (409 with the count); usage rows keep their own copy of the `code`, so history stays readable.
- **The code never changes after creation** — logs, `llm_usage.provider` and the encrypted credential's associated
  data all name it.
- **Seeded rows:** `gemini`, `openai`, `local-openai`, `deepseek` and `claude`. Claude is seeded against Anthropic's
  OpenAI-compatible layer; a native Anthropic `MESSAGES` protocol would be a new adapter value later, not a schema
  change.
- **The Settings screen manages providers**, including the key paste box, so connecting a vendor is an operator
  action in the browser rather than an edit to `application.yml`.

## Consequences

**Positive**

- Supporting a new vendor — DeepSeek today, anything that speaks either wire shape tomorrow — is an insert, not a
  deployment. Multiple endpoints of one protocol coexist, each with its own key.
- A user can connect their own account (id + key) from the UI; the key travels once, over the API, and is encrypted
  before it touches the database.
- The Phase 2 environment-variable path still works unchanged for deployments that manage secrets centrally: a row
  that names no stored key reads `api_key_env`.

**Negative / accepted trade-offs**

- Pasting a key requires `BCOSMOS_AI_SECRET_KEY` to be set (one `openssl rand -base64 32` on the deployment). The
  refusal message says exactly that.
- Encryption at rest protects against a database dump or backup leaking a *working* key; it does not protect
  against an attacker who can read both the database and the process environment — the master key lives in the
  environment (ADR 0006 territory: no secrets in the repository).
- The provider `code` is now immutable data, and the `llm_model` unique key changed shape (a migration the
  changelog performs with a backfill from the old enum values).
- The API layer, not the compiler, now guards provider misuse (unknown id → 404, in use → 409, missing key → 409 at
  call time), so those paths need their tests — they have them (`ProviderRegistryTest`, `SecretCipherTest`,
  `ModelRegistryTest`).

## Alternatives considered

- **Keep configuration, add more entries** — rejected: every new vendor is a redeployment, and one key per
  protocol cannot serve two accounts.
- **Store the key in plaintext in the database** — rejected: a dump, backup or log line would carry a working
  credential (spec v2 §10 requires environment or a secrets store, never committed).
- **External secrets manager (Vault, cloud KMS)** — deferred: right for a multi-tenant production deployment, far
  too much machinery for a single-operator tool; the `SecretCipher` port can be re-pointed at one later without
  touching the registry or the API.
