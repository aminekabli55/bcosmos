package com.bcosmos.backend.ai.rag;

/**
 * Estimates how many tokens a piece of code costs, without shipping a tokenizer for every provider.
 *
 * <p>It is a heuristic on purpose and it is labelled as one wherever it is shown: roughly four characters per
 * token, which is close enough for code and stable across providers. The alternative — counting tokens with the
 * exact model tokenizer — would mean a tokenizer dependency per provider, and the number is only used to keep a
 * prompt inside a budget. Every prompt size the UI shows says "estimated" (spec v2 section 9: no fabricated
 * metrics).</p>
 */
public final class TokenEstimator {

    /** Characters per token used by the estimate. */
    public static final int CHARS_PER_TOKEN = 4;

    private TokenEstimator() {
    }

    /** Estimated tokens of a text, rounded up; 0 for null or empty. */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }
}
