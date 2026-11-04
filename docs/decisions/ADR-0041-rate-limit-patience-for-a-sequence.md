# ADR 0041 — A long-running sequence sits out its own rate limits

- **Status:** Accepted (post-Phase 11 follow-up)
- **Date:** 2026-10-16
- **Related:** [ADR 0040](ADR-0040-rate-limit-backoff.md) (the provider's wait is read and reported; this ADR is what
  uses it), [ADR 0021](ADR-0021-rag-and-validated-suggestions.md) (the embedding index), spec v2 section 7.2

## Context

ADR 0040 made a 429 its own failure and taught the gateway to retry after the wait the provider itself asks for —
as long as that wait fits inside `bcosmos.ai.timeout`. It also recorded the case that is left over, and the next run
produced it:

```
16:02:09 WARN EMBED call on gemini/gemini-embedding-2 failed after 2 attempt(s): rate-limited - gemini rate
         limited the call (HTTP 429); it asks to wait 46s before the next attempt
Model call failed — gemini rate limited the call (HTTP 429); it asks to wait 46s before the next attempt
```

46 s does not fit a 30 s budget, so the retry was refused inside the call — deliberately, because one call must not
hold a request for minutes on a provider's say-so. The message told the operator to re-run once the window had
passed, and for a *single* answer that is exactly right.

For the flow that produced it, it is not. Building the vector index of a run is a sequence of embedding calls that
already takes minutes, and `EmbeddingService` stores nothing unless every batch succeeded. So re-running re-embeds
the same 100-odd chunks, reaches the same quota wall at the same batch, and fails in the same place with the same
message: a repository that does not fit the tier's per-minute allowance could never be indexed at all, however often
it was asked. The unit of policy was wrong: a per-call budget is the right bound for one call, not for a batch job
that happens to be driven by a request.

## Decision

1. **The wait belongs to the sequence.** A new `RateLimitPatience` (in the AI gateway package, beside the retry
   policy it completes) wraps one step of a long-running sequence: it catches a `RATE_LIMITED` failure, sleeps what
   the provider asked for, and calls again — a fresh call with a fresh budget, since the previous call's budget was
   spent and rightly so. The gateway itself is unchanged: `bcosmos.ai.timeout` still bounds one call, and a single
   answer somebody is waiting on still fails fast rather than sleeping through a quota window.
2. **Waiting is bounded three ways.** It waits only when the provider stated how long (no guessed window lengths);
   only while the whole sequence stays inside `bcosmos.retrieval.rate-limit-patience`
   (`BCOSMOS_RETRIEVAL_RATE_LIMIT_PATIENCE`, default 2m), which covers any per-minute allowance; and at most ten
   times, so a provider repeating a tiny wait cannot spin here. A wait that does not fit fails the flow with the
   provider's own number in the message, as ADR 0040 decided — a spent daily quota is not waited out, it is reported.
3. **One patience per index build.** The instance accumulates what the sequence has waited, so it is created inside
   `ensureIndex` for that build rather than kept in the singleton service: two index builds never share a budget for
   waiting, and the class is documented as belonging to one sequence.
4. **Only the embedding sequence uses it.** It is not a wrapper around every call. An explanation or a suggestion
   chat call is a single answer a user is waiting for, and making those patient would trade a fast, clear failure for
   a slow one.
5. **Nothing new is stored and no migration is needed.** Every refused call is still a usage row with
   `error_code = rate-limited`, so the waits a build sat out are visible afterwards.

## Consequences

- A repository whose index needs more calls than the tier allows per minute now completes: the first refusal costs
  the wait the provider asked for, and the build continues from the batch it was on.
- An index build can now take up to `rate-limit-patience` longer than the provider needs, which is a request that
  runs for minutes in any case; the setting is the knob, and `0` turns the waiting off entirely.
- The failed-attempt cost is recorded: the refuses that led to the wait are usage rows, so a leaderboard of model
  reliability sees them.
- A daily quota still fails the build, in the same place, with the wait named — now at the sequence level, where the
  operator also reads that the flow sat out what it was allowed to.
- **Still open, and now more visible:** the index build is all-or-nothing. It survives a window, but an interrupted
  build (a restart, a connection dropped by the browser) still starts over. Storing the batches that succeeded would
  turn the patience into progress rather than into a longer attempt.
