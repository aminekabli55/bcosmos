package com.bcosmos.backend.ai.gateway;

import java.util.Map;

/**
 * The one place that reads process environment variables (spec v2 section 10: credentials come from the
 * environment).
 *
 * <p>It is an interface for one reason: a test that needs to prove "the key came from {@code DEEPSEEK_API_KEY}"
 * cannot set an environment variable, so it supplies a map instead. Nothing else may read the environment.</p>
 *
 * <p>In production the implementation is backed by Spring's {@code Environment}, which resolves a process
 * environment variable of that name and nothing else of interest — the variable names are uppercase and namespaced
 * enough not to collide. Going through Spring is what lets an integration test hand the gateway a key with
 * {@code @SpringBootTest(properties = "DEEPSEEK_API_KEY=...")} instead of depending on the machine it runs on.</p>
 */
@FunctionalInterface
public interface HostEnvironment {

    /** The value of a variable, or {@code null} when it is unset or blank. */
    String variable(String name);

    /**
     * A fixed map, for tests.
     *
     * <p>The name is checked before the lookup because a provider row is allowed to name no variable at all — a
     * local Ollama or vLLM server usually does — and an immutable map rejects a null key with an exception rather
     * than reporting a miss.</p>
     */
    static HostEnvironment of(Map<String, String> variables) {
        return name -> {
            if (name == null || name.isBlank()) {
                return null;
            }
            String value = variables.get(name);
            return value == null || value.isBlank() ? null : value;
        };
    }
}
