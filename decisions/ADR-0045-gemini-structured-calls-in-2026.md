# ADR 0045 — Gemini structured calls in 2026: a content turn, a reachable model, a realistic budget

- **Status:** Accepted (AI follow-up after Phase 11)
- **Date:** 2026-10-19
- **Related:** [ADR 0038](ADR-0038-provider-registry-in-the-database.md), [ADR 0040](ADR-0040-rate-limit-backoff.md),
  [ADR 0044](ADR-0044-bounded-resumable-index-builds.md), changeset `0027-drop-model-default` (the registry has no
  stored default; the screen now selects an enabled row itself)

## Context

With the index build fixed, "Suggest a fix" still failed on the real repository, and the failures were not about the
prompt or the code. Three separate defects were in the way, each visible only after the previous one was removed.

1. **HTTP 400 on every structured call.** Every structured prompt in the codebase is a single system message
   (`StructuredRequest.of(modelId, List.of(ChatMessage.system(prompt)), …)`). `GeminiAdapter` moved system messages
   into `systemInstruction` and left `contents` empty. Gemini requires at least one turn in `contents`; a system
   instruction alone is not a conversation. All eight structured calls ever recorded were rejected with HTTP 400.
2. **HTTP 404 after that was fixed.** `gemini-2.5-flash` is seeded by changeset 0008. Google announced in September
   2026 that access to the 2.5 family is limited to projects that used it in the past; new projects receive
   `404 NOT_FOUND` from `generateContent` even though `models.list` still advertises the model, and Google points
   new projects at **3.8 Flash** or **3.5 Flash-Lite**
   ([discussion](https://discuss.ai.google.dev/t/gemini-2-5-flash-model-metadata-returns-200-but-generatecontent-returns-404/184203),
   [models](https://ai.google.dev/gemini-api/docs/models)).
3. **HTTP 504 once the model answered.** `bcosmos.ai.timeout` was 30 s. `gemini-3.8-flash` answered one suggestion in
   20 s and exceeded 30 s on the next finding of the same repository, so a slow answer became a gateway timeout. The
   first attempt also hit the 2,048 output-token cap and was truncated mid-JSON, which the gateway saw as "not valid
   JSON" and retried.

## Decision

1. **A system-only prompt gets a neutral user turn.** `GeminiAdapter` adds one content part ("Follow the instructions
   above and answer.") when the request contains no user or assistant message. It adds no task of its own; the whole
   prompt still lives in `systemInstruction`. `OpenAiCompatibleAdapter` is unchanged: a system message alone is valid
   there.
2. **The seed registers a model Google currently serves.** Changeset `0028-seed-current-gemini-chat-model` inserts
   `gemini-3.8-flash` for the `gemini` provider, **disabled**, like every seeded model — the per-model opt-in of spec
   §10 remains. The user's `gemini-2.5-flash` row is untouched: registry rows are user data, and a project that does
   have 2.5 access keeps it.
3. **The screen selects the newest enabled row of each kind.** With no stored default (ADR 0042), "registry default"
   was an option that could only fail. The chat and embedding selects now start on the highest-id enabled row, so a
   newly enabled model is what "Suggest a fix" uses even though the old seeded row keeps id 1.
4. **A structured answer gets a realistic budget.** `bcosmos.ai.timeout` defaults to **90 s** and
   `bcosmos.ai.max-output-tokens` to **4096**. Embedding calls still finish in about a second; the larger output cap
   is what a summary + explanation + citations + diff actually needs.

## Consequences

- A suggestion on the 458-file repository now returns `PROPOSED` with `gemini/gemini-3.8-flash`: 200 in 32 s on
  finding 1036 and 57 s on finding 1037 (which needed one schema retry), with the index at 288 and 480 of 1,636
  chunks respectively.
- The 90 s budget is a ceiling, not a target. A slow reasoning model can now take up to 90 s per attempt; a retry
  starts only if the total budget allows another attempt, so the request still ends.
- `schemaOk` in the verdict remains "the first answer needed no retry", as its own documentation says. A suggestion
  that had to be corrected shows `schema: ✗` although the stored answer satisfies the schema. This is a known
  reporting quirk, not a failed suggestion.
- Model names age. The registry is data, so a future replacement is a Settings action (`POST /api/models`) plus, when
  it affects every install, another seed changeset — not a code change.
- Not verified here: the backend test suite does not compile in this checkout (68 errors in six pre-existing AI test
  files that still assume the removed default-model flag), so this work was verified through the running API against
  the real repository and database, plus the frontend suite and production build (see AGENTS.md).
