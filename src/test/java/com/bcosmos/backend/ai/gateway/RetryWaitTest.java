package com.bcosmos.backend.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

/**
 * Reading a provider's own retry instruction: the header form, Google's body form, and everything unusable.
 *
 * <p>The unusable half matters as much as the usable one: this input is a response body from a provider, so every
 * shape it does not expect has to end in "no instruction" rather than in a wait nobody asked for.</p>
 */
class RetryWaitTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readsTheRetryAfterHeaderAsASecondsValue() {
        assertThat(RetryWait.instructed(headers("30"), null, JSON)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void readsTheRetryAfterHeaderAsAnHttpDate() {
        String until = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(45));

        Duration wait = RetryWait.instructed(headers(until), null, JSON);

        assertThat(wait).isBetween(Duration.ofSeconds(40), Duration.ofSeconds(50));
    }

    @Test
    void hasNoInstructionWhenTheHeaderCannotBeRead() {
        assertThat(RetryWait.instructed(headers("soon"), null, JSON)).isZero();
        assertThat(RetryWait.instructed(headers("-5"), null, JSON)).isZero();
        assertThat(RetryWait.instructed(headers("0"), null, JSON)).isZero();
        assertThat(RetryWait.instructed(headers("  "), null, JSON)).isZero();
        assertThat(RetryWait.instructed(new HttpHeaders(), null, JSON)).isZero();
    }

    @Test
    void readsGooglesRetryInfoDetailOutOfTheErrorBody() {
        Duration wait = RetryWait.instructed(null, googleBody("17s"), JSON);

        assertThat(wait).isEqualTo(Duration.ofSeconds(17));
    }

    @Test
    void readsAFractionalProtobufDuration() {
        assertThat(RetryWait.parseDuration("1.5s")).isEqualTo(Duration.ofMillis(1500));
        assertThat(RetryWait.parseDuration("42s")).isEqualTo(Duration.ofSeconds(42));
        assertThat(RetryWait.parseDuration(" 42s ")).isEqualTo(Duration.ofSeconds(42));
    }

    @Test
    void hasNoInstructionWhenADurationIsNotAProtobufOne() {
        assertThat(RetryWait.parseDuration("PT42S")).isZero();
        assertThat(RetryWait.parseDuration("42")).isZero();
        assertThat(RetryWait.parseDuration("")).isZero();
        assertThat(RetryWait.parseDuration(null)).isZero();
        assertThat(RetryWait.parseDuration("soon")).isZero();
    }

    @Test
    void hasNoInstructionWhenTheBodyDoesNotCarryOne() {
        assertThat(RetryWait.instructed(null, "{\"error\": {\"code\": 429}}", JSON)).isZero();
        assertThat(RetryWait.instructed(null, "{\"error\": {\"details\": {\"retryDelay\": \"9s\"}}}", JSON))
                .isZero();
        assertThat(RetryWait.instructed(null, "not json at all", JSON)).isZero();
        assertThat(RetryWait.instructed(null, null, JSON)).isZero();
    }

    @Test
    void prefersTheHeaderOverTheBody() {
        // An OpenAI-compatible provider states it in the header; a Google-shaped body must not override it.
        assertThat(RetryWait.instructed(headers("5"), googleBody("60s"), JSON)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void ignoresADurationThatIsNotACredibleWait() {
        // The body is untrusted input: a duration nobody would ever mean is dropped rather than waited out.
        assertThat(RetryWait.instructed(null, googleBody("1000000s"), JSON)).isZero();
        assertThat(RetryWait.instructed(headers("99999999"), null, JSON)).isZero();
    }

    @Test
    void describesAWaitTheWayAMessageReadsIt() {
        assertThat(RetryWait.describe(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(RetryWait.describe(Duration.ofSeconds(150))).isEqualTo("2m 30s");
        assertThat(RetryWait.describe(Duration.ofMinutes(65))).isEqualTo("1h 5m");
        assertThat(RetryWait.describe(Duration.ofHours(50))).isEqualTo("2d 2h");
    }

    private static HttpHeaders headers(String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
        return headers;
    }

    /** The shape the Gemini API answers a 429 with: the wait lives in the details, not in a header. */
    private static String googleBody(String retryDelay) {
        return """
                {
                  "error": {
                    "code": 429,
                    "message": "You exceeded your current quota",
                    "status": "RESOURCE_EXHAUSTED",
                    "details": [
                      {"@type": "type.googleapis.com/google.rpc.QuotaFailure", "violations": []},
                      {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "%s"}
                    ]
                  }
                }
                """.formatted(retryDelay);
    }
}
