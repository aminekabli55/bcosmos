package com.bcosmos.backend.ai.suggestion;

import com.bcosmos.backend.ai.rag.EvidenceBundle;
import com.bcosmos.backend.jobs.LanguageAdapterRegistry;
import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParsedFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks a suggestion against the code it claims to fix (spec v2 section 7.3).
 *
 * <p>Five checks, in the order that wastes the least work:</p>
 * <ol>
 *   <li><b>evidence</b> - every cited file is one of the run's files and every cited line range exists in the
 *       stored revision. A model that cites a file it was never shown has invented it;</li>
 *   <li><b>diff applies</b> - the diff is well formed and matches the analysed revision, in memory only;</li>
 *   <li><b>parses</b> - the patched file still parses, through the same language adapter that parsed it before;</li>
 *   <li><b>target finding gone</b> - the finding's fingerprint is no longer reported for that file;</li>
 *   <li><b>no new findings</b> - any fingerprint the patch added is listed, so a "fix" that breaks something else
 *       is visible instead of looking clean.</li>
 * </ol>
 *
 * <p>What is deliberately not here: compiling the patch and running tests. There is no sandbox in this phase, so
 * the verdict says so. Nothing is ever written to the repository (spec v2 section 1.3).</p>
 */
@Component
public class PatchValidator {

    private static final Logger log = LoggerFactory.getLogger(PatchValidator.class);

    private final LanguageAdapterRegistry adapters;

    public PatchValidator(LanguageAdapterRegistry adapters) {
        this.adapters = adapters;
    }

    /**
     * Validates one suggestion.
     *
     * @param targetFingerprint the finding's fingerprint (spec v2 section 4.2), which is what "is it gone?" means
     * @param filePath       repository-relative path of that finding's file
     * @param originalSource the stored revision of that file
     * @param suggestion     the model's answer
     * @param schemaOk       whether the answer passed the JSON schema without a retry
     * @param evidence       what retrieval showed the model, for a note when the cited file was not in it
     * @param fileLineCounts lines per file of the run, used to check cited line ranges
     */
    public ValidationVerdict validate(String targetFingerprint, String filePath, String originalSource,
            FixSuggestion suggestion, boolean schemaOk, EvidenceBundle evidence,
            Map<String, Integer> fileLineCounts) {
        List<String> notes = new ArrayList<>(List.of(ValidationVerdict.COMPILE_NOT_VERIFIED,
                ValidationVerdict.TESTS_NOT_RUN));
        boolean evidenceOk = checkEvidence(suggestion.evidence(), evidence, fileLineCounts, notes);

        UnifiedDiff diff;
        try {
            diff = UnifiedDiff.parse(suggestion.proposedDiff());
        } catch (InvalidDiffException failure) {
            notes.add("the diff was rejected: " + failure.getMessage());
            return new ValidationVerdict(schemaOk, evidenceOk, false, false, false, List.of(), notes);
        }
        diff.path().filter(path -> !path.equals(filePath))
                .ifPresent(path -> notes.add("the diff names " + path + " but the finding is in " + filePath));

        var patched = diff.applyTo(originalSource);
        if (patched.isEmpty()) {
            notes.add("the diff does not apply to the revision that was analysed; the model may have worked from"
                    + " the evidence bundle alone");
            return new ValidationVerdict(schemaOk, evidenceOk, false, false, false, List.of(), notes);
        }

        Path path = Path.of(filePath);
        var adapter = adapters.forFile(path);
        if (adapter.isEmpty()) {
            notes.add("no language adapter handles " + filePath + ", so the patched file was not parsed");
            return new ValidationVerdict(schemaOk, evidenceOk, true, false, false, List.of(), notes);
        }

        List<Finding> before = findingsOf(adapter.get(), path, originalSource, notes);
        List<Finding> after = findingsOf(adapter.get(), path, patched.get(), notes);
        if (after == null) {
            notes.add("the patched file does not parse, so no finding could be re-checked");
            return new ValidationVerdict(schemaOk, evidenceOk, true, false, false, List.of(), notes);
        }

        Set<String> beforeFingerprints = fingerprintsOf(before);
        boolean targetFindingGone = !after.stream()
                .anyMatch(found -> found.fingerprint().equals(targetFingerprint));
        List<ValidationVerdict.NewFinding> newFindings = after.stream()
                .filter(found -> !beforeFingerprints.contains(found.fingerprint()))
                .map(found -> new ValidationVerdict.NewFinding(found.ruleId(), found.span().startLine(),
                        found.message()))
                .toList();
        if (!targetFindingGone) {
            notes.add("the rule still reports this finding in the patched file");
        }
        log.debug("validated a suggestion for {}: applies=true parses=true gone={} new={}", filePath,
                targetFindingGone, newFindings.size());
        return new ValidationVerdict(schemaOk, evidenceOk, true, true, targetFindingGone, newFindings, notes);
    }

    /** Runs the file through its adapter; {@code null} means "it did not parse". */
    private <T extends ParsedFile> List<Finding> findingsOf(LanguageAdapter<T> adapter, Path path, String source,
            List<String> notes) {
        try {
            T parsed = adapter.parse(path, source);
            if (!parsed.parsed()) {
                return null;
            }
            return adapter.analyze(parsed, adapter.defaultRuleSet());
        } catch (RuntimeException failure) {
            notes.add("the file could not be analysed: " + failure.getMessage());
            return null;
        }
    }

    private static Set<String> fingerprintsOf(List<Finding> findings) {
        Set<String> fingerprints = new LinkedHashSet<>();
        if (findings != null) {
            findings.forEach(finding -> fingerprints.add(finding.fingerprint()));
        }
        return fingerprints;
    }

    /**
     * Whether every citation can be checked against the run.
     *
     * <p>An absolute path, a file the run never saw, or a line range outside the stored file all fail. A citation
     * with no lines (a whole file) only has to exist.</p>
     */
    private static boolean checkEvidence(List<EvidenceRef> citations, EvidenceBundle evidence,
            Map<String, Integer> fileLineCounts, List<String> notes) {
        if (citations.isEmpty()) {
            notes.add("the suggestion cites no evidence at all");
            return false;
        }
        Set<String> shown = new LinkedHashSet<>();
        evidence.items().forEach(item -> shown.add(item.file()));

        boolean ok = true;
        for (EvidenceRef citation : citations) {
            String file = citation.file();
            if (file == null || file.isBlank()) {
                notes.add("a citation has no file");
                ok = false;
                continue;
            }
            if (file.startsWith("/") || file.contains(":\\")) {
                notes.add("a citation names an absolute path, which the response is never allowed to contain");
                ok = false;
                continue;
            }
            Integer lineCount = fileLineCounts.get(file);
            if (lineCount == null) {
                notes.add("the suggestion cites " + file + ", which is not a file of this run");
                ok = false;
                continue;
            }
            if (citation.startLine() == null) {
                continue;
            }
            Integer endLine = citation.endLine() == null ? citation.startLine() : citation.endLine();
            if (citation.startLine() < 1 || endLine > lineCount || endLine < citation.startLine()) {
                notes.add("the citation " + file + ":" + citation.startLine() + "-" + endLine
                        + " is outside the stored file, which has " + lineCount + " line(s)");
                ok = false;
                continue;
            }
            if (!shown.contains(file)) {
                notes.add("the citation " + file + " was not part of the evidence the model was shown");
            }
        }
        return ok;
    }
}
