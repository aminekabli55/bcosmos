package com.bcosmos.backend.ai.gateway;

import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Validates a model answer against the JSON Schema that was sent in the prompt (spec v2 section 7.1: "JSON schema in
 * the prompt, parse, validate, and retry once with the validation error").
 *
 * <p>It implements the subset of JSON Schema this project writes in its prompt resources, and it is deliberate
 * about saying so instead of pretending to be a full validator:</p>
 * <ul>
 *   <li>supported: {@code type} (string or list), {@code required}, {@code properties}, {@code items},
 *       {@code enum}, plus nesting</li>
 *   <li>ignored: every other keyword, including {@code additionalProperties}, {@code format},
 *       {@code minimum}/{@code maximum}, {@code pattern}, {@code oneOf}/{@code anyOf}</li>
 * </ul>
 * <p>Prompt schemas therefore state constraints this class can actually check. A schema keyword nobody enforces is
 * worse than no schema: it produces a validated-looking answer that was never checked.</p>
 */
public final class JsonSchemaValidator {

    /** Guards against a pathological schema, not against a large answer. */
    private static final int MAX_DEPTH = 32;

    private static final int MAX_VALUE_IN_MESSAGE = 40;

    /** Validation messages, each naming the JSON path that failed. Empty means the value is valid. */
    public List<String> validate(JsonNode schema, JsonNode value) {
        List<String> errors = new ArrayList<>();
        validateNode(schema, value, "$", errors, 0);
        return List.copyOf(errors);
    }

    private void validateNode(JsonNode schema, JsonNode value, String path, List<String> errors, int depth) {
        if (schema == null || !schema.isObject()) {
            return;
        }
        if (depth > MAX_DEPTH) {
            errors.add(path + ": the schema nests deeper than " + MAX_DEPTH + " levels");
            return;
        }
        JsonNode type = schema.get("type");
        if (type != null && !matchesType(type, value)) {
            errors.add(path + ": expected " + describeType(type) + " but found " + describeValue(value));
            return;
        }
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray() && !isOneOf(enumNode, value)) {
            errors.add(path + ": value " + describeValue(value) + " is not one of " + describeTypes(enumNode));
        }
        if (value.isArray() && schema.has("items")) {
            for (int index = 0; index < value.size(); index++) {
                validateNode(schema.get("items"), value.get(index), path + "[" + index + "]", errors, depth + 1);
            }
        }
        if (value.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode name : required) {
                    if (!value.has(name.asText())) {
                        errors.add(child(path, name.asText()) + ": required property is missing");
                    }
                }
            }
            JsonNode properties = schema.get("properties");
            if (properties != null && properties.isObject()) {
                properties.properties().forEach(property -> {
                    if (value.has(property.getKey())) {
                        validateNode(property.getValue(), value.get(property.getKey()),
                                child(path, property.getKey()), errors, depth + 1);
                    }
                });
            }
        }
    }

    private static boolean matchesType(JsonNode type, JsonNode value) {
        for (String expected : typesOf(type)) {
            boolean matches = switch (expected) {
                case "object" -> value.isObject();
                case "array" -> value.isArray();
                case "string" -> value.isTextual();
                case "integer" -> value.isIntegralNumber();
                case "number" -> value.isNumber();
                case "boolean" -> value.isBoolean();
                case "null" -> value.isNull();
                default -> false;
            };
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOneOf(JsonNode allowed, JsonNode value) {
        for (JsonNode candidate : allowed) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> typesOf(JsonNode type) {
        if (type.isTextual()) {
            return List.of(type.asText());
        }
        List<String> types = new ArrayList<>();
        if (type.isArray()) {
            type.forEach(node -> types.add(node.asText()));
        }
        return types;
    }

    private static String describeType(JsonNode type) {
        return String.join(" or ", typesOf(type));
    }

    private static String describeTypes(JsonNode values) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        values.forEach(node -> joiner.add(node.asText()));
        return joiner.toString();
    }

    /** Short, and never a wall of model output: enough to name what was wrong, not to log the whole answer. */
    private static String describeValue(JsonNode value) {
        if (value.isObject()) {
            return "an object";
        }
        if (value.isArray()) {
            return "an array";
        }
        String rendered = value.isTextual() ? "'" + value.asText() + "'" : value.toString();
        return rendered.length() > MAX_VALUE_IN_MESSAGE
                ? rendered.substring(0, MAX_VALUE_IN_MESSAGE) + "…'"
                : rendered;
    }

    /** JSON property names are case-sensitive, so the path keeps them exactly as the schema wrote them. */
    private static String child(String path, String name) {
        return path + "." + name;
    }
}
