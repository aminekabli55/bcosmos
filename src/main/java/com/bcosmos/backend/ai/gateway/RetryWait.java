package com.bcosmos.backend.ai.gateway;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * How long a provider asked us to wait before calling again, and how to say that in a message.
 *
 * <p>Two shapes carry it in practice. The {@code Retry-After} header (a number of seconds, or an HTTP date) is what
 * the OpenAI-compatible providers send; Google reports it in the error body instead, as a {@code RetryInfo} detail
 * holding a protobuf duration ({@code "retryDelay": "42s"}). Both are read defensively: anything missing,
 * unreadable or negative means "no instruction", and the caller keeps its own backoff.</p>
 *
 * <p>Following that instruction is what separates a retry that works from one that cannot: the gateway's exponential
 * backoff comes back within a second or two, which is far too early to clear a per-minute quota window (ADR 0040).</p>
 *
 * <p>The error body is read for that one duration and nothing else. It is never logged, never quoted in a message
 * and never stored: an error body can echo the request, and the request carries source code (spec v2 section
 * 10).</p>
 */
final class RetryWait {

    /**
     * The longest wait we are willing to read out of a provider response.
     *
     * <p>A body is untrusted input and a mistyped duration can say {@code 1000000s}; a value like that is dropped
     * rather than carried around. It is only a sanity bound on parsing: whether a wait is worth sleeping for is the
     * retry policy's decision, against the call's budget.</p>
     */
    private static final Duration MAX = Duration.ofDays(7);

    private static final String RETRY_INFO = "google.rpc.RetryInfo";

    private RetryWait() {
    }

    /**
     * The wait the provider asked for: the header when it is there, otherwise the {@code RetryInfo} detail of a
     * Google error body.
     *
     * @return the wait, or {@link Duration#ZERO} when the provider said nothing usable
     */
    static Duration instructed(HttpHeaders headers, String body, ObjectMapper json) {
        Duration header = parseHeader(headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER));
        return header.isZero() ? fromRetryInfo(body, json) : header;
    }

    /**
     * Reads the {@code Retry-After} header, which is either a number of seconds ({@code 30}) or an HTTP date.
     *
     * <p>Both forms are legitimate (RFC 9110) and appear in the wild, so a value that is not a number is tried as a
     * date rather than discarded.</p>
     */
    static Duration parseHeader(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        String trimmed = value.trim();
        try {
            return clamped(Duration.ofSeconds(Long.parseLong(trimmed)));
        } catch (NumberFormatException notSeconds) {
            // Not a delta-seconds value; the date form is tried below.
        }
        try {
            ZonedDateTime until = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            // A date in the past means "come back now", which the caller's own backoff handles.
            return clamped(Duration.between(ZonedDateTime.now(ZoneOffset.UTC), until));
        } catch (DateTimeParseException notADate) {
            return Duration.ZERO;
        }
    }

    /** {@code error.details[].retryDelay} of a Google error body. */
    private static Duration fromRetryInfo(String body, ObjectMapper json) {
        if (body == null || body.isBlank() || json == null) {
            return Duration.ZERO;
        }
        try {
            JsonNode details = json.readTree(body).path("error").path("details");
            if (!details.isArray()) {
                return Duration.ZERO;
            }
            for (JsonNode detail : details) {
                if (!detail.path("@type").asText("").endsWith(RETRY_INFO)) {
                    continue;
                }
                return parseDuration(detail.path("retryDelay").asText(""));
            }
        } catch (RuntimeException unreadable) {
            // An error body we cannot read is not a failure of its own: we simply have no instruction, and the
            // caller falls back to its configured backoff.
        }
        return Duration.ZERO;
    }

    /** A protobuf duration: a decimal count of seconds with a trailing {@code s} ({@code "42s"}, {@code "1.5s"}). */
    static Duration parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        String trimmed = value.trim();
        if (!trimmed.endsWith("s")) {
            return Duration.ZERO;
        }
        try {
            double seconds = Double.parseDouble(trimmed.substring(0, trimmed.length() - 1));
            return clamped(Duration.ofMillis(Math.round(seconds * 1000)));
        } catch (NumberFormatException notANumber) {
            return Duration.ZERO;
        }
    }

    /** A wait that is negative (the window is already open) or longer than we accept is no instruction at all. */
    private static Duration clamped(Duration wait) {
        if (wait.isNegative() || wait.isZero() || wait.compareTo(MAX) > 0) {
            return Duration.ZERO;
        }
        return wait;
    }

    /** The wait as a message reads it: {@code 45s}, {@code 2m 30s}, {@code 1h 5m}. */
    static String describe(Duration wait) {
        long seconds = wait.toSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3_600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        if (seconds < 86_400) {
            return (seconds / 3_600) + "h " + ((seconds % 3_600) / 60) + "m";
        }
        return (seconds / 86_400) + "d " + ((seconds % 86_400) / 3_600) + "h";
    }
}
