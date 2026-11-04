# ADR 0019 — The AI gateway: one client, provider adapters, opt-in, and validated JSON

- **Status:** Accepted (Phase 2)
- **Date:** 2026-10-13
- **Related:** [ADR 0007](ADR-0007-llm-client-gateway.md) (provider-neutral `LlmClient`), [ADR 0013](ADR-0013-hosted-llm-apis.md)
  (hosted APIs only), spec v2 sections 7.1, 8, 10 and 11

## Context

Phase 2 needs one interface that business code can call, several providers behind it, a registry that says which
models may be used, usage accounting that survives failures, structured output that is actually validated, and a
guarantee that credentials never leave the process. ADR 0007 already fixed the shape (`LlmClient` with `chat`,
`structured`, `embed`); this ADR records how the implementation lives up to it.

## Decision

1. **Policy in one place, HTTP in the adapters.** `LlmGateway` owns model resolution, the opt-in check, redaction,
   retries, the time budget, schema validation, the schema retry and usage recording. A `LlmProviderAdapter` owns
   one provider's endpoint, auth header, request body and response shape. Adding a provider is one class plus one
   bean; it cannot silently behave differently from the others because it does not implement any of the policy.
2. **Two gates before code leaves the machine.** A call needs the AI layer to be on *and* an enabled registry row
   (`llm_model.enabled`). Enabling a row whose provider has no key in the environment is refused at edit time
   (409 with the variable name), not at call time. The master switch started as the `bcosmos.ai.enabled` property
   only; it is now a stored setting the Settings screen owns, with the property as its default
   ([ADR 0020](ADR-0020-stored-ai-switch.md)).
3. **Credentials stay in the environment.** `ProviderCredentials` is the only class that returns a key value; it
   reads `bcosmos.ai.providers.*.api-key`, which is populated from `GEMINI_API_KEY` / `OPENAI_API_KEY`
   placeholders. No key is stored in the database, returned by an API (models report
   `credentialConfigured` + `credentialEnvironmentVariable` instead) or written to a log.
4. **Redaction before every send.** `SecretRedactor` masks the configured keys and the well-known key shapes
   (bearer tokens, `AIza…`, `sk-…`, GitHub, AWS, Slack, JWTs, PEM blocks, `token: "…"` assignments) in every message
   the gateway sends. It errs towards redaction: losing a constant in an explanation is acceptable, leaking a live
   key is not.
5. **Structured output is validated, retried once, and both attempts are reported.** The schema travels in the
   prompt, the answer is parsed and validated, and a violation is retried exactly once with the validation errors
   attached. Both attempts reach the provider and both are visible: the response carries `rejectedAttempts`, and the
   usage row carries `attempts`. A second failure is a 502, never a half-validated answer.
6. **A hand-written JSON Schema subset, documented rather than implied.** `JsonSchemaValidator` implements `type`
   (string or list), `required`, `properties`, `items` and `enum`, and ignores everything else — so prompt schemas
   only state constraints that are really checked. Adding a full JSON Schema library is a follow-up decision, not a
   silent expectation.
7. **`llm_usage` is a first-class table.** One row per call, written in its own transaction
   (`REQUIRES_NEW`) so usage survives a rollback, and written for failures too (`PROVIDER_ERROR`, `TIMEOUT`,
   `SCHEMA_INVALID`). Tokens, latency, attempts, estimated cost, prompt version, provider and model name are stored;
   the provider/model name are denormalized so a row stays readable after a registry edit, and `model_id` is
   `ON DELETE SET NULL`.
8. **Budget first, retries second.** Retries use Resilience4j with exponential backoff and only for failures marked
   retryable (HTTP 429/5xx, transport errors) — never for a rejected credential or a rejected prompt. The total
   budget is checked *before* each attempt, so a retry cannot add another full timeout to the caller's wait, and the
   per-attempt guard is the HTTP client's read timeout.
9. **Prompt files live with the backend.** Spec section 7.1 says prompts live in `ai-lab/prompts/`. That is kept for
   experiment and evaluation prompts, but the *runtime* templates are
   `backend/src/main/resources/prompts/<name>.<version>.md` with a sibling `<name>.<version>.json` schema: the
   backend must build and run without the `ai-lab` repository, which is a separate Git repository (ADR 0016). The
   version is stored on every usage row (`explain-finding.v1`).
10. **`llm_model.is_default`** is an addition to the spec's column list: the settings screen needs a persisted
    default, and the registry keeps the "at most one default" invariant itself.

## Consequences

- A third provider is a small, reviewable adapter; the retry/redaction/validation behaviour is inherited.
- Tests never need a real API: `wiremock-standalone` fakes both provider shapes, and the gateway tests drive
  retries, timeouts, schema retries and redaction through the real HTTP path.
- The `structured` path always makes at most two provider calls; cost is bounded and accounted for.
- Until a full JSON Schema library is added, a schema keyword the validator ignores is not enforced — the schemas
  in `backend/src/main/resources/prompts/` are written accordingly, and a test pins that behaviour.
- The global AI switch is live-editable from the Settings screen: `LlmGateway` asks an `AiEnablement` port, and
  `settings.AiSwitch` answers it from the `app_setting` row with the deployment property as the fallback
  ([ADR 0020](ADR-0020-stored-ai-switch.md)). A deployment that needs a switch the browser can never turn back on
  still has no mechanism, which is recorded there as a follow-up.
