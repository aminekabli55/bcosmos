# ADR 0040 — A rate limit is its own failure, and the provider's own wait beats our backoff

- **Status:** Accepted (post-Phase 11 follow-up)
- **Date:** 2026-10-16
- **Related:** [ADR 0019](ADR-0019-ai-gateway.md) point 8 (budget first, retries second — refined here),
  [ADR 0021](ADR-0021-rag-and-validated-suggestions.md) (the embedding index), spec v2 sections 7.1, 7.2 and 12

## Context

Building the vector index of a run is a sequence of embedding calls, one per batch of 16 chunks. On a free tier a
real run hit the provider's per-minute allowance part-way through:

```
15:39:36.804 embedding on gemini/gemini-embedding-2 finished in 1657 ms after 1 attempt(s), 16 vector(s)
...
15:39:43.898 embedding on gemini/gemini-embedding-2 finished in 1378 ms after 1 attempt(s), 16 vector(s)
15:39:46.449 WARN EMBED call on gemini/gemini-embedding-2 failed after 3 attempt(s): provider-error - gemini
             answered HTTP 429; the call may be retried
```

Seven batches of 16 in about eleven seconds is what the quota counts against, so the seventh was refused. Three
things were wrong with how that was handled:

1. **The retry could not succeed.** `bcosmos.ai.initial-backoff` is 500 ms and doubles, so all three attempts were
   spent inside 2.5 seconds. A per-minute quota window is still closed at that point, which means the two extra
   attempts were only two more refusals — requests sent and paid for, for nothing.
2. **The provider had already said how long to wait**, and the gateway threw it away: Google reports
   `RetryInfo.retryDelay` in the error body of a 429 (an OpenAI-compatible provider reports `Retry-After` in the
   header), and `ProviderHttp` read neither.
3. **The failure was indistinguishable from a broken provider.** Every 429 became `provider-error`, the same code as
   a 500, so neither the operator nor the usage table could tell "the quota is spent, come back in a minute" from
   "the provider is failing".

The user-visible result was a 502 whose message offered a retry that would fail in exactly the same place: an index
build is all-or-nothing (`EmbeddingService` stores only after every batch has been embedded), so a repository that
does not fit the tier's per-minute allowance could never be indexed, however often it was re-run.

## Decision

1. **A rate limit is its own error code.** `LlmErrorCode.RATE_LIMITED` (`rate-limited`) replaces `PROVIDER_ERROR`
   for HTTP 429. Transport errors and 5xx keep `provider-error`. `LlmUsageOutcome` is unchanged: the outcome is
   still `PROVIDER_ERROR`, and the code column is where the distinction lives, so no migration is needed and nothing
   that reads outcomes has to learn a new value.
2. **The failure carries the provider's own wait.** `LlmException.instructedWait()` holds it, and the message quotes
   it ("it asks to wait 30s before the next attempt"), so the operator reads the number instead of guessing.
3. **Reading it is one small, defensive class.** `RetryWait` takes the `Retry-After` header when there is one
   (delta-seconds or an HTTP date) and otherwise Google's `RetryInfo` detail from the error body. Anything missing,
   unreadable, negative or absurd becomes "no instruction" and the configured backoff decides. The body is read for
   that single duration and nothing else: it is never logged, never quoted in a message, and never stored, so the
   rule of ADR 0019 — an error body can echo the request, and the request carries source code — still holds.
4. **Our backoff yields to that wait, but only when the call can afford it.** The retry interval is the instructed
   wait when it fits inside `bcosmos.ai.timeout`, otherwise the exponential backoff. A provider asking for an hour
   must not hold an HTTP request for an hour, and a shorter budget must not be spent sleeping either.
5. **Giving up says why.** When the budget is gone before another attempt can be made, the gateway reports a rate
   limit with the wait it could not honour ("asked to wait 1s, longer than the 1s budget of one call
   (bcosmos.ai.timeout, BCOSMOS_AI_TIMEOUT); re-run it once the window has passed"), and only falls back to
   `Model call timed out` when it has nothing better to say. That terminal failure is marked non-retryable, because
   the budget that made the retry impossible is not going to grow.

## Consequences

- A rate limit with a wait the budget can absorb — the common per-minute case — is now survived by the call itself
  instead of failing the whole request: the retry sleeps what the provider asked for and proceeds.
- When the wait genuinely does not fit, the operator learns the cause and the length of the wait from the 502,
  instead of a timeout that hid both. Raising `BCOSMOS_AI_TIMEOUT` is then a decision they can take knowingly.
- The expiry of a **daily** quota is still a failure: retrying it cannot succeed, and the message now says so
  instead of looking like a transient provider fault.
- Only the retry timing changed. The attempt count, the budget check before each attempt, redaction, schema
  validation and usage recording are untouched, and `POST /api/findings/{id}/suggestions` still answers 502 with a
  problem detail.
- **Not solved here, deliberately:** a repository whose index needs more embedding calls than the tier allows per
  minute still failed part-way, and because the build is all-or-nothing it started over on the next attempt. The
  wait this ADR reads is put to work one level up by [ADR 0041](ADR-0041-rate-limit-patience-for-a-sequence.md),
  which lets the index build sit a window out; storing partial indexes is still open.
