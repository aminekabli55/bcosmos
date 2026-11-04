package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmModelRecord;
import com.bcosmos.backend.persistence.LlmModelStore;
import com.bcosmos.backend.persistence.LlmProviderRecord;
import com.bcosmos.backend.persistence.LlmProviderStore;
import com.bcosmos.backend.persistence.ProviderProtocol;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fixtures for the gateway tests.
 *
 * <p>Since Phase 11 a provider is a row, so the tests build rows instead of configuration: {@link #providerStore}
 * behind {@link ProviderCredentials}, and {@code HostEnvironment} as a map, because a test cannot set an
 * environment variable and should not have to.</p>
 */
final class AiTestSupport {

    static final String GEMINI_KEY = "gemini-test-key-0123456789";
    static final String OPENAI_KEY = "openai-test-key-0123456789";
    static final String GEMINI_ENV = "GEMINI_API_KEY";
    static final String OPENAI_ENV = "OPENAI_API_KEY";
    static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    static final BigDecimal COST_IN = new BigDecimal("0.000300");
    static final BigDecimal COST_OUT = new BigDecimal("0.002500");

    /** A fixed master key: 32 bytes, so stored credentials can be encrypted and decrypted in a test. */
    static final String MASTER_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private AiTestSupport() {
    }

    /** A provider row whose credential comes from the environment variable named by the row. */
    static LlmProviderRecord provider(long id, String code, ProviderProtocol protocol, String baseUrl,
            String apiKeyEnv, boolean apiKeyRequired) {
        return new LlmProviderRecord(id, code, code, protocol, baseUrl, apiKeyEnv, null, null, apiKeyRequired,
                CREATED, CREATED);
    }

    static LlmProviderRecord gemini(String baseUrl) {
        return provider(1, "gemini", ProviderProtocol.GEMINI, baseUrl, GEMINI_ENV, true);
    }

    static LlmProviderRecord openai(String baseUrl) {
        return provider(2, "openai", ProviderProtocol.OPENAI_COMPATIBLE, baseUrl, OPENAI_ENV, true);
    }

    /** A provider whose key was pasted into the settings screen and is therefore encrypted. */
    static LlmProviderRecord withStoredKey(LlmProviderRecord provider, String plaintextKey) {
        SecretCipher cipher = cipher();
        String hint = plaintextKey.length() <= 4 ? plaintextKey : plaintextKey.substring(plaintextKey.length() - 4);
        return provider.withStoredKey(cipher.encrypt(plaintextKey, provider.code()), hint, CREATED);
    }

    static SecretCipher cipher() {
        return SecretCipher.fromBase64(MASTER_KEY);
    }

    static LlmModelRecord model(long id, LlmProviderRecord provider, String modelName, boolean enabled) {
        return new LlmModelRecord(id, provider, modelName, LlmModelKind.HOSTED, LlmModelCapability.CHAT, null, enabled,
                128_000, COST_IN, COST_OUT, "test row", CREATED, CREATED);
    }

    /** A row that has not been inserted yet: the store assigns the id, as a real insert would. */
    static LlmModelRecord newModel(LlmProviderRecord provider, String modelName, boolean enabled) {
        return new LlmModelRecord(null, provider, modelName, LlmModelKind.HOSTED, LlmModelCapability.CHAT, null,
                enabled, 128_000, COST_IN, COST_OUT, "test row", CREATED, CREATED);
    }

    /** An embedding registry row: same shape plus the capability and the vector width it asks for. */
    static LlmModelRecord embeddingModel(long id, LlmProviderRecord provider, String modelName, boolean enabled,
            int dimension) {
        return new LlmModelRecord(id, provider, modelName, LlmModelKind.HOSTED, LlmModelCapability.EMBEDDING,
                dimension, enabled, 8_192, COST_IN, BigDecimal.ZERO, "test embedding row", CREATED, CREATED);
    }

    /** The environment a test provider reads when it has no stored key. */
    static Map<String, String> environmentWithKeys() {
        return Map.of(GEMINI_ENV, GEMINI_KEY, OPENAI_ENV, OPENAI_KEY);
    }

    static ProviderCredentials credentials(LlmProviderRecord... providers) {
        return credentials(environmentWithKeys(), providers);
    }

    static ProviderCredentials credentials(Map<String, String> environment, LlmProviderRecord... providers) {
        return new ProviderCredentials(new FakeProviderStore(providers), cipher(), HostEnvironment.of(environment));
    }

    static AiProperties properties(boolean enabled) {
        return properties(enabled, Duration.ofSeconds(5), 2);
    }

    /**
     * Section defaults with only what a test varies: the switch, the budget and the attempt count.
     *
     * <p>The master key is the fixed test key and no key file is configured, so a test never reads or writes one in
     * the developer's home directory.</p>
     */
    static AiProperties properties(boolean enabled, Duration timeout, int maxAttempts) {
        return new AiProperties(enabled, timeout, Duration.ofSeconds(2), maxAttempts, Duration.ofMillis(10), 2.0, 512,
                MASTER_KEY, "", false);
    }

    static final class FakeProviderStore implements LlmProviderStore {

        private final Map<Long, LlmProviderRecord> rows = new LinkedHashMap<>();
        private long nextId = 1;

        FakeProviderStore(LlmProviderRecord... providers) {
            for (LlmProviderRecord provider : providers) {
                long id = provider.id() == null ? nextId++ : provider.id();
                nextId = Math.max(nextId, id + 1);
                rows.put(id, provider);
            }
        }

        @Override
        public List<LlmProviderRecord> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public Optional<LlmProviderRecord> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<LlmProviderRecord> findByCode(String code) {
            return rows.values().stream().filter(row -> row.code().equals(code)).findFirst();
        }

        @Override
        public LlmProviderRecord save(LlmProviderRecord provider) {
            long id = provider.id() == null ? nextId++ : provider.id();
            LlmProviderRecord stored = new LlmProviderRecord(id, provider.code(), provider.label(),
                    provider.protocol(), provider.baseUrl(), provider.apiKeyEnv(), provider.apiKeyCiphertext(),
                    provider.apiKeyHint(), provider.apiKeyRequired(), provider.createdAt(), provider.updatedAt());
            rows.put(id, stored);
            return stored;
        }

        @Override
        public void delete(long id) {
            rows.remove(id);
        }
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
        public List<LlmModelRecord> findByCapability(LlmModelCapability capability) {
            return rows.values().stream().filter(model -> model.capability() == capability).toList();
        }

        @Override
        public LlmModelRecord save(LlmModelRecord model) {
            long id = model.id() == null ? nextId++ : model.id();
            LlmModelRecord stored = withId(model, id);
            rows.put(id, stored);
            return stored;
        }

        @Override
        public long countByProvider(long providerId) {
            return rows.values().stream().filter(model -> model.provider().id() == providerId).count();
        }

        @Override
        public boolean existsByProviderAndModelName(long providerId, String modelName) {
            return rows.values().stream().anyMatch(model -> model.provider().id() == providerId
                    && model.modelName().equals(modelName));
        }

        @Override
        public void delete(long id) {
            rows.remove(id);
        }

        private static LlmModelRecord withId(LlmModelRecord model, long id) {
            return new LlmModelRecord(id, model.provider(), model.modelName(), model.kind(), model.capability(),
                    model.embeddingDimension(), model.enabled(), model.contextWindow(),
                    model.costPer1kIn(), model.costPer1kOut(), model.notes(), model.createdAt(), model.updatedAt());
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
