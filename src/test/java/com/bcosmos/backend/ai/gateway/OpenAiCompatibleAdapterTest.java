package com.bcosmos.backend.ai.gateway;

import static com.bcosmos.backend.ai.gateway.AiTestSupport.OPENAI_KEY;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.backend.persistence.LlmProviderRecord;
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

/** The OpenAI-compatible adapter: the shape several vendors share, against a fake provider. */
class OpenAiCompatibleAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String EMBED_PATH = "/v1/embeddings";

    private static WireMockServer server;

    private OpenAiCompatibleAdapter adapter;

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
    void postsTheOpenAiShapeAndReadsTheAnswerWithItsTokens() {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(json("""
                {
                  "choices": [{"message": {"role": "assistant", "content": "the field is never read"}}],
                  "usage": {"prompt_tokens": 90, "completion_tokens": 25}
                }
                """)));

        ProviderCompletion completion = adapter.complete(model(), new CompletionRequest(
                List.of(ChatMessage.system("instructions"), ChatMessage.user("look at this")), false, 300, 0.0));

        assertThat(completion.text()).isEqualTo("the field is never read");
        assertThat(completion.tokensIn()).isEqualTo(90);
        assertThat(completion.tokensOut()).isEqualTo(25);
        assertThat(server.getAllServeEvents()).anySatisfy(event -> {
            assertThat(event.getRequest().getHeader("Authorization")).isEqualTo("Bearer " + OPENAI_KEY);
            assertThat(event.getRequest().getBodyAsString())
                    .contains("\"model\":\"gpt-4o-mini\"")
                    .contains("\"role\":\"system\"")
                    .contains("\"max_tokens\":300")
                    .doesNotContain("response_format");
        });
    }

    @Test
    void asksForJsonObjectOutputForStructuredCalls() {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(json("""
                {"choices": [{"message": {"content": "{}"}}]}
                """)));

        adapter.complete(model(), new CompletionRequest(List.of(ChatMessage.user("answer")), true, 128, null));

        assertThat(server.getAllServeEvents()).anySatisfy(event -> assertThat(event.getRequest().getBodyAsString())
                .contains("\"response_format\":{\"type\":\"json_object\"}"));
    }

    @Test
    void treatsRateLimitingAsItsOwnRetryableFailure() {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> adapter.complete(model(), request()))
                .isInstanceOfSatisfying(LlmProviderException.class, failure -> {
                    assertThat(failure.retryable()).isTrue();
                    assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.RATE_LIMITED);
                    assertThat(failure.getMessage()).contains("429");
                    // Nothing in this answer says when to come back, so no wait is invented for it (ADR 0040).
                    assertThat(failure.instructedWait()).isZero();
                });
    }

    @Test
    void readsTheRetryAfterHeaderOfARateLimit() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> adapter.complete(model(), request()))
                .isInstanceOfSatisfying(LlmProviderException.class, failure -> {
                    assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.RATE_LIMITED);
                    assertThat(failure.instructedWait()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(failure.getMessage()).contains("30s");
                });
    }

    @Test
    void treatsAnEmptyChoiceAsMalformed() {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(json("{\"choices\": []}")));

        assertThatThrownBy(() -> adapter.complete(model(), request()))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(LlmErrorCode.MALFORMED_RESPONSE));
    }

    @Test
    void embedsEveryTextAndReportsTheReportedTokenCount() {
        server.stubFor(post(urlEqualTo(EMBED_PATH)).willReturn(json("""
                {
                  "data": [{"embedding": [1.0, 2.0, 3.0]}, {"embedding": [4.0, 5.0, 6.0]}],
                  "usage": {"prompt_tokens": 12}
                }
                """)));

        ModelRef embeddingModel = ModelRegistry.toRef(
                AiTestSupport.model(5, openAiProvider(), "text-embedding-3-small", true, false));
        ProviderEmbeddings embeddings = adapter.embed(embeddingModel, List.of("a", "b"));

        assertThat(embeddings.vectors()).hasSize(2);
        assertThat(embeddings.vectors().getFirst()).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(embeddings.tokensIn()).isEqualTo(12);
        server.verify(1, postRequestedFor(urlEqualTo(EMBED_PATH)));
    }

    private OpenAiCompatibleAdapter adapter(Duration timeout) {
        return new OpenAiCompatibleAdapter(AiTestSupport.properties(true, timeout, 2),
                AiTestSupport.credentials(openAiProvider()), JSON);
    }

    /**
     * The provider row for the fake server.
     *
     * <p>The base URL is the versioned root and the paths are appended to it, which is how a real vendor is
     * pointed at: {@code https://api.deepseek.com/v1} plus {@code /chat/completions}.</p>
     */
    private static LlmProviderRecord openAiProvider() {
        return AiTestSupport.openai(server.baseUrl() + "/v1");
    }

    private static CompletionRequest request() {
        return new CompletionRequest(List.of(ChatMessage.user("hi")), false, 64, null);
    }

    private static ModelRef model() {
        return ModelRegistry.toRef(AiTestSupport.model(2, openAiProvider(), "gpt-4o-mini", true, false));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
