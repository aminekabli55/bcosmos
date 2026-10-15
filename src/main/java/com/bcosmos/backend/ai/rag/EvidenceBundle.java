package com.bcosmos.backend.ai.rag;

import java.util.List;
import java.util.Objects;

/**
 * Everything retrieval gathered for one finding, with its own cost accounting (spec v2 section 7.2).
 *
 * <p>Every item carries a file and a line range, because a suggestion must be able to cite what it was shown, and
 * the API verifies those citations afterwards. {@code notes} is where the honest gaps go: what retrieval could not
 * provide is stated in the prompt and in the response, not left for the reader to assume.</p>
 */
public record EvidenceBundle(
        List<EvidenceItem> items,
        String ruleDescription,
        List<String> notes,
        int estimatedTokens,
        String embedModel,
        int consideredChunks,
        boolean truncated) {

    public EvidenceBundle {
        items = List.copyOf(items);
        notes = List.copyOf(notes);
        Objects.requireNonNull(ruleDescription, "ruleDescription");
        Objects.requireNonNull(embedModel, "embedModel");
    }

    /** Whether retrieval produced anything a prompt can use. */
    public boolean empty() {
        return items.isEmpty();
    }

    /** The enclosing declaration, when retrieval found one. */
    public java.util.Optional<EvidenceItem> enclosing() {
        return items.stream().filter(item -> item.kind() == EvidenceKind.ENCLOSING).findFirst();
    }

    /**
     * One piece of evidence: where it is, what it says, and how it got here.
     *
     * @param kind       why this item is in the bundle
     * @param symbolId   symbol it came from, or 0 for an item that is not a symbol chunk
     * @param file       repository-relative path
     * @param startLine  first line of the item, relative to the file
     * @param endLine    last line of the item
     * @param similarity cosine similarity {@code [0,1]} for a similar chunk, {@code null} when the item was not
     *                   selected by similarity (the enclosing declaration is exact, not retrieved)
     */
    public record EvidenceItem(
            EvidenceKind kind,
            long symbolId,
            String file,
            int startLine,
            int endLine,
            String qualifiedName,
            String text,
            Double similarity,
            int estimatedTokens) {

        public EvidenceItem {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(text, "text");
        }
    }

    /** Why an item is in the bundle. */
    public enum EvidenceKind {

        /** The declaration the finding sits in, taken from the symbol table: exact, not a guess. */
        ENCLOSING,

        /** A chunk that a vector search ranked close to the finding. */
        SIMILAR
    }
}
