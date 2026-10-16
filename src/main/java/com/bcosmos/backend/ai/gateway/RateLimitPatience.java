package com.bcosmos.backend.ai.gateway;

import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How long a long-running sequence of calls is willing to sit out a rate limit (ADR 0041).
 *
 * <p>The gateway's budget bounds one call, and a provider asking to wait longer than that budget cannot be retried
 * inside it: the call fails with the wait it could not honour. That is the right answer for a single answer somebody
 * is waiting on, and the wrong one for the one flow that is a sequence — building the vector index of a run is
 * dozens of embedding calls over minutes, and a per-minute quota window is exactly the situation it has to survive.
 * So the sequence waits here instead, at its own level: it sleeps what the provider asked for, then makes a fresh
 * call with a fresh budget.</p>
 *
 * <p>Three things keep that from becoming an unbounded hold on a request:</p>
 *
 * <ul>
 *   <li>It waits only when the provider said how long. An uninstructed rate limit goes straight back to the caller,
 *       because guessing the length of a quota window is how a request ends up sleeping for no good reason.</li>
 *   <li>It waits only while the total stays inside the configured patience. A spent daily quota asks for hours, so
 *       the flow fails with the wait in the message instead of holding the request for them.</li>
 *   <li>It sits out a handful of windows at most. A provider that refuses ten times is not going to be persuaded by
 *       the eleventh wait, however small each one is.</li>
 * </ul>
 *
 * <p>One instance belongs to one sequence: {@link #call} adds up what it has waited so far. It is therefore not
 * thread-safe, and the flow that uses it creates one per operation rather than keeping one in a singleton.</p>
 */
public final class RateLimitPatience {

    private static final Logger log = LoggerFactory.getLogger(RateLimitPatience.class);

    /** The most windows one sequence sits out, so a provider repeating a tiny wait cannot spin here. */
    private static final int MAX_SITTINGS = 10;

    private final Duration patience;
    private final Sleeper sleeper;
    private Duration spent = Duration.ZERO;
    private int sittings;

    /**
     * @param patience     what the whole sequence may sit out, in total; zero (or a negative value) never waits
     * @param sleeper      how the wait is taken
     */
    public RateLimitPatience(Duration patience, Sleeper sleeper) {
        this.patience = patience == null || patience.isNegative() ? Duration.ZERO : patience;
        this.sleeper = sleeper;
    }

    /**
     * Runs one step of the sequence, sitting out the rate limits that the patience can cover.
     *
     * @param step one call to the provider, for example the embedding of one batch
     * @throws LlmProviderException the rate limit the patience could not outlast, or any other failure unchanged
     */
    public <T> T call(Supplier<T> step) {
        while (true) {
            try {
                return step.get();
            } catch (LlmProviderException failure) {
                Duration wait = waitAskedFor(failure);
                if (wait.isZero()) {
                    throw failure;
                }
                if (sittings >= MAX_SITTINGS || spent.plus(wait).compareTo(patience) > 0) {
                    log.warn("{}; this flow has sat out all the waiting it is allowed ({}), so it stops here",
                            failure.getMessage(), RetryWait.describe(patience));
                    throw failure;
                }
                spent = spent.plus(wait);
                sittings++;
                // The failure message already carries the wait and the provider's name, so this line does not
                // repeat them (and never quotes a response body).
                log.info("{}; sitting out the window and retrying", failure.getMessage());
                sleep(wait);
            }
        }
    }

    /** The wait a rate limit asked for, {@link Duration#ZERO} for anything else and for a rate limit that said nothing. */
    private static Duration waitAskedFor(LlmProviderException failure) {
        return LlmErrorCode.RATE_LIMITED.equals(failure.errorCode()) ? failure.instructedWait() : Duration.ZERO;
    }

    private void sleep(Duration wait) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException(LlmErrorCode.PROVIDER_ERROR,
                    "interrupted while waiting out a rate limit", false, interrupted);
        }
    }

    /** How the wait is taken. The real one blocks the calling thread, which is what a synchronous flow does. */
    @FunctionalInterface
    public interface Sleeper {

        void sleep(Duration wait) throws InterruptedException;

        Sleeper THREAD = wait -> Thread.sleep(wait.toMillis());
    }
}
