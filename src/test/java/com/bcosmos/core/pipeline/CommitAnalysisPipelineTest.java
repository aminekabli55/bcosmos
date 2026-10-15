package com.bcosmos.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.core.Fixtures;
import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.model.ParseStatus;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link CommitAnalysisPipeline} (ADR 0025).
 *
 * <p>These tests use the real {@link JavaAdapter} against known fixture files: no mock adapters, no mock
 * normalizer — the goal is to prove the pipeline wires everything correctly.</p>
 */
class CommitAnalysisPipelineTest {

    private final CommitAnalysisPipeline pipeline = new CommitAnalysisPipeline(new JavaAdapter());

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void parseable_file_produces_OK_status() throws IOException {
        String source = Fixtures.source("EmptyCatchSample.java");
        CommitAnalysisResult result = pipeline.analyseText(
                "fixtures/EmptyCatchSample.java", source, "abc123");
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(result.parseError()).isNull();
    }

    @Test
    void findings_are_normalized_and_deduplicated() throws IOException {
        String source = Fixtures.source("EmptyCatchSample.java");
        CommitAnalysisResult result = pipeline.analyseText(
                "fixtures/EmptyCatchSample.java", source, "abc123");
        // EmptyCatchSample.java has at least one empty catch block
        assertThat(result.findings()).isNotEmpty();
        // All fingerprints are distinct (normalizer deduplicates)
        long distinctFingerprints = result.findings().stream()
                .map(f -> f.fingerprint())
                .distinct()
                .count();
        assertThat(distinctFingerprints).isEqualTo(result.findings().size());
    }

    @Test
    void content_hash_is_returned_unchanged() throws IOException {
        String source = Fixtures.source("EmptyCatchSample.java");
        CommitAnalysisResult result = pipeline.analyseText(
                "fixtures/EmptyCatchSample.java", source, "expected-hash");
        assertThat(result.contentHash()).isEqualTo("expected-hash");
    }

    @Test
    void file_source_is_called_once() throws IOException {
        String[] callCount = {""};
        FileSource source = path -> {
            callCount[0] += "X";
            return Fixtures.source("EmptyCatchSample.java");
        };
        pipeline.analyse("fixtures/EmptyCatchSample.java", source);
        assertThat(callCount[0]).isEqualTo("X"); // called exactly once
    }

    @Test
    void analyzerVersion_and_rulesetHash_are_populated() throws IOException {
        String source = Fixtures.source("EmptyCatchSample.java");
        CommitAnalysisResult result = pipeline.analyseText(
                "fixtures/EmptyCatchSample.java", source, "hash");
        assertThat(result.analyzerVersion()).isNotBlank();
        assertThat(result.rulesetHash()).isNotBlank();
    }

    @Test
    void relative_path_is_preserved() throws IOException {
        String source = Fixtures.source("EmptyCatchSample.java");
        CommitAnalysisResult result = pipeline.analyseText(
                "src/main/java/Foo.java", source, "hash");
        assertThat(result.relativePath()).isEqualTo("src/main/java/Foo.java");
    }

    // ── parse failure ─────────────────────────────────────────────────────────

    @Test
    void syntax_error_file_produces_FAILED_status_without_throwing() throws IOException {
        String brokenSource = "public class Broken { void x( { }"; // missing ')'
        CommitAnalysisResult result = pipeline.analyseText(
                "fixtures/Broken.java", brokenSource, "hash");
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(result.parseError()).isNotBlank();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void empty_source_does_not_throw() throws IOException {
        CommitAnalysisResult result = pipeline.analyseText(
                "fixtures/Empty.java", "", "hash");
        // An empty file either fails to parse (OK) or produces no findings; the point is: no exception
        assertThat(result.relativePath()).isEqualTo("fixtures/Empty.java");
    }

    // ── path validation ───────────────────────────────────────────────────────

    @Test
    void absolute_path_is_rejected() {
        assertThatThrownBy(() -> pipeline.analyseText("/absolute/Foo.java", "source", "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── FileSource IOException propagates ─────────────────────────────────────

    @Test
    void source_IOException_propagates_to_caller() {
        FileSource broken = path -> { throw new IOException("disk error"); };
        assertThatThrownBy(() -> pipeline.analyse("fixtures/Foo.java", broken))
                .isInstanceOf(IOException.class)
                .hasMessage("disk error");
    }

    @Test
    void multi_adapter_handles_typescript_and_java() {
        CommitAnalysisPipeline multiPipeline = new CommitAnalysisPipeline(
                java.util.List.of(new JavaAdapter(), new com.bcosmos.core.typescript.TypeScriptAdapter()));

        CommitAnalysisResult javaResult = multiPipeline.analyseText(
                "src/Main.java", "public class Main { public static void main(String[] args) {} }", "hash1");
        assertThat(javaResult.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(javaResult.languageId()).isEqualTo("java");

        CommitAnalysisResult tsResult = multiPipeline.analyseText(
                "src/app.ts", "const x: any = 1; console.log(x);", "hash2");
        assertThat(tsResult.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(tsResult.languageId()).isEqualTo("typescript");
        assertThat(tsResult.findings()).isNotEmpty(); // flags no-explicit-any and no-console
    }
}
