package com.bcosmos.backend.ai.gateway;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * A versioned prompt file and the schema that goes with it (spec v2 section 7.1: "prompt templates in versioned
 * files, referenced by {@code prompt_version}").
 *
 * <p>Placeholders are written {@code {{name}}} and are filled by {@link #render(Map)}. A placeholder with no value
 * fails instead of reaching a model as literal braces: an unfilled {@code {{snippet}}} would quietly turn an
 * explanation request into a request about nothing.</p>
 *
 * @param name    prompt name, for example {@code explain-finding}
 * @param version file version, for example {@code v1}
 * @param body    the instruction text, with placeholders
 * @param schema  the JSON Schema a structured answer must satisfy
 */
public record PromptTemplate(String name, String version, String body, JsonNode schema) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    public PromptTemplate {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(schema, "schema");
    }

    /** The version stamp stored with a call, for example {@code explain-finding.v1}. */
    public String id() {
        return name + "." + version;
    }

    /** Fills the placeholders. */
    public String render(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(body);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null) {
                throw new PromptTemplateException("prompt " + id() + " needs a value for '" + key + "'");
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
