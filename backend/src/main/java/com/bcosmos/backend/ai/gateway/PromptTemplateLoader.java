package com.bcosmos.backend.ai.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads prompt templates from {@code classpath:prompts/}.
 *
 * <p>Files are named {@code <name>.<version>.md} with a sibling {@code <name>.<version>.json} holding the schema, so
 * the version a call used is visible in the repository and stored on the usage row. Templates are read once and
 * cached; a missing or unreadable file fails loudly rather than sending a half-built prompt.</p>
 */
public class PromptTemplateLoader {

    /** Classpath folder holding the versioned prompts. */
    public static final String PROMPTS_LOCATION = "classpath:prompts/%s.%s.md";

    private static final String SCHEMA_LOCATION = "classpath:prompts/%s.%s.json";

    private final ResourceLoader resources;
    private final ObjectMapper json;
    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    public PromptTemplateLoader(ResourceLoader resources, ObjectMapper json) {
        this.resources = resources;
        this.json = json;
    }

    /** The template with this name and version. */
    public PromptTemplate load(String name, String version) {
        return cache.computeIfAbsent(name + "." + version, key -> read(name, version));
    }

    private PromptTemplate read(String name, String version) {
        String body = text(PROMPTS_LOCATION.formatted(name, version));
        Resource schemaResource = resources.getResource(SCHEMA_LOCATION.formatted(name, version));
        if (!schemaResource.exists()) {
            throw new PromptTemplateException(
                    "prompt " + name + "." + version + " has no schema file (" + SCHEMA_LOCATION.formatted(name, version) + ")");
        }
        JsonNode schema;
        try (var input = schemaResource.getInputStream()) {
            schema = json.readTree(input);
        } catch (IOException | RuntimeException failure) {
            throw new PromptTemplateException("prompt schema " + name + "." + version + " could not be read", failure);
        }
        return new PromptTemplate(name, version, body, schema);
    }

    private String text(String location) {
        Resource resource = resources.getResource(location);
        if (!resource.exists()) {
            throw new PromptTemplateException("prompt file " + location + " does not exist");
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new PromptTemplateException("prompt file " + location + " could not be read", failure);
        }
    }
}
