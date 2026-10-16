package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.ProviderProtocol;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Google Gemini adapter (spec v2 section 7.1: the free tier is the testing provider).
 *
 * <p>{@code generateContent} with {@code responseMimeType: application/json} for structured calls. The API key
 * travels in the {@code x-goog-api-key} header rather than in the query string, so it cannot end up in a URL that a
 * proxy or a log file records.</p>
 *
 * <p>Gemini also exposes an OpenAI-compatible route ({@code /v1beta/openai}); it is available as a provider row
 * without any code here, which is exactly the difference this protocol column buys.</p>
 */
public class GeminiAdapter implements LlmProviderAdapter {

    private static final String GENERATE_PATH = "/v1beta/models/%s:generateContent";
    private static final String EMBED_PATH = "/v1beta/models/%s:batchEmbedContents";

    private final AiProperties properties;
    private final ProviderCredentials credentials;
    private final RestClient http;
    private final ObjectMapper json;

    public GeminiAdapter(AiProperties properties, ProviderCredentials credentials, ObjectMapper json) {
        this.properties = properties;
        this.credentials = credentials;
        this.http = ProviderHttp.client(properties);
        this.json = json;
    }

    @Override
    public ProviderProtocol protocol() {
        return ProviderProtocol.GEMINI;
    }

    @Override
    public ProviderCompletion complete(ModelRef model, CompletionRequest request) {
        String apiKey = credentials.resolve(model.provider());
        String provider = model.provider().code();
        ObjectNode body = requestBody(model, request);
        try {
            String response = http.post()
                    .uri(credentials.requireBaseUrl(model.provider()) + GENERATE_PATH.formatted(model.modelName()))
                    .headers(headers -> authorize(headers, apiKey))
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readCompletion(provider, response);
        } catch (RuntimeException failure) {
            throw ProviderHttp.map(provider, failure);
        }
    }

    @Override
    public ProviderEmbeddings embed(ModelRef model, List<String> texts) {
        String apiKey = credentials.resolve(model.provider());
        String provider = model.provider().code();
        ObjectNode body = json.createObjectNode();
        ArrayNode requests = body.putArray("requests");
        for (String text : texts) {
            ObjectNode request = requests.addObject()
                    .put("model", "models/" + model.modelName());
            request.putObject("content")
                    .putArray("parts")
                    .addObject()
                    .put("text", text);
            if (model.embeddingDimension() != null) {
                // The registry's width, not the model's default: 3072-dimensional vectors do not fit the
                // code_chunk.embedding column and cannot be indexed by pgvector's HNSW (spec 7.2).
                request.putObject("embedContentConfig")
                        .put("outputDimensionality", model.embeddingDimension());
            }
        }
        try {
            String response = http.post()
                    .uri(credentials.requireBaseUrl(model.provider()) + EMBED_PATH.formatted(model.modelName()))
                    .headers(headers -> authorize(headers, apiKey))
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readEmbeddings(provider, response, texts.size());
        } catch (RuntimeException failure) {
            throw ProviderHttp.map(provider, failure);
        }
    }

    /** A provider that takes no credential gets no header at all, rather than an empty key. */
    private static void authorize(HttpHeaders headers, String apiKey) {
        if (apiKey != null) {
            headers.set("x-goog-api-key", apiKey);
        }
    }

    private ObjectNode requestBody(ModelRef model, CompletionRequest request) {
        ObjectNode body = json.createObjectNode();
        StringBuilder system = new StringBuilder();
        ArrayNode contents = body.putArray("contents");
        for (ChatMessage message : request.messages()) {
            if (message.role() == ChatMessage.Role.SYSTEM) {
                system.append(system.isEmpty() ? "" : "\n\n").append(message.content());
                continue;
            }
            contents.addObject()
                    .put("role", message.role() == ChatMessage.Role.ASSISTANT ? "model" : "user")
                    .putArray("parts")
                    .addObject()
                    .put("text", message.content());
        }
        if (!system.isEmpty()) {
            body.putObject("systemInstruction").putArray("parts").addObject().put("text", system.toString());
        }
        ObjectNode generation = body.putObject("generationConfig");
        generation.put("maxOutputTokens", request.maxOutputTokens());
        if (request.temperature() != null) {
            generation.put("temperature", request.temperature());
        }
        if (request.jsonMode()) {
            generation.put("responseMimeType", "application/json");
        }
        return body;
    }

    private ProviderCompletion readCompletion(String provider, String response) {
        JsonNode root = parse(provider, response);
        String blockReason = ProviderHttp.textAt(root, "promptFeedback", "blockReason");
        if (blockReason != null) {
            throw new LlmProviderException(LlmErrorCode.PROVIDER_REJECTED,
                    provider + " blocked the prompt (" + blockReason + ")", false);
        }
        JsonNode candidates = ProviderHttp.at(root, "candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw ProviderHttp.malformed(provider, "no candidates in the response");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : candidates) {
            for (JsonNode part : ProviderHttp.at(candidate, "content", "parts")) {
                String piece = ProviderHttp.textAt(part, "text");
                if (piece != null) {
                    text.append(piece);
                }
            }
        }
        if (text.isEmpty()) {
            throw ProviderHttp.malformed(provider, "the candidate carried no text");
        }
        return new ProviderCompletion(
                text.toString(),
                ProviderHttp.intAt(root, "usageMetadata", "promptTokenCount"),
                ProviderHttp.intAt(root, "usageMetadata", "candidatesTokenCount"));
    }

    private ProviderEmbeddings readEmbeddings(String provider, String response, int expected) {
        JsonNode embeddings = ProviderHttp.at(parse(provider, response), "embeddings");
        if (!embeddings.isArray() || embeddings.size() != expected) {
            throw ProviderHttp.malformed(provider,
                    "expected " + expected + " embedding(s) but the response carried " + embeddings.size());
        }
        List<float[]> vectors = new ArrayList<>(expected);
        for (JsonNode embedding : embeddings) {
            vectors.add(toVector(provider, embedding.path("values")));
        }
        // Gemini does not report token usage for batch embeddings, so no token count is invented here.
        return new ProviderEmbeddings(vectors, 0);
    }

    private float[] toVector(String provider, JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            throw ProviderHttp.malformed(provider, "an embedding carried no values");
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            vector[index] = (float) values.get(index).asDouble();
        }
        return vector;
    }

    private JsonNode parse(String provider, String response) {
        try {
            return json.readTree(response == null ? "" : response);
        } catch (RuntimeException failure) {
            throw ProviderHttp.malformed(provider, "the response was not JSON");
        }
    }
}
