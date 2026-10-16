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
 * Adapter for any endpoint that speaks the OpenAI chat-completions and embeddings shape (spec v2 section 7.1).
 *
 * <p>That is the point of this adapter and the reason the provider is data: DeepSeek, Groq, OpenRouter, xAI,
 * Mistral, Together, Ollama, vLLM and Anthropic's compatibility layer all expose this contract, so supporting one
 * of them is a base URL and a key in {@code llm_provider} — no class, no rebuild. The base URL is the provider's
 * versioned root ({@code https://api.deepseek.com/v1}); the paths below are appended to it.</p>
 *
 * <p>Structured calls ask for {@code response_format: json_object} and the answer is still validated — JSON mode
 * says "this is JSON", never "this matches the schema", which is what makes a provider that ignores the parameter
 * (Anthropic's compatibility layer does) safe to use.</p>
 */
public class OpenAiCompatibleAdapter implements LlmProviderAdapter {

    private static final String CHAT_PATH = "/chat/completions";
    private static final String EMBED_PATH = "/embeddings";

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
    public ProviderProtocol protocol() {
        return ProviderProtocol.OPENAI_COMPATIBLE;
    }

    @Override
    public ProviderCompletion complete(ModelRef model, CompletionRequest request) {
        // Null when the provider row says it takes no credential, which is the normal case for a local server.
        String apiKey = credentials.resolve(model.provider());
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
        String provider = model.provider().code();
        try {
            String response = http.post()
                    .uri(credentials.requireBaseUrl(model.provider()) + CHAT_PATH)
                    .headers(headers -> authorize(headers, apiKey))
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readCompletion(provider, response);
        } catch (RuntimeException failure) {
            throw ProviderHttp.map(provider, failure, json);
        }
    }

    @Override
    public ProviderEmbeddings embed(ModelRef model, List<String> texts) {
        String apiKey = credentials.resolve(model.provider());
        ObjectNode body = json.createObjectNode();
        body.put("model", model.modelName());
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        String provider = model.provider().code();
        try {
            String response = http.post()
                    .uri(credentials.requireBaseUrl(model.provider()) + EMBED_PATH)
                    .headers(headers -> authorize(headers, apiKey))
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readEmbeddings(provider, response, texts.size());
        } catch (RuntimeException failure) {
            throw ProviderHttp.map(provider, failure, json);
        }
    }

    /** A provider that takes no credential gets no header at all, rather than an empty bearer token. */
    private static void authorize(HttpHeaders headers, String apiKey) {
        if (apiKey != null) {
            headers.set("Authorization", "Bearer " + apiKey);
        }
    }

    private ProviderCompletion readCompletion(String provider, String response) {
        JsonNode root = parse(provider, response);
        JsonNode choices = ProviderHttp.at(root, "choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw ProviderHttp.malformed(provider, "no choices in the response");
        }
        String text = ProviderHttp.textAt(choices.get(0), "message", "content");
        if (text == null) {
            String refusal = ProviderHttp.textAt(choices.get(0), "finish_reason");
            throw ProviderHttp.malformed(provider,
                    refusal == null ? "the choice carried no content" : "the choice ended with " + refusal);
        }
        return new ProviderCompletion(text,
                ProviderHttp.intAt(root, "usage", "prompt_tokens"),
                ProviderHttp.intAt(root, "usage", "completion_tokens"));
    }

    private ProviderEmbeddings readEmbeddings(String provider, String response, int expected) {
        JsonNode root = parse(provider, response);
        JsonNode data = ProviderHttp.at(root, "data");
        if (!data.isArray() || data.size() != expected) {
            throw ProviderHttp.malformed(provider,
                    "expected " + expected + " embedding(s) but the response carried " + data.size());
        }
        List<float[]> vectors = new ArrayList<>(expected);
        for (JsonNode item : data) {
            vectors.add(toVector(provider, item.path("embedding")));
        }
        return new ProviderEmbeddings(vectors, ProviderHttp.intAt(root, "usage", "prompt_tokens"));
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
