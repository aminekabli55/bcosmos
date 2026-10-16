package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.LlmOperation;
import com.bcosmos.backend.persistence.LlmUsageOutcome;
import com.bcosmos.backend.persistence.ProviderProtocol;
import io.github.resilience4j.retry.Retry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class LlmGateway implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private static final String SCHEMA_INSTRUCTION = """
            Answer with a single JSON object and nothing else: no prose, no markdown fence.
            The object must validate against this JSON Schema:
            %s
            """;

    private static final String RETRY_INSTRUCTION = """
            The previous answer did not match the schema. Problems: %s
            Answer again with only the corrected JSON object.
            """;

    private static final String NOTHING_TO_BIND = "the answer could not be read as JSON";

    private final AiProperties properties;
    private final AiEnablement enablement;
    private final ModelRegistry models;
    private final Map<ProviderProtocol, LlmProviderAdapter> adapters;
    private final UsageRecorder usage;
    private final SecretRedactor redactor;
    private final JsonSchemaValidator schemaValidator;
    private final ObjectMapper json;
    private final Retry retry;
    private final Clock clock;

    public LlmGateway(AiProperties properties, AiEnablement enablement, ModelRegistry models,
            List<LlmProviderAdapter> adapterList, UsageRecorder usage, SecretRedactor redactor, ObjectMapper json,
            Retry retry, Clock clock) {
        this.properties = properties;
        this.enablement = enablement;
        this.models = models;
        this.adapters = adapterList.stream().collect(Collectors.toUnmodifiableMap(
                LlmProviderAdapter::protocol, Function.identity(), (first, second) -> first));
        this.usage = usage;
        this.redactor = redactor;
        this.schemaValidator = new JsonSchemaValidator();
        this.json = json;
        this.retry = retry;
        this.clock = clock;
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        requireEnabled();
        ModelRef model = models.requireUsable(request.modelId());
        Call call = new Call(model, LlmOperation.CHAT, request.promptVersion(), clock.instant());
        try {
            ProviderCompletion completion = complete(call, request.messages(), false, request.maxOutputTokens(),
                    request.temperature());
            call.addTokens(completion.tokensIn(), completion.tokensOut());
            LlmUsage account = call.usage();
            usage.record(call.row(LlmUsageOutcome.SUCCESS, null));
            log.info("chat on {} finished in {} ms after {} attempt(s), {} token(s)", model.label(),
                    account.latencyMs(), account.attempts(), account.totalTokens());
            return new ChatResult(model, completion.text(), account);
        } catch (LlmException failure) {
            recordFailure(call, failure);
            throw failure;
        }
    }

    @Override
    public <T> StructuredResult<T> structured(StructuredRequest<T> request) {
        requireEnabled();
        ModelRef model = models.requireUsable(request.modelId());
        Call call = new Call(model, LlmOperation.STRUCTURED, request.promptVersion(), clock.instant());
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(SCHEMA_INSTRUCTION.formatted(request.jsonSchema().toPrettyString())));
        prompt.addAll(request.messages());
        try {
            ProviderCompletion first = complete(call, prompt, true, request.maxOutputTokens(), null);
            call.addTokens(first.tokensIn(), first.tokensOut());
            List<String> violations = violations(request, first.text());
            if (violations.isEmpty()) {
                return structuredResult(call, request, first.text(), List.of());
            }
            log.info("{} answered JSON the schema rejected; retrying once with the validation error: {}",
                    model.label(), violations);

            List<ChatMessage> retryPrompt = new ArrayList<>(prompt);
            retryPrompt.add(ChatMessage.assistant(first.text()));
            retryPrompt.add(ChatMessage.user(RETRY_INSTRUCTION.formatted(String.join("; ", violations))));
            ProviderCompletion second = complete(call, retryPrompt, true, request.maxOutputTokens(), null);
            call.addTokens(second.tokensIn(), second.tokensOut());
            List<String> remaining = violations(request, second.text());
            if (!remaining.isEmpty()) {
                throw new StructuredOutputException(model.label(), remaining);
            }
            return structuredResult(call, request, second.text(), violations);
        } catch (LlmException failure) {
            recordFailure(call, failure);
            throw failure;
        }
    }

    @Override
    public EmbedResult embed(EmbedRequest request) {
        requireEnabled();
        // Embedding has its own capability gate: a chat model answers nonsense to a vector request, and an
        // embedding model must never be the default for chat.
        ModelRef model = models.requireEmbeddingModel(request.modelId());
        Call call = new Call(model, LlmOperation.EMBED, null, clock.instant());
        try {
            LlmProviderAdapter adapter = adapterFor(model.provider().protocol());
            ProviderEmbeddings embeddings = withRetries(call, () -> adapter.embed(model, request.texts()));
            requireDeclaredWidth(model, embeddings);
            call.addTokens(embeddings.tokensIn(), 0);
            LlmUsage account = call.usage();
            usage.record(call.row(LlmUsageOutcome.SUCCESS, null));
            log.info("embedding on {} finished in {} ms after {} attempt(s), {} vector(s)", model.label(),
                    account.latencyMs(), account.attempts(), embeddings.vectors().size());
            return new EmbedResult(embeddings.vectors(), model.id(), model.modelName(), account);
        } catch (LlmException failure) {
            recordFailure(call, failure);
            throw failure;
        }
    }

    /**
     * A vector only fits the column it is stored in if its width matches what the registry row declares.
     *
     * <p>The check is here rather than in each adapter, so both providers are held to the same rule: a silent
     * 3072-dimensional answer in a 1536-wide table is a corrupt index, not a warning.</p>
     */
    private static void requireDeclaredWidth(ModelRef model, ProviderEmbeddings embeddings) {
        Integer declared = model.embeddingDimension();
        if (declared == null) {
            return;
        }
        for (float[] vector : embeddings.vectors()) {
            if (vector.length != declared) {
                throw new LlmProviderException(LlmErrorCode.PROVIDER_ERROR, model.label() + " returned "
                        + vector.length + " embedding values but its registry row declares " + declared
                        + "; set embedding_dimension to the width the provider really returns", false);
            }
        }
    }

    private <T> StructuredResult<T> structuredResult(Call call, StructuredRequest<T> request, String answer,
            List<String> violations) {
        T value = bind(request, answer);
        LlmUsage account = call.usage();
        usage.record(call.row(LlmUsageOutcome.SUCCESS, null));
        log.info("structured call on {} finished in {} ms after {} attempt(s), {} token(s)",
                call.model().label(), account.latencyMs(), account.attempts(), account.totalTokens());
        return new StructuredResult<>(call.model(), value, account, violations);
    }

    private ProviderCompletion complete(Call call, List<ChatMessage> messages, boolean jsonMode,
            Integer maxOutputTokens, Double temperature) {
        LlmProviderAdapter adapter = adapterFor(call.model().provider().protocol());
        CompletionRequest request = new CompletionRequest(
                redactor.redact(messages),
                jsonMode,
                maxOutputTokens == null ? properties.maxOutputTokens() : maxOutputTokens,
                temperature);
        return withRetries(call, () -> adapter.complete(call.model(), request));
    }

    private <T> T withRetries(Call call, Supplier<T> attempt) {
        Supplier<T> guarded = () -> {
            if (call.budgetExhausted(properties.timeout())) {
                throw call.outOfTime(properties.timeout());
            }
            call.countAttempt();
            try {
                return attempt.get();
            } catch (LlmException failure) {
                // Kept so that giving up can report the reason the provider gave, not just that we ran out of time.
                call.remember(failure);
                throw failure;
            }
        };
        try {
            return Retry.decorateSupplier(retry, guarded).get();
        } catch (LlmException failure) {
            throw failure;
        } catch (RuntimeException unexpected) {
            throw new LlmProviderException(LlmErrorCode.PROVIDER_ERROR,
                    call.model().provider().code() + " call failed (" + unexpected.getClass().getSimpleName() + ")",
                    false, unexpected);
        }
    }

    private List<String> violations(StructuredRequest<?> request, String answer) {
        String payload = jsonObjectIn(answer);
        if (payload == null) {
            return List.of("$: the answer held no JSON object");
        }
        JsonNode node;
        try {
            node = json.readTree(payload);
        } catch (RuntimeException notJson) {
            return List.of("$: the answer is not valid JSON");
        }
        return schemaValidator.validate(request.jsonSchema(), node);
    }

    private <T> T bind(StructuredRequest<T> request, String answer) {
        String payload = jsonObjectIn(answer);
        try {
            return json.treeToValue(json.readTree(payload), request.responseType());
        } catch (RuntimeException failure) {
            throw new StructuredOutputException(request.responseType().getSimpleName(), List.of(NOTHING_TO_BIND));
        }
    }

    /** The JSON object inside an answer: models happily wrap it in prose or a markdown fence anyway. */
    private static String jsonObjectIn(String answer) {
        if (answer == null) {
            return null;
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        return start >= 0 && end > start ? answer.substring(start, end + 1) : null;
    }

    private void recordFailure(Call call, LlmException failure) {
        LlmUsageOutcome outcome = switch (failure.errorCode()) {
            case LlmErrorCode.SCHEMA_INVALID -> LlmUsageOutcome.SCHEMA_INVALID;
            case LlmErrorCode.TIMEOUT -> LlmUsageOutcome.TIMEOUT;
            default -> LlmUsageOutcome.PROVIDER_ERROR;
        };
        usage.record(call.row(outcome, failure.errorCode()));
        log.warn("{} call on {} failed after {} attempt(s): {} - {}", call.operation(), call.model().label(),
                call.attempts(), failure.errorCode(), failure.getMessage());
    }

    /**
     * The first gate of every call: the AI layer must be on.
     *
     * <p>The answer comes from {@link AiEnablement}, not from the properties, so the Settings screen can switch the
     * whole layer off without a restart (spec v2 section 10).</p>
     */
    private void requireEnabled() {
        if (!enablement.enabled()) {
            throw new LlmDisabledException();
        }
    }

    /**
     * The adapter for a wire shape. The vendor is not consulted: two vendors speaking the same protocol share one
     * adapter, which is why a new provider is a row and not a class (ADR 0038).
     */
    private LlmProviderAdapter adapterFor(ProviderProtocol protocol) {
        LlmProviderAdapter adapter = adapters.get(protocol);
        if (adapter == null) {
            throw new IllegalStateException("no adapter is registered for protocol " + protocol);
        }
        return adapter;
    }

    /** What one call to one model costs and how long it took, accumulated across attempts and schema retries. */
    private static final class Call {

        private final ModelRef model;
        private final LlmOperation operation;
        private final String promptVersion;
        private final Instant startedAt;
        private final long startedNanos = System.nanoTime();

        private int attempts;
        private int tokensIn;
        private int tokensOut;
        private LlmException lastFailure;

        private Call(ModelRef model, LlmOperation operation, String promptVersion, Instant startedAt) {
            this.model = model;
            this.operation = operation;
            this.promptVersion = promptVersion;
            this.startedAt = startedAt;
        }

        private ModelRef model() {
            return model;
        }

        private LlmOperation operation() {
            return operation;
        }

        private void countAttempt() {
            attempts++;
        }

        private int attempts() {
            return attempts;
        }

        private void addTokens(int inputTokens, int outputTokens) {
            this.tokensIn += inputTokens;
            this.tokensOut += outputTokens;
        }

        private boolean budgetExhausted(Duration budget) {
            return System.nanoTime() - startedNanos >= budget.toNanos();
        }

        private void remember(LlmException failure) {
            this.lastFailure = failure;
        }

        /**
         * What to throw when the budget is gone before another attempt can be made.
         *
         * <p>Exhausting the budget is reported as a timeout only when nothing better is known. A provider that
         * asked us to wait longer than the budget allows is reported as what it is: the wait is the reason there
         * was no second attempt, and a timeout would hide both the cause and the length of the wait the operator has
         * to sit out (ADR 0040).</p>
         *
         * <p>The failure is not marked retryable, because this is where retrying stops: the budget that made it
         * impossible is not going to grow, and a loop that kept asking would only spend the provider's quota
         * again.</p>
         */
        private LlmException outOfTime(Duration budget) {
            if (lastFailure != null && !lastFailure.instructedWait().isZero()) {
                return new LlmProviderException(LlmErrorCode.RATE_LIMITED, model.label() + " rate limited the call and"
                        + " asked to wait " + RetryWait.describe(lastFailure.instructedWait())
                        + ", longer than the " + RetryWait.describe(budget) + " budget of one call"
                        + " (bcosmos.ai.timeout,"
                        + " BCOSMOS_AI_TIMEOUT); re-run it once the window has passed", false, lastFailure);
            }
            return new LlmTimeoutException("the call to " + model.label() + " exceeded the " + budget + " budget",
                    false);
        }

        private LlmUsage usage() {
            long latencyMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
            return new LlmUsage(tokensIn, tokensOut, latencyMs, attempts, model.estimatedCostUsd(tokensIn, tokensOut));
        }

        private UsageRecord row(LlmUsageOutcome outcome, String errorCode) {
            LlmUsage usage = usage();
            return new UsageRecord(operation, model, promptVersion, usage.tokensIn(), usage.tokensOut(),
                    usage.latencyMs(), usage.attempts(), usage.estimatedCostUsd(), outcome, errorCode, startedAt);
        }
    }
}
