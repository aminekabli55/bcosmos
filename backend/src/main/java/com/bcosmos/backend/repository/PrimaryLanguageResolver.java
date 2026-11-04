package com.bcosmos.backend.repository;

import java.util.Map;

/**
 * Chooses the language that represents a repository, from the files a run really analysed (spec v2 section 3.1).
 *
 * <p>Phase 1 registered every repository as Java because there was one adapter, and the repository list therefore
 * showed "Java · L3" for a TypeScript project after Phase 10 added the second one (ADR 0033). The census uses what
 * discovery already produced — no second walk of the repository and no parsing — so it stays a by-product of a run.
 * A run whose files carry no usable language leaves the stored value alone rather than blanking it.</p>
 */
public final class PrimaryLanguageResolver {

    private PrimaryLanguageResolver() {
    }

    /**
     * @param current the language stored on the repository, returned unchanged when nothing wins
     * @param filesByLanguage files per language id, as persisted on {@code file_record}
     * @return the language with the most files; ties are broken alphabetically so the answer is deterministic
     */
    public static String resolve(String current, Map<String, Long> filesByLanguage) {
        String winner = null;
        long winnerFiles = 0;
        for (Map.Entry<String, Long> entry : filesByLanguage.entrySet()) {
            String language = entry.getKey();
            long files = entry.getValue() == null ? 0 : entry.getValue();
            if (language == null || language.isBlank() || files <= 0) {
                continue;
            }
            if (winner == null || files > winnerFiles || (files == winnerFiles && language.compareTo(winner) < 0)) {
                winner = language;
                winnerFiles = files;
            }
        }
        return winner == null ? current : winner;
    }
}
