package com.bcosmos.backend.ai.gateway;

import static com.bcosmos.backend.ai.gateway.AiTestSupport.GEMINI_KEY;
import static com.bcosmos.backend.ai.gateway.AiTestSupport.model;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.backend.ai.explanation.FindingExplanation;
import com.bcosmos.backend.config.AiConfiguration;
import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.LlmOperation;
import com.bcosmos.backend.persistence.LlmProvider;
import com.bcosmos.backend.persistence.LlmUsageOutcome;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;


class LlmGatewayTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String GENERATE_PATH = "/v1beta/models/gemini-2.5-flash:generateContent";
    private static final String EMBED_PATH = "/v1beta/models/gemini-2.5-flash:batchEmbedContents";

    private static final String VALID_EXPLANATION = """
            {
              "summary": "the catch block hides the failure",
              "explanation": "the exception is discarded, so an unreadable file looks like an empty one",
              "confidence": "HIGH",
              "references": [{"file": "IngestRunner.java", "startLine": 22}]
            }
            """;

    private static final String INVALID_EXPLANATION = """
            {"explanation": "no summary here", "confidence": "HIGH", "references": []}
            """;

    private static WireMockServer server;

    private AiTestSupport.FakeModelStore store;
    private AiTestSupport.RecordingUsageRecorder recorder;
    private LlmGateway gateway;

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
        // The embedding row declares width 2 so it matches the vectors the fake provider returns.
        store = new AiTestSupport.FakeModelStore(
                model(1, LlmProvider.GEMINI, "gemini-2.5-flash", true, true),
                model(2, LlmProvider.OPENAI_COMPATIBLE, "gpt-4o-mini", false, false),
                AiTestSupport.embeddingModel(3, LlmProvider.GEMINI, "gemini-2.5-flash", true, true, 2));
        recorder = new AiTestSupport.RecordingUsageRecorder();
        gateway = gateway(AiTestSupport.properties(true, server.baseUrl(), server.baseUrl(), Duration.ofSeconds(5), 2));
    }

    @Test
    void retriesAFailingProviderAndRecordsOneRowForTheSuccessfulCall() {
        firstAttemptFailsThen("the field is never read", "chat-retry");

        ChatResult result = gateway.chat(ChatRequest.of(null, List.of(ChatMessage.user("explain this")), "chat.v1"));

        assertThat(result.text()).isEqualTo("the field is never read");
        assertThat(result.model().modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(result.usage().attempts()).isEqualTo(2);
        assertThat(result.usage().estimatedCostUsd()).isEqualByComparingTo("0.000130");
        assertThat(recorder.rows()).singleElement().satisfies(row -> {
            assertThat(row.outcome()).isEqualTo(LlmUsageOutcome.SUCCESS);
            assertThat(row.operation()).isEqualTo(LlmOperation.CHAT);
            assertThat(row.attempts()).isEqualTo(2);
            assertThat(row.promptVersion()).isEqualTo("chat.v1");
            assertThat(row.model().id()).isEqualTo(1L);
        });
    }

    @Test
    void stopsRetryingAfterTheConfiguredAttemptsAndRecordsTheFailure() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> gateway.chat(ChatRequest.of(null, List.of(ChatMessage.user("explain")), "chat.v1")))
                .isInstanceOf(LlmProviderException.class);

        assertThat(recorder.rows()).singleElement().satisfies(row -> {
            assertThat(row.outcome()).isEqualTo(LlmUsageOutcome.PROVIDER_ERROR);
            assertThat(row.attempts()).isEqualTo(2);
            assertThat(row.errorCode()).isEqualTo(LlmErrorCode.PROVIDER_ERROR);
        });
    }

    @Test
    void givesUpOnAProviderThatOutlivesTheTimeBudget() {
        gateway = gateway(AiTestSupport.properties(true, server.baseUrl(), server.baseUrl(),
                Duration.ofMillis(300), 1));
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(geminiAnswer("too late").withFixedDelay(1500)));

        assertThatThrownBy(() -> gateway.chat(ChatRequest.of(null, List.of(ChatMessage.user("explain")), null)))
                .isInstanceOf(LlmTimeoutException.class);

        assertThat(recorder.rows()).singleElement()
                .satisfies(row -> assertThat(row.outcome()).isEqualTo(LlmUsageOutcome.TIMEOUT));
    }

    @Test
    void redactsAConfiguredKeyBeforeThePromptLeavesTheProcess() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(geminiAnswer("ok")));

        gateway.chat(ChatRequest.of(null,
                List.of(ChatMessage.user("here is my key " + GEMINI_KEY + ", explain this")), null));

        assertThat(server.getAllServeEvents()).isNotEmpty();
        assertThat(server.getAllServeEvents()).allSatisfy(event ->
                assertThat(event.getRequest().getBodyAsString()).doesNotContain(GEMINI_KEY));
        assertThat(server.getAllServeEvents()).anySatisfy(event ->
                assertThat(event.getRequest().getBodyAsString()).contains("[gemini-key redacted]"));
    }

    @Test
    void sendsNothingAtAllWhenTheAiLayerIsSwitchedOff() {
        gateway = gateway(AiTestSupport.properties(false, server.baseUrl(), server.baseUrl(), Duration.ofSeconds(5), 2));

        assertThatThrownBy(() -> gateway.chat(ChatRequest.of(null, List.of(ChatMessage.user("explain")), null)))
                .isInstanceOf(LlmDisabledException.class);

        assertThat(server.getAllServeEvents()).isEmpty();
        assertThat(recorder.rows()).isEmpty();
    }

    @Test
    void sendsNothingWhenTheStoredSwitchIsOffEvenThoughTheDeploymentPropertyIsOn() {
        // The settings screen can switch the whole layer off without a restart; nothing may be sent and nothing paid
        // for (spec v2 section 10). No usage row is written either, because no call was made.
        gateway = gateway(AiTestSupport.properties(true, server.baseUrl(), server.baseUrl(), Duration.ofSeconds(5), 2),
                () -> false);

        assertThatThrownBy(() -> gateway.chat(ChatRequest.of(null, List.of(ChatMessage.user("explain")), null)))
                .isInstanceOf(LlmDisabledException.class);

        assertThat(server.getAllServeEvents()).isEmpty();
        assertThat(recorder.rows()).isEmpty();
    }

    @Test
    void refusesAModelThatIsNotEnabledEvenWhenTheAiLayerIsOn() {
        assertThatThrownBy(() -> gateway.chat(ChatRequest.of(2L, List.of(ChatMessage.user("explain")), null)))
                .isInstanceOf(ModelDisabledException.class);

        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void hasNothingToCallWhenNoModelIsNamedAndNoneIsTheDefault() {
        store = new AiTestSupport.FakeModelStore(model(3, LlmProvider.GEMINI, "gemini-2.5-flash", true, false));
        gateway = gateway(AiTestSupport.properties(true, server.baseUrl(), server.baseUrl(), Duration.ofSeconds(5), 2));

        assertThatThrownBy(() -> gateway.chat(ChatRequest.of(null, List.of(ChatMessage.user("explain")), null)))
                .isInstanceOf(ModelNotConfiguredException.class);
    }

    @Test
    void retriesStructuredOutputOnceWithTheValidationErrorAndReportsBothAttempts() {
        answerTwice(INVALID_EXPLANATION, VALID_EXPLANATION, "structured-retry");

        StructuredResult<FindingExplanation> result = gateway.structured(
                StructuredRequest.of(null, List.of(ChatMessage.user("explain finding 7")),
                        FindingExplanation.class, loader().load("explain-finding", "v1").schema(),
                        "explain-finding.v1"));

        assertThat(result.retried()).isTrue();
        assertThat(result.validationErrors()).containsExactly("$.summary: required property is missing");
        assertThat(result.value().summary()).isEqualTo("the catch block hides the failure");
        assertThat(result.usage().attempts()).isEqualTo(2);
        assertThat(server.getAllServeEvents()).anySatisfy(event ->
                assertThat(event.getRequest().getBodyAsString()).contains("did not match the schema"));
        assertThat(recorder.rows()).singleElement()
                .satisfies(row -> assertThat(row.outcome()).isEqualTo(LlmUsageOutcome.SUCCESS));
    }

    @Test
    void failsWhenTheRetryIsInvalidTooAndRecordsTheSchemaFailure() {
        server.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(geminiAnswer(INVALID_EXPLANATION)));

        assertThatThrownBy(() -> gateway.structured(StructuredRequest.of(null,
                List.of(ChatMessage.user("explain finding 7")), FindingExplanation.class,
                loader().load("explain-finding", "v1").schema(), "explain-finding.v1")))
                .isInstanceOfSatisfying(StructuredOutputException.class,
                        failure -> assertThat(failure.validationErrors())
                                .containsExactly("$.summary: required property is missing"));

        assertThat(recorder.rows()).singleElement().satisfies(row -> {
            assertThat(row.outcome()).isEqualTo(LlmUsageOutcome.SCHEMA_INVALID);
            assertThat(row.attempts()).isEqualTo(2);
        });
    }

    @Test
    void embedsEveryTextAndRecordsTheCall() {
        server.stubFor(post(urlEqualTo(EMBED_PATH)).willReturn(json("""
                {"embeddings": [{"values": [0.5, 0.25]}]}
                """)));

        EmbedResult result = gateway.embed(new EmbedRequest(null, List.of("public final class Book")));

        assertThat(result.vectors()).hasSize(1);
        assertThat(result.dimensions()).isEqualTo(2);
        assertThat(recorder.rows()).singleElement()
                .satisfies(row -> assertThat(row.operation()).isEqualTo(LlmOperation.EMBED));
    }

    /**
     * A gateway whose switch follows the property, which is the deployment default.
     */
    private LlmGateway gateway(AiProperties properties) {
        return gateway(properties, () -> properties.enabled());
    }

    private LlmGateway gateway(AiProperties properties, AiEnablement enablement) {
        ProviderCredentials credentials = new ProviderCredentials(properties);
        List<LlmProviderAdapter> adapters = List.of(
                new GeminiAdapter(properties, credentials, JSON),
                new OpenAiCompatibleAdapter(properties, credentials, JSON));
        return new LlmGateway(properties, enablement, new ModelRegistry(store, credentials, Clock.systemUTC()),
                adapters, recorder, SecretRedactor.from(credentials), JSON, AiConfiguration.retryFor(properties),
                Clock.systemUTC());
    }

    private static PromptTemplateLoader loader() {
        return new PromptTemplateLoader(new DefaultResourceLoader(), JSON);
    }

    /** Answers the first call with {@code first} and the second with {@code second}: the schema-retry path. */
    private static void answerTwice(String first, String second, String scenario) {
        server.stubFor(post(urlEqualTo(GENERATE_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(geminiAnswer(first))
                .willSetStateTo("second-attempt"));
        server.stubFor(post(urlEqualTo(GENERATE_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs("second-attempt")
                .willReturn(geminiAnswer(second)));
    }

    /** Fails the first call with HTTP 500, then answers with the given text: the provider-retry path. */
    private static void firstAttemptFailsThen(String answer, String scenario) {
        server.stubFor(post(urlEqualTo(GENERATE_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-attempt"));
        server.stubFor(post(urlEqualTo(GENERATE_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs("second-attempt")
                .willReturn(geminiAnswer(answer)));
    }

    /** A Gemini answer envelope with the given text and a fixed token count. */
    private static ResponseDefinitionBuilder geminiAnswer(String text) {
        ObjectNode envelope = JSON.createObjectNode();
        envelope.putArray("candidates")
                .addObject()
                .putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", text);
        envelope.putObject("usageMetadata").put("promptTokenCount", 100).put("candidatesTokenCount", 40);
        return json(envelope.toString());
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
