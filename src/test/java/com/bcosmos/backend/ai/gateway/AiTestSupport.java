package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmModelRecord;
import com.bcosmos.backend.persistence.LlmModelStore;
import com.bcosmos.backend.persistence.LlmProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AiTestSupport {

    static final String GEMINI_KEY = "gemini-test-key-0123456789";
    static final String OPENAI_KEY = "openai-test-key-0123456789";
    static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    static final BigDecimal COST_IN = new BigDecimal("0.000300");
    static final BigDecimal COST_OUT = new BigDecimal("0.002500");

    private AiTestSupport() {
    }

    static LlmModelRecord model(long id, LlmProvider provider, String modelName, boolean enabled, boolean isDefault) {
        return new LlmModelRecord(id, provider, modelName, LlmModelKind.HOSTED, enabled, isDefault, 128_000, COST_IN,
                COST_OUT, "test row", CREATED, CREATED);
    }

    static AiProperties properties(boolean enabled, String geminiBaseUrl, String openAiBaseUrl, Duration timeout,
            int maxAttempts) {
        return new AiProperties(
                enabled,
                timeout,
                Duration.ofSeconds(2),
                maxAttempts,
                Duration.ofMillis(10),
                2.0,
                512,
                Map.of(
                        LlmProvider.GEMINI,
                        new AiProperties.ProviderSettings(geminiBaseUrl, GEMINI_KEY, "GEMINI_API_KEY"),
                        LlmProvider.OPENAI_COMPATIBLE,
                        new AiProperties.ProviderSettings(openAiBaseUrl, OPENAI_KEY, "OPENAI_API_KEY")));
    }

    static AiProperties propertiesWithoutKeys(boolean enabled) {
        return new AiProperties(enabled, Duration.ofSeconds(5), Duration.ofSeconds(2), 2, Duration.ofMillis(10), 2.0,
                512,
                Map.of(
                        LlmProvider.GEMINI,
                        new AiProperties.ProviderSettings("http://localhost:1", "", "GEMINI_API_KEY"),
                        LlmProvider.OPENAI_COMPATIBLE,
                        new AiProperties.ProviderSettings("http://localhost:1", "", "OPENAI_API_KEY")));
    }

    static final class FakeModelStore implements LlmModelStore {

        private final Map<Long, LlmModelRecord> rows = new LinkedHashMap<>();
        private long nextId = 1;

        FakeModelStore(LlmModelRecord... models) {
            for (LlmModelRecord model : models) {
                long id = model.id() == null ? nextId++ : model.id();
                nextId = Math.max(nextId, id + 1);
                rows.put(id, withId(model, id));
            }
        }

        @Override
        public List<LlmModelRecord> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public Optional<LlmModelRecord> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<LlmModelRecord> findDefault() {
            return rows.values().stream().filter(LlmModelRecord::defaultModel).findFirst();
        }

        @Override
        public LlmModelRecord save(LlmModelRecord model) {
            long id = model.id() == null ? nextId++ : model.id();
            LlmModelRecord stored = withId(model, id);
            rows.put(id, stored);
            return stored;
        }

        /** Rows marked as default, so a test can assert the registry keeps at most one. */
        List<LlmModelRecord> defaults() {
            return rows.values().stream().filter(LlmModelRecord::defaultModel).toList();
        }

        private static LlmModelRecord withId(LlmModelRecord model, long id) {
            return new LlmModelRecord(id, model.provider(), model.modelName(), model.kind(), model.enabled(),
                    model.defaultModel(), model.contextWindow(), model.costPer1kIn(), model.costPer1kOut(),
                    model.notes(), model.createdAt(), model.updatedAt());
        }
    }

    /** Collects usage rows instead of writing them, so a test can assert what was recorded. */
    static final class RecordingUsageRecorder implements UsageRecorder {

        private final List<UsageRecord> rows = new ArrayList<>();

        @Override
        public void record(UsageRecord record) {
            rows.add(record);
        }

        List<UsageRecord> rows() {
            return List.copyOf(rows);
        }
    }
}
