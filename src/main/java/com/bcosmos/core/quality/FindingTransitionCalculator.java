package com.bcosmos.core.quality;

import com.bcosmos.core.model.FindingTransition;
import com.bcosmos.core.model.FindingTransitionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Calculates lifecycle transitions of findings between two analysis snapshots (Phase 8, spec section 4.1 & 4.2).
 *
 * <p>Invariant (spec section 4.2): A finding is <strong>never</strong> marked {@code RESOLVED} if the file failed to
 * parse in the later run, or if the rule was disabled in the later run. In those cases, it is classified as
 * {@code UNCERTAIN}.</p>
 */
public final class FindingTransitionCalculator {

    public record FindingItem(
            Long id,
            String fingerprint,
            String ruleId,
            String filePath,
            String message) {

        public FindingItem {
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(filePath, "filePath");
        }
    }

    private FindingTransitionCalculator() {
    }

    /**
     * Compares findings between two snapshots and returns their transition classifications.
     *
     * @param fromFindings    findings from the baseline snapshot
     * @param toFindings      findings from the target snapshot
     * @param toFailedFiles   set of relative file paths that failed to parse in the target snapshot
     * @param toActiveRuleIds set of rule IDs active in the target snapshot (empty/null means all rules active)
     */
    public static List<FindingTransition> compare(
            List<FindingItem> fromFindings,
            List<FindingItem> toFindings,
            Set<String> toFailedFiles,
            Set<String> toActiveRuleIds) {

        List<FindingTransition> transitions = new ArrayList<>();
        Set<String> failedFiles = toFailedFiles == null ? Collections.emptySet() : toFailedFiles;
        Set<String> activeRules = toActiveRuleIds == null ? Collections.emptySet() : toActiveRuleIds;

        // Group baseline findings by fingerprint -> list of findings for that fingerprint
        Map<String, List<FindingItem>> fromByFingerprint = new HashMap<>();
        for (FindingItem f : fromFindings) {
            fromByFingerprint.computeIfAbsent(f.fingerprint(), k -> new ArrayList<>()).add(f);
        }

        Set<Long> matchedFromIds = new HashSet<>();

        // Match target findings to baseline findings
        for (FindingItem to : toFindings) {
            List<FindingItem> candidates = fromByFingerprint.getOrDefault(to.fingerprint(), Collections.emptyList());
            FindingItem matchedFrom = null;
            for (FindingItem candidate : candidates) {
                if (!matchedFromIds.contains(candidate.id())) {
                    matchedFrom = candidate;
                    break;
                }
            }

            if (matchedFrom != null) {
                matchedFromIds.add(matchedFrom.id());
                transitions.add(new FindingTransition(
                        matchedFrom.id(),
                        to.id(),
                        to.fingerprint(),
                        to.ruleId(),
                        to.filePath(),
                        FindingTransitionType.PERSISTENT,
                        1.0,
                        "Identical fingerprint in subsequent run"));
            } else {
                transitions.add(new FindingTransition(
                        null,
                        to.id(),
                        to.fingerprint(),
                        to.ruleId(),
                        to.filePath(),
                        FindingTransitionType.NEW,
                        1.0,
                        "New finding introduced in subsequent run"));
            }
        }

        // Process baseline findings that did not appear in the target run
        for (FindingItem from : fromFindings) {
            if (matchedFromIds.contains(from.id())) {
                continue;
            }

            // Check if file failed to parse in target snapshot
            if (failedFiles.contains(from.filePath())) {
                transitions.add(new FindingTransition(
                        from.id(),
                        null,
                        from.fingerprint(),
                        from.ruleId(),
                        from.filePath(),
                        FindingTransitionType.UNCERTAIN,
                        0.5,
                        "Target file failed to parse; resolution unconfirmed"));
            } else if (!activeRules.isEmpty() && !activeRules.contains(from.ruleId())) {
                transitions.add(new FindingTransition(
                        from.id(),
                        null,
                        from.fingerprint(),
                        from.ruleId(),
                        from.filePath(),
                        FindingTransitionType.UNCERTAIN,
                        0.5,
                        "Rule disabled in target run; cannot verify resolution"));
            } else {
                transitions.add(new FindingTransition(
                        from.id(),
                        null,
                        from.fingerprint(),
                        from.ruleId(),
                        from.filePath(),
                        FindingTransitionType.RESOLVED,
                        1.0,
                        "Target finding resolved in subsequent run"));
            }
        }

        return List.copyOf(transitions);
    }
}
