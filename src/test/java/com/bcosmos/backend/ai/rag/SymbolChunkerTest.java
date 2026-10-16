package com.bcosmos.backend.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.config.RetrievalProperties;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.SymbolKind;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Chunking decides what gets embedded, so its rules are asserted directly: one chunk per declaration worth
 * reasoning about, whole lines, a header a citation can be traced from, and no silent cost explosion from a huge
 * class (spec v2 section 7.2).
 */
class SymbolChunkerTest {

    private static final String SOURCE = """
            package com.example;

            class Reader {
                private int count;

                String read(String path) {
                    return path;
                }
            }
            """;

    private final SymbolChunker chunker = new SymbolChunker(
            new RetrievalProperties(1536, 8, 6000, 200, 16, Duration.ofMinutes(2)));

    @Test
    void buildsOneChunkPerTypeAndMethodWithAHeaderAndTheExactLines() {
        List<CodeChunkDraft> chunks = chunker.chunks(List.of(
                source(1, SymbolKind.CLASS, "com.example.Reader", 3, 10),
                source(2, SymbolKind.METHOD, "com.example.Reader.read", 6, 8),
                source(3, SymbolKind.FIELD, "com.example.Reader.count", 4, 4)));

        assertThat(chunks).hasSize(2);
        CodeChunkDraft method = chunks.get(1);
        assertThat(method.qualifiedName()).isEqualTo("com.example.Reader.read");
        assertThat(method.startLine()).isEqualTo(6);
        assertThat(method.endLine()).isEqualTo(8);
        assertThat(method.text())
                .startsWith("file: src/main/java/com/example/Reader.java lines 6-8\n"
                        + "symbol: com.example.Reader.read [METHOD]\n--------\n")
                .contains("        return path;");
        assertThat(method.text()).doesNotContain("symbol: com.example.Reader.count");
        assertThat(method.tokenCount()).isEqualTo(TokenEstimator.estimate(method.text()));
        assertThat(method.truncated()).isFalse();
    }

    @Test
    void skipsAFieldBecauseAOneLineDeclarationIsNotAUnitOfCode() {
        List<CodeChunkDraft> chunks = chunker.chunks(List.of(
                source(3, SymbolKind.FIELD, "com.example.Reader.count", 4, 4)));

        assertThat(chunks).isEmpty();
    }

    @Test
    void skipsASymbolWhoseStoredRevisionDoesNotCoverItsLines() {
        List<CodeChunkDraft> chunks = chunker.chunks(List.of(
                source(9, SymbolKind.METHOD, "com.example.Gone.method", 500, 520)));

        assertThat(chunks).isEmpty();
    }

    @Test
    void truncatesAHugeDeclarationAndSaysSo() {
        SymbolChunker tiny = new SymbolChunker(
                new RetrievalProperties(1536, 8, 6000, 60, 16, Duration.ofMinutes(2)));

        List<CodeChunkDraft> chunks = tiny.chunks(List.of(
                source(1, SymbolKind.CLASS, "com.example.Reader", 3, 10)));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().truncated()).isTrue();
        assertThat(chunks.getFirst().text()).contains("[truncated at 60 characters]");
    }

    @Test
    void estimatesTokensAtFourCharactersEach() {
        assertThat(TokenEstimator.estimate(null)).isZero();
        assertThat(TokenEstimator.estimate("")).isZero();
        assertThat(TokenEstimator.estimate("abcd")).isEqualTo(1);
        assertThat(TokenEstimator.estimate("abcde")).isEqualTo(2);
    }

    private static SymbolSource source(long symbolId, SymbolKind kind, String qualifiedName, int startLine,
            int endLine) {
        return new SymbolSource(symbolId, 7L, kind, qualifiedName, "src/main/java/com/example/Reader.java",
                new Span(startLine, 1, endLine, 1), SOURCE);
    }
}
