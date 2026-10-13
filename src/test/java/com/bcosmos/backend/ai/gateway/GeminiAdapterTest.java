package com.bcosmos.backend.ai.gateway;

import static com.bcosmos.backend.ai.gateway.AiTestSupport.GEMINI_KEY;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.backend.persistence.LlmProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** The Gemini adapter: request shape, response reading and error mapping, against a fake provider. */
class GeminiAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String GENERATE_PATH = "/v1beta/models/gemini-2.5-flash:generateContent";
    private static final String EMBED_PATH = "/v1beta/models/text-embedding-004:batchEmbedContents";

    private static WireMockServer server;

    private GeminiAdapter adapter;

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        server.resetAll();
        adapter = adapter(Duration.ofSeconds(5));
    }

    @Test
    void sendsTheKeyInAHeaderAndReadsTheAnswerWithItsTokens() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(json("""
                {
                  "candidates": [{"content": {"parts": [{"text": "the catch block hides the failure"}]}}],
                  "usageMetadata": {"promptTokenCount": 120, "candidatesTokenCount": 30}
                }
                """)));

        ProviderCompletion completion = adapter.complete(model(), new CompletionRequest(
                List.of(ChatMessage.system("instructions"), ChatMessage.user("look at this")), false, 256, 0.2));

        assertThat(completion.text()).isEqualTo("the catch block hides the failure");
        assertThat(completion.tokensIn()).isEqualTo(120);
        assertThat(completion.tokensOut()).isEqualTo(30);
        assertThat(server.getAllServeEvents()).anySatisfy(event -> {
            assertThat(event.getRequest().getHeader("x-goog-api-key")).isEqualTo(GEMINI_KEY);
            assertThat(event.getRequest().getBodyAsString())
                    .contains("systemInstruction")
                    .contains("instructions")
                    .contains("\"maxOutputTokens\":256")
                    .doesNotContain("responseMimeType");
        });
    }

    @Test
    void asksForJsonOutputOnlyWhenStructuredOutputIsRequested() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(json("""
                {"candidates": [{"content": {"parts": [{"text": "{}"}]}}]}
                """)));

        adapter.complete(model(), new CompletionRequest(List.of(ChatMessage.user("answer")), true, 512, null));

        assertThat(server.getAllServeEvents()).anySatisfy(event -> assertThat(event.getRequest().getBodyAsString())
                .contains("\"responseMimeType\":\"application/json\""));
    }

    @Test
    void mapsRejectedCredentialsToANonRetryableFailureWithoutEchoingTheKey() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(aResponse().withStatus(401)
                .withBody("{\"error\":{\"message\":\"API key not valid: " + GEMINI_KEY + "\"}}")));

        assertThatThrownBy(() -> adapter.complete(model(), new CompletionRequest(List.of(ChatMessage.user("hi")),
                false, 64, null)))
                .isInstanceOfSatisfying(LlmProviderException.class, failure -> {
                    assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.UNAUTHORIZED);
                    assertThat(failure.retryable()).isFalse();
                    assertThat(failure.getMessage()).doesNotContain(GEMINI_KEY);
                });
    }

    @Test
    void treatsServerFailuresAsRetryableAndBadRequestsAsFinal() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> adapter.complete(model(), request()))
                .isInstanceOfSatisfying(LlmException.class, failure -> assertThat(failure.retryable()).isTrue());

        server.resetAll();
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(aResponse().withStatus(400)));
        assertThatThrownBy(() -> adapter.complete(model(), request()))
                .isInstanceOfSatisfying(LlmException.class, failure -> {
                    assertThat(failure.retryable()).isFalse();
                    assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.PROVIDER_REJECTED);
                });
    }

    @Test
    void reportsABlockedPromptAsARejectedRequest() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(json("""
                {"promptFeedback": {"blockReason": "SAFETY"}}
                """)));

        assertThatThrownBy(() -> adapter.complete(model(), request()))
                .isInstanceOfSatisfying(LlmProviderException.class, failure -> {
                    assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.PROVIDER_REJECTED);
                    assertThat(failure.getMessage()).contains("SAFETY");
                });
    }

    @Test
    void treatsAResponseWithoutCandidatesAsMalformed() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(json("{\"candidates\": []}")));

        assertThatThrownBy(() -> adapter.complete(model(), request()))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.MALFORMED_RESPONSE));
    }

    @Test
    void embedsEveryTextAndReportsThatGeminiSendsNoTokenCount() {
        server.stubFor(post(urlEqualTo(EMBED_PATH)).willReturn(json("""
                {"embeddings": [{"values": [0.1, 0.2]}, {"values": [0.3, 0.4]}]}
                """)));

        ProviderEmbeddings embeddings = adapter.embed(embeddingModel(), List.of("a", "b"));

        assertThat(embeddings.vectors()).hasSize(2);
        assertThat(embeddings.vectors().get(1)).containsExactly(0.3f, 0.4f);
        assertThat(embeddings.tokensIn()).isZero();
        server.verify(1, postRequestedFor(urlEqualTo(EMBED_PATH)));
    }

    private GeminiAdapter adapter(Duration timeout) {
        var properties = AiTestSupport.properties(true, server.baseUrl(), server.baseUrl(), timeout, 2);
        return new GeminiAdapter(properties, new ProviderCredentials(properties), JSON);
    }

    private static CompletionRequest request() {
        return new CompletionRequest(List.of(ChatMessage.user("hi")), false, 64, null);
    }

    private static ModelRef model() {
        return ModelRegistry.toRef(AiTestSupport.model(1, LlmProvider.GEMINI, "gemini-2.5-flash", true, true));
    }

    private static ModelRef embeddingModel() {
        return ModelRegistry.toRef(AiTestSupport.model(4, LlmProvider.GEMINI, "text-embedding-004", true, false));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
