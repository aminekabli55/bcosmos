package com.bcosmos.backend.ai.rag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * The descriptions retrieval attaches to a finding's rule (spec v2 section 7.2).
 *
 * <p>They live in {@code rules/rule-descriptions.properties}, next to the rule set they describe, and are read
 * once. A rule with no entry is not guessed at: {@link #describe(String)} says so, and the prompt then works from
 * the tool's own message alone. A missing entry is visible in the test suite rather than papered over with an
 * invented description.</p>
 */
@Component
public class RuleCatalog {

    /** Classpath location of the descriptions. */
    public static final String LOCATION = "classpath:rules/rule-descriptions.properties";

    private static final String NO_DESCRIPTION =
            "(no description is recorded for this rule; work from the tool's message and the code)";

    private final Map<String, String> descriptions;

    public RuleCatalog(ResourceLoader resources) {
        this.descriptions = Map.copyOf(read(resources));
    }

    /** The description of one rule id, or an explicit "nothing recorded" sentence. */
    public String describe(String ruleId) {
        if (ruleId == null) {
            return NO_DESCRIPTION;
        }
        return descriptions.getOrDefault(ruleId, NO_DESCRIPTION);
    }

    /** Rule ids the catalog describes; the rule catalog test compares this with the analysis baseline. */
    public Set<String> knownRuleIds() {
        return descriptions.keySet();
    }

    /** True when the catalog has an entry for this rule. */
    public boolean knows(String ruleId) {
        return ruleId != null && descriptions.containsKey(ruleId);
    }

    private static Map<String, String> read(ResourceLoader resources) {
        Resource resource = resources.getResource(LOCATION);
        if (!resource.exists()) {
            throw new IllegalStateException("rule descriptions " + LOCATION + " are missing");
        }
        Properties loaded = new Properties();
        try (InputStream in = resource.getInputStream();
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        } catch (IOException failure) {
            throw new IllegalStateException("rule descriptions " + LOCATION + " could not be read", failure);
        }
        Map<String, String> descriptions = new LinkedHashMap<>();
        loaded.stringPropertyNames().stream().sorted()
                .forEach(name -> descriptions.put(name, loaded.getProperty(name).trim()));
        return descriptions;
    }
}
