# ADR 0020 — The AI master switch is a stored setting, with the deployment property as its default

- **Status:** Accepted (Phase 2)
- **Date:** 2026-10-14
- **Related:** [ADR 0007](ADR-0007-llm-client-gateway.md) (the `LlmClient` gateway),
  [ADR 0019](ADR-0019-ai-gateway.md) (the rest of the gateway's policy), spec v2 sections 9 and 10

## Context

The gateway has always had a global switch (`bcosmos.ai.enabled`, spec section 10: "the whole AI layer can be turned
off; analysis still works"). It was a deployment property only, so the Settings screen could display it but not change
it, and ADR 0019 recorded that as a known gap: "toggling it from the browser needs a persisted setting".

The switch is security-relevant: with it on, prompts — including code snippets — leave the machine. With it off, the
gateway refuses before resolving a model, so no provider is called and nothing is billed.

## Decision

1. **One key/value table, not one table per switch.** Liquibase changeset `0010-create-app-setting` adds
   `app_setting (setting_key PK, setting_value, updated_at)`. The Settings screen also has to hold ignore paths
   later (spec section 9), and a second table for that is not warranted. The table holds no secrets: credentials stay
   in environment variables (ADR 0007, spec section 10).
2. **The property stays the default; a stored row overrides it.** No row means "follow `bcosmos.ai.enabled`", which
   keeps the dev profile on and the global file off. `PUT /api/settings/ai` with no `enabled` value clears the row,
   which is how the screen offers "follow the deployment default" without a second endpoint.
3. **The gateway depends on a one-method port, not on the properties.** `LlmGateway.requireEnabled` consults
   `AiEnablement` (`boolean enabled()`), and `settings.AiSwitch` implements it over the store. A second implementation
   would be a policy change; the port keeps the dependency direction clean (`ai.gateway` never imports `settings`).
4. **The value is read per call, not cached.** A switch flipped in the browser takes effect on the next request with
   no restart, which is the whole point. The cost is one primary-key read per model call — negligible next to the
   call, and it removes an entire class of "why is it still on?" questions.
5. **The response says where the value came from.** `GET /api/settings/ai` returns `enabled`, `propertyDefault`,
   `overridden` and `updatedAt`, so the screen can label a stored value as an override and an inherited one as the
   deployment default instead of guessing.

## Consequences

- The Settings screen can turn the AI layer on and off, and the API is the only place the decision lives:
  `GET /api/settings/ai`, `PUT /api/settings/ai`.
- A deployment that wants a hard off switch no longer gets one from the property alone; that would need a
  kill-switch layered above the stored value, which is a deliberate follow-up if a shared deployment needs it.
- Tests must clean up after themselves: a test that leaves the layer switched off in a shared container changes the
  outcome of whatever runs next, so `FindingExplanationIntegrationTest` deletes the row after every test.
- `app_setting` is the first table that is configuration rather than analysis data; the ignore paths of spec section 9
  will join it.
