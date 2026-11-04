package com.bcosmos.backend.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.AnalysisStage;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import com.bcosmos.backend.persistence.SymbolEntity;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.SymbolKind;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The vector store is the one part of Phase 3 that cannot be tested with a fake: the pgvector column, the HNSW
 * index and the {@code <=>} ordering are the feature (spec v2 section 7.2, section 11's Testcontainers row).
 *
 * <p>The vectors are basis vectors on purpose, so the expected order is arithmetic rather than a tolerance
 * argument: a chunk with the same direction as the query has cosine distance 0, an orthogonal one has 1.</p>
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JdbcCodeChunkStoreTest {

    private static final int DIMENSION = 1536;
    private static final String MODEL = "GEMINI/gemini-embedding-2";
    private static final String OTHER_MODEL = "OPENAI_COMPATIBLE/text-embedding-3-small";

    @Autowired
    private JdbcCodeChunkStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RepositoryJpaRepository repositories;

    @Autowired
    private AnalysisRunJpaRepository runs;

    @Autowired
    private FileRecordJpaRepository files;

    @Autowired
    private SymbolJpaRepository symbols;

    private long runId;
    private long fileId;
    private long symbolId;

    @BeforeEach
    void seedARunWithOneFileAndOneSymbol() {
        jdbc.update("delete from code_chunk");
        jdbc.update("delete from suggestion");
        jdbc.update("delete from finding");
        jdbc.update("delete from symbol");
        jdbc.update("delete from file_content");
        jdbc.update("delete from file_record");
        jdbc.update("delete from analysis_run");
        jdbc.update("delete from repository");

        var repository = repositories.save(new RepositoryEntity("fixture", "/repos/fixture",
                RepositorySourceType.LOCAL, "java", RepositoryStatus.ANALYSED, Instant.now()));
        var run = runs.save(new AnalysisRunEntity(repository.getId(), AnalysisRunStatus.SUCCEEDED,
                AnalysisStage.COMPLETED, 1, Instant.now()));
        runId = run.getId();
        var file = files.save(new FileRecordEntity(runId, "src/main/java/com/example/Reader.java", "java", "hash", 20,
                ParseStatus.OK, null));
        fileId = file.getId();
        symbolId = symbols.save(new SymbolEntity(runId, fileId, SymbolKind.METHOD, "read", "com.example.Reader.read",
                "com.example.Reader", new Span(5, 1, 12, 1), "signature-hash")).getId();
    }

    @Test
    void storesChunksAndReplacesThemWhenTheSameRunIsIndexedAgain() {
        assertThat(store.replaceAll(runId, MODEL, List.of(
                chunk(symbolId, "first version", basis(0)))))
                .isEqualTo(1);
        assertThat(store.count(runId, MODEL)).isEqualTo(1);

        assertThat(store.replaceAll(runId, MODEL, List.of(chunk(symbolId, "second version", basis(0))))).isEqualTo(1);

        assertThat(store.count(runId, MODEL)).isEqualTo(1);
        assertThat(store.nearest(runId, MODEL, basis(0), 5).getFirst().text()).contains("second version");
    }

    @Test
    void ordersNeighboursByCosineDistanceAndHonoursTheLimit() {
        long nearest = symbols.save(symbolFor("nearest")).getId();
        long second = symbols.save(symbolFor("second")).getId();
        store.replaceAll(runId, MODEL, List.of(
                chunk(nearest, "identical direction", basis(0)),
                chunk(second, "orthogonal direction", basis(1)),
                chunk(symbolId, "orthogonal direction too", basis(2))));

        List<CodeChunkStore.StoredChunk> found = store.nearest(runId, MODEL, basis(0), 2);

        assertThat(found).hasSize(2);
        assertThat(found.getFirst().symbolId()).isEqualTo(nearest);
        assertThat(found.getFirst().distance()).isLessThan(0.0001);
        assertThat(found.getFirst().similarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(found.get(1).symbolId()).isNotEqualTo(nearest);
    }

    @Test
    void keepsTheVectorsOfTwoEmbeddingModelsApart() {
        store.replaceAll(runId, MODEL, List.of(chunk(symbolId, "gemini vector", basis(0))));
        store.replaceAll(runId, OTHER_MODEL, List.of(chunk(symbolId, "openai vector", basis(1))));

        assertThat(store.count(runId, MODEL)).isEqualTo(1);
        assertThat(store.count(runId, OTHER_MODEL)).isEqualTo(1);
        assertThat(store.nearest(runId, MODEL, basis(0), 5).getFirst().text()).isEqualTo("gemini vector");
        assertThat(store.nearest(runId, OTHER_MODEL, basis(1), 5).getFirst().text()).isEqualTo("openai vector");
    }

    @Test
    void searchesOnlyTheGivenRun() {
        store.replaceAll(runId, MODEL, List.of(chunk(symbolId, "this run", basis(0))));

        assertThat(store.nearest(runId + 1_000, MODEL, basis(0), 5)).isEmpty();
    }

    @Test
    void theMigrationCreatesAVectorColumnOfTheConfiguredWidthWithAnHnswIndex() {
        String columnType = jdbc.queryForObject("""
                select format_type(atttypid, atttypmod) from pg_attribute
                where attrelid = 'code_chunk'::regclass and attname = 'embedding'
                """, String.class);
        String indexDefinition = jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'idx_code_chunk_embedding_hnsw'", String.class);

        assertThat(columnType).isEqualTo("vector(" + DIMENSION + ")");
        assertThat(indexDefinition).contains("USING hnsw").contains("vector_cosine_ops");
    }

    @Test
    void postgresRefusesAVectorOfTheWrongWidth() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into code_chunk (run_id, file_id, symbol_id, relative_path, qualified_name, start_line,"
                        + " end_line, text, token_count, embedding, embed_model, created_at)"
                        + " values (?, ?, ?, 'a/File.java', 'File', 1, 1, 'text', 1, cast(? as vector), ?, now())",
                runId, fileId, symbolId, "(1,2,3)", MODEL))
                .hasMessageContaining("vector");
    }

    /** A vector pointing along one axis: cosine distance to it is arithmetic, not a tolerance argument. */
    private static float[] basis(int index) {
        float[] vector = new float[DIMENSION];
        vector[index] = 1.0f;
        return vector;
    }

    private SymbolEntity symbolFor(String name) {
        return new SymbolEntity(runId, fileId, SymbolKind.METHOD, name, "com.example.Reader." + name,
                "com.example.Reader", new Span(14, 1, 18, 1), "hash-" + name);
    }

    private CodeChunkStore.EmbeddedChunk chunk(long chunkSymbolId, String text, float[] embedding) {
        CodeChunkDraft draft = new CodeChunkDraft(chunkSymbolId, fileId, "src/main/java/com/example/Reader.java",
                "com.example.Reader.read", 5, 12, text, TokenEstimator.estimate(text), false);
        return new CodeChunkStore.EmbeddedChunk(runId, draft, MODEL, embedding);
    }
}
