package com.bcosmos.backend.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Sitting out a rate limit at the level of a long-running sequence: only when the provider said how long, only
 * while the flow can afford it, and never more than a handful of times (ADR 0041).
 */
class RateLimitPatienceTest {

    private final List<Duration> waits = new ArrayList<>();

    @Test
    void retriesAStepAfterTheWaitTheProviderAskedFor() {
        AtomicInteger calls = new AtomicInteger();
        RateLimitPatience patience = patience(Duration.ofMinutes(2));

        String result = patience.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw rateLimited(Duration.ofSeconds(30));
            }
            return "indexed";
        });

        assertThat(result).isEqualTo("indexed");
        assertThat(calls).hasValue(3);
        assertThat(waits).containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(30));
    }

    /** The whole point of the class: 45s of waiting is fine for a build, whatever the per-call budget says. */
    @Test
    void waitsLongerThanOneCallBudgetWithoutTouchingIt() {
        AtomicInteger calls = new AtomicInteger();
        RateLimitPatience patience = patience(Duration.ofMinutes(2));

        patience.call(() -> {
            if (calls.incrementAndGet() == 1) {
                throw rateLimited(Duration.ofSeconds(45));
            }
            return "ok";
        });

        assertThat(waits).containsExactly(Duration.ofSeconds(45));
    }

    @Test
    void givesUpWhenTheWaitWouldExceedThePatienceOfTheFlow() {
        AtomicInteger calls = new AtomicInteger();
        RateLimitPatience patience = patience(Duration.ofSeconds(60));

        assertThatThrownBy(() -> patience.call(() -> {
            calls.incrementAndGet();
            throw rateLimited(Duration.ofSeconds(90));
        })).isInstanceOfSatisfying(LlmProviderException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.RATE_LIMITED));

        // Nothing was waited for and nothing was sent again: the answer is the flow failing, not a request held.
        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void givesUpOnceItHasSatOutTheWindowsItIsAllowed() {
        AtomicInteger calls = new AtomicInteger();
        RateLimitPatience patience = patience(Duration.ofHours(1));

        assertThatThrownBy(() -> patience.call(() -> {
            calls.incrementAndGet();
            throw rateLimited(Duration.ofMillis(100));
        })).isInstanceOf(LlmProviderException.class);

        // A provider repeating a tiny wait must not spin here, however much patience is left.
        assertThat(calls).hasValue(11);
        assertThat(waits).hasSize(10);
    }

    /** No instruction means no guess: the failure goes straight back to the caller. */
    @Test
    void doesNotWaitForARateLimitThatDidNotSayHowLong() {
        AtomicInteger calls = new AtomicInteger();
        RateLimitPatience patience = patience(Duration.ofMinutes(2));

        assertThatThrownBy(() -> patience.call(() -> {
            calls.incrementAndGet();
            throw rateLimited(Duration.ZERO);
        })).isInstanceOf(LlmProviderException.class);

        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void passesEveryOtherFailureStraightThrough() {
        AtomicInteger calls = new AtomicInteger();
        RateLimitPatience patience = patience(Duration.ofMinutes(2));

        assertThatThrownBy(() -> patience.call(() -> {
            calls.incrementAndGet();
            throw new LlmProviderException(LlmErrorCode.PROVIDER_ERROR, "gemini answered HTTP 500", true);
        })).isInstanceOfSatisfying(LlmProviderException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.PROVIDER_ERROR));

        // A 5xx is the gateway's business: its retries and its budget already cover it.
        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void waitsForNothingWhenThePatienceIsZero() {
        AtomicInteger calls = new AtomicInteger();
        RateLimitPatience patience = patience(Duration.ZERO);

        assertThatThrownBy(() -> patience.call(() -> {
            calls.incrementAndGet();
            throw rateLimited(Duration.ofSeconds(1));
        })).isInstanceOf(LlmProviderException.class);

        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void stopsAtAnInterruptedWaitInsteadOfSleepingOn() {
        RateLimitPatience patience = new RateLimitPatience(Duration.ofMinutes(2), wait -> {
            throw new InterruptedException("the request was cancelled");
        });

        assertThatThrownBy(() -> patience.call(() -> {
            throw rateLimited(Duration.ofSeconds(5));
        })).isInstanceOfSatisfying(LlmProviderException.class, failure -> {
            assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.PROVIDER_ERROR);
            assertThat(failure.retryable()).isFalse();
        });

        // The interrupt is handed back to the caller rather than swallowed by the wait.
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    private RateLimitPatience patience(Duration patience) {
        return new RateLimitPatience(patience, waits::add);
    }

    private static LlmProviderException rateLimited(Duration wait) {
        return new LlmProviderException(LlmErrorCode.RATE_LIMITED, "gemini rate limited the call (HTTP 429)", true,
                wait, null);
    }
}
