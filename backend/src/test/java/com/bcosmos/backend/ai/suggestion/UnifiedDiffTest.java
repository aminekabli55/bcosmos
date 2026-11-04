package com.bcosmos.backend.ai.suggestion;

import static com.bcosmos.backend.ai.suggestion.TestDiffs.diff;
import static com.bcosmos.backend.ai.suggestion.TestDiffs.replaceLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The diff applier is what turns "the model wrote a patch" into the verdict's {@code diffApplies} flag, so it is
 * tested against the cases that decide that flag: a patch that matches, a patch that does not, a patch with several
 * hunks, and a patch that lies about its own line counts (spec v2 section 7.3, section 11's "patch application").
 */
class UnifiedDiffTest {

    static final String PATH = "src/main/java/com/example/Reader.java";

    static final String SOURCE = """
            package com.example;

            import java.io.IOException;

            class Reader {
                String read(String path) {
                    try {
                        return load(path);
                    } catch (IOException ex) {
                    }
                    return "";
                }

                private String load(String path) throws IOException {
                    return path;
                }
            }
            """;

    @Test
    void appliesAReplacementAndKeepsTheRestOfTheFile() {
        UnifiedDiff diff = UnifiedDiff.parse(replaceLine(PATH, SOURCE, 10,
                "            throw new IllegalStateException(\"unreadable: \" + path, ex);",
                "        }"));

        Optional<String> patched = diff.applyTo(SOURCE);

        assertThat(patched).isPresent();
        assertThat(patched.get()).contains("throw new IllegalStateException(\"unreadable: \" + path, ex);");
        assertThat(patched.get()).startsWith("package com.example;");
        assertThat(patched.get()).contains("        return \"\";");
        assertThat(diff.path()).contains(PATH);
    }

    @Test
    void refusesAPatchWhoseContextDoesNotMatchTheRevision() {
        String foreign = replaceLine(PATH, SOURCE, 10, "            log.warn(\"unreadable\", ex);")
                .replace("} catch (IOException ex) {", "} catch (OtherException ex) {");

        assertThat(UnifiedDiff.parse(foreign).applyTo(SOURCE)).isEmpty();
    }

    @Test
    void refusesAHunkThatStartsBeforeTheBeginningOfTheFile() {
        String beyond = """
                --- a/Reader.java
                +++ b/Reader.java
                @@ -0,0 +1,1 @@
                +package com.example;
                """;

        assertThat(UnifiedDiff.parse(beyond).applyTo(SOURCE)).isEmpty();
    }

    @Test
    void appliesEveryHunkOfAMultiHunkPatch() {
        String twoHunks = diff(PATH, SOURCE, Map.of(
                10, List.of("            throw new IllegalStateException(\"unreadable: \" + path, ex);", "        }")));

        Optional<String> patched = UnifiedDiff.parse(twoHunks).applyTo(SOURCE);

        assertThat(patched).isPresent();
        assertThat(patched.get()).contains("throw new IllegalStateException");
        assertThat(patched.get()).contains("    private String load(String path) throws IOException {");
    }

    @Test
    void rejectsAHunkWhoseBodyDoesNotMatchItsHeader() {
        String miscounted = """
                --- a/Reader.java
                +++ b/Reader.java
                @@ -4,7 +4,7 @@
                 class Reader {
                -    String read(String path) {
                +    String read(String path) {
                """;

        assertThatThrownBy(() -> UnifiedDiff.parse(miscounted))
                .isInstanceOf(InvalidDiffException.class)
                .hasMessageContaining("declares 7 old and 7 new lines");
    }

    @Test
    void rejectsAnUnreadableHunkHeaderAnEmptyDiffAndAStrayLine() {
        assertThatThrownBy(() -> UnifiedDiff.parse("--- a/Reader.java\n+++ b/Reader.java\n@@ nonsense @@\n"))
                .isInstanceOf(InvalidDiffException.class)
                .hasMessageContaining("unreadable hunk header");
        assertThatThrownBy(() -> UnifiedDiff.parse("   "))
                .isInstanceOf(InvalidDiffException.class)
                .hasMessageContaining("no diff");
        assertThatThrownBy(() -> UnifiedDiff.parse(replaceLine(PATH, SOURCE, 10, "        }") + "\nnonsense\n"))
                .isInstanceOf(InvalidDiffException.class)
                .hasMessageContaining("unexpected line in a hunk");
    }
}
