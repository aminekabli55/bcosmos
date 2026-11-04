package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.config.AiProperties;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The parts both adapters share: how to build a client with the configured timeouts, how to read a JSON answer
 * defensively, and how to turn an HTTP failure into a {@link LlmException} the gateway can retry or report.
 *
 * <p>Error messages carry the provider code and the status code only. Response bodies never reach a message or a log
 * line: an error body can echo the request, and the request contains source code (spec v2 section 10).</p>
 */
final class ProviderHttp {

    private ProviderHttp() {
    }

    /**
     * A client with the configured connect and read timeouts.
     *
     * <p>The read timeout is the per-attempt guard; the total budget is enforced by {@link LlmGateway} before each
     * attempt, so retries can never stretch a call past the configured time.</p>
     */
    static RestClient client(AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        // Built here rather than injected: each adapter owns the timeouts of its own provider, and the bodies are
        // plain JSON strings, so no message-converter configuration is involved.
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * Maps a transport or HTTP failure to the gateway's error vocabulary.
     *
     * <p>The mapper is here for one thing only: a 429 can say when to come back, and Google says it in the error
     * body ({@code RetryInfo.retryDelay}) rather than in a header. {@link RetryWait} reads that one duration and
     * discards the rest of the body, so nothing of it can reach a message or a log line.</p>
     */
    static LlmException map(String provider, RuntimeException failure, ObjectMapper json) {
        if (failure instanceof LlmException llmFailure) {
            return llmFailure;
        }
        if (failure instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 401 || status == 403) {
                return new LlmProviderException(LlmErrorCode.UNAUTHORIZED,
                        provider + " rejected the credentials (HTTP " + status + ")", false, failure);
            }
            if (status == 429) {
                // A rate limit is the provider telling us when it will serve us again, not a broken provider.
                return LlmProviderException.rateLimited(provider,
                        RetryWait.instructed(response.getResponseHeaders(), response.getResponseBodyAsString(), json),
                        failure);
            }
            if (status == 408 || status >= 500) {
                return new LlmProviderException(LlmErrorCode.PROVIDER_ERROR,
                        provider + " answered HTTP " + status + "; the call may be retried", true, failure);
            }
            return new LlmProviderException(LlmErrorCode.PROVIDER_REJECTED,
                    provider + " rejected the request (HTTP " + status + ")", false, failure);
        }
        if (isTimeout(failure)) {
            return new LlmTimeoutException(provider + " did not answer within the configured timeout", failure);
        }
        return new LlmProviderException(LlmErrorCode.PROVIDER_ERROR,
                provider + " call failed (" + failure.getClass().getSimpleName() + ")", true, failure);
    }

    /** A provider that answered something unreadable is a failure, not an empty answer. */
    static LlmProviderException malformed(String provider, String detail) {
        return new LlmProviderException(LlmErrorCode.MALFORMED_RESPONSE,
                provider + " answered something unexpected: " + detail, true);
    }

    /** Follows a path in a JSON document, tolerating missing nodes (they become a JSON {@code null} node). */
    static JsonNode at(JsonNode node, String... path) {
        JsonNode current = node;
        for (String segment : path) {
            current = current.path(segment);
        }
        return current;
    }

    /** A text field, or {@code null} when it is missing, not textual or blank. */
    static String textAt(JsonNode node, String... path) {
        JsonNode candidate = at(node, path);
        return candidate.isTextual() && !candidate.asText().isBlank() ? candidate.asText() : null;
    }

    /** A token counter, or 0 when the provider did not report one. */
    static int intAt(JsonNode node, String... path) {
        return at(node, path).asInt(0);
    }

    private static boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
