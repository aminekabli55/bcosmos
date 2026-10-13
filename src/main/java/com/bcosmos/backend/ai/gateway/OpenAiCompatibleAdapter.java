package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.LlmProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adapter for any endpoint that speaks the OpenAI chat-completions and embeddings shape (spec v2 section 7.1).
 *
 * <p>That is the point of this adapter: several vendors expose the same HTTP contract, so switching between them is
 * a base URL and a key, not a new class. Structured calls ask for {@code response_format: json_object} and the
 * answer is still validated - JSON mode says "this is JSON", never "this matches the schema".</p>
 */
public class OpenAiCompatibleAdapter implements LlmProviderAdapter {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String EMBED_PATH = "/v1/embeddings";

    private final AiProperties properties;
    private final ProviderCredentials credentials;
    private final RestClient http;
    private final ObjectMapper json;

    public OpenAiCompatibleAdapter(AiProperties properties, ProviderCredentials credentials, ObjectMapper json) {
        this.properties = properties;
        this.credentials = credentials;
        this.http = ProviderHttp.client(properties);
        this.json = json;
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI_COMPATIBLE;
    }

    @Override
    public ProviderCompletion complete(ModelRef model, CompletionRequest request) {
        String apiKey = credentials.require(provider());
        ObjectNode body = json.createObjectNode();
        body.put("model", model.modelName());
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage message : request.messages()) {
            messages.addObject()
                    .put("role", message.role().name().toLowerCase(java.util.Locale.ROOT))
                    .put("content", message.content());
        }
        body.put("max_tokens", request.maxOutputTokens());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (request.jsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        try {
            String response = http.post()
                    .uri(credentials.baseUrl(provider()) + CHAT_PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readCompletion(response);
        } catch (RuntimeException failure) {
            throw ProviderHttp.map(provider(), failure);
        }
    }

    @Override
    public ProviderEmbeddings embed(ModelRef model, List<String> texts) {
        String apiKey = credentials.require(provider());
        ObjectNode body = json.createObjectNode();
        body.put("model", model.modelName());
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        try {
            String response = http.post()
                    .uri(credentials.baseUrl(provider()) + EMBED_PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readEmbeddings(response, texts.size());
        } catch (RuntimeException failure) {
            throw ProviderHttp.map(provider(), failure);
        }
    }

    private ProviderCompletion readCompletion(String response) {
        JsonNode root = parse(response);
        JsonNode choices = ProviderHttp.at(root, "choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw ProviderHttp.malformed(provider(), "no choices in the response");
        }
        String text = ProviderHttp.textAt(choices.get(0), "message", "content");
        if (text == null) {
            String refusal = ProviderHttp.textAt(choices.get(0), "finish_reason");
            throw ProviderHttp.malformed(provider(),
                    refusal == null ? "the choice carried no content" : "the choice ended with " + refusal);
        }
        return new ProviderCompletion(text,
                ProviderHttp.intAt(root, "usage", "prompt_tokens"),
                ProviderHttp.intAt(root, "usage", "completion_tokens"));
    }

    private ProviderEmbeddings readEmbeddings(String response, int expected) {
        JsonNode root = parse(response);
        JsonNode data = ProviderHttp.at(root, "data");
        if (!data.isArray() || data.size() != expected) {
            throw ProviderHttp.malformed(provider(),
                    "expected " + expected + " embedding(s) but the response carried " + data.size());
        }
        List<float[]> vectors = new ArrayList<>(expected);
        for (JsonNode item : data) {
            vectors.add(toVector(item.path("embedding")));
        }
        return new ProviderEmbeddings(vectors, ProviderHttp.intAt(root, "usage", "prompt_tokens"));
    }

    private float[] toVector(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            throw ProviderHttp.malformed(provider(), "an embedding carried no values");
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            vector[index] = (float) values.get(index).asDouble();
        }
        return vector;
    }

    private JsonNode parse(String response) {
        try {
            return json.readTree(response == null ? "" : response);
        } catch (RuntimeException failure) {
            throw ProviderHttp.malformed(provider(), "the response was not JSON");
        }
    }
}
