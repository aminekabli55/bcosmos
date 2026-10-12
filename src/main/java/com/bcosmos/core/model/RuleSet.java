package com.bcosmos.core.model;

import java.util.Objects;
import java.util.Set;

/**
 * Identity of the rules a run was analyzed with.
 *
 * <p>{@link #id()} names the rule set the adapter knows (for Java: {@code bcosmos-baseline}, loaded from
 * {@code pmd/bcosmos-baseline.xml}). {@link #enabledRuleIds()} narrows it further when a user disables rules;
 * an empty set means "every rule in the rule set".</p>
 *
 * <p>{@link #hash()} is the stable fingerprint of the rule set definition, stored on every analysis run so a
 * later run can be compared with an earlier one.</p>
 *
 * @param id             rule set id
 * @param enabledRuleIds rule ids to keep, empty means all of them
 * @param hash           sha256 of the rule set definition
 */
public record RuleSet(String id, Set<String> enabledRuleIds, String hash) {

    public RuleSet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hash, "hash");
        enabledRuleIds = enabledRuleIds == null ? Set.of() : Set.copyOf(enabledRuleIds);
    }

    /** All rules of the rule set. */
    public static RuleSet all(String id, String hash) {
        return new RuleSet(id, Set.of(), hash);
    }

    public boolean includes(String ruleId) {
        return enabledRuleIds.isEmpty() || enabledRuleIds.contains(ruleId);
    }
}
