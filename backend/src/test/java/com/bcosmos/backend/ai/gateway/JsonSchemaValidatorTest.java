package com.bcosmos.backend.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The validator decides whether a model answer is usable, so its behaviour (and its documented limits) is pinned
 * here.
 */
class JsonSchemaValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SCHEMA = """
            {
              "type": "object",
              "required": ["summary", "confidence", "references"],
              "properties": {
                "summary": {"type": "string"},
                "confidence": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
                "references": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["file", "startLine"],
                    "properties": {
                      "file": {"type": "string"},
                      "startLine": {"type": "integer"}
                    }
                  }
                },
                "score": {"type": ["integer", "null"]}
              }
            }
            """;

    private final JsonSchemaValidator validator = new JsonSchemaValidator();

    @Test
    void acceptsAConformingObject() {
        assertThat(validator.validate(schema(), json("""
                {
                  "summary": "empty catch swallows the failure",
                  "confidence": "HIGH",
                  "references": [{"file": "src/IngestRunner.java", "startLine": 22}],
                  "score": null
                }
                """))).isEmpty();
    }

    @Test
    void namesThePathOfEveryMissingRequiredProperty() {
        List<String> problems = validator.validate(schema(), json("""
                {"explanation": "text"}
                """));

        assertThat(problems).containsExactlyInAnyOrder(
                "$.summary: required property is missing",
                "$.confidence: required property is missing",
                "$.references: required property is missing");
    }

    @Test
    void reportsATypeMismatchWithoutWalkingChildren() {
        List<String> problems = validator.validate(schema(), json("""
                {"summary": 42, "confidence": "HIGH", "references": []}
                """));

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).isEqualTo("$.summary: expected string but found 42");
    }

    @Test
    void reportsAnEnumViolationWithTheAllowedValues() {
        List<String> problems = validator.validate(schema(), json("""
                {"summary": "x", "confidence": "probably", "references": []}
                """));

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst())
                .isEqualTo("$.confidence: value 'probably' is not one of [LOW, MEDIUM, HIGH]");
    }

    @Test
    void pointsAtTheOffendingArrayItem() {
        List<String> problems = validator.validate(schema(), json("""
                {
                  "summary": "x",
                  "confidence": "LOW",
                  "references": [
                    {"file": "a.java", "startLine": 1},
                    {"file": "b.java", "startLine": "line 7"}
                  ]
                }
                """));

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).isEqualTo("$.references[1].startLine: expected integer but found 'line 7'");
    }

    @Test
    void aNumberIsNotAnIntegerBecauseJsonKeepsThemApart() {
        List<String> problems = validator.validate(schema(), json("""
                {"summary": "x", "confidence": "LOW", "references": [], "score": 1.5}
                """));

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).isEqualTo("$.score: expected integer or null but found 1.5");
    }

    @Test
    void ignoresKeywordsItDoesNotImplement() {
        JsonNode schemaWithExtras = json("""
                {
                  "type": "object",
                  "required": ["summary"],
                  "additionalProperties": false,
                  "properties": {
                    "summary": {"type": "string", "minLength": 5000, "format": "email", "pattern": "^never$"}
                  }
                }
                """);

        assertThat(validator.validate(schemaWithExtras, json("""
                {"summary": "short", "extra": "allowed because additionalProperties is not enforced"}
                """))).isEmpty();
    }

    private static JsonNode schema() {
        return json(SCHEMA);
    }

    private static JsonNode json(String document) {
        return JSON.readTree(document);
    }
}
