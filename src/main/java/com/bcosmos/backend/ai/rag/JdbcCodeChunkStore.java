package com.bcosmos.backend.ai.rag;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CodeChunkStore} on Postgres, using pgvector's cosine distance (spec v2 section 7.2).
 *
 * <p>Two details are deliberate:</p>
 * <ul>
 *   <li>The vector travels as a text literal cast with {@code cast(? as vector)}. The JDBC driver is not asked to
 *       understand a pgvector type, and the cast is written long-form because a {@code ?::vector} placeholder is
 *       ambiguous to some SQL parsers.</li>
 *   <li>Ordering is by {@code embedding <=> query} ascending, which is cosine distance: nearest first, the same
 *       operator the HNSW index is built for, so the index is usable rather than decorative.</li>
 * </ul>
 */
@Repository
public class JdbcCodeChunkStore implements CodeChunkStore {

    private static final String COLUMNS = """
            run_id, symbol_id, file_id, relative_path, qualified_name, start_line, end_line, text, token_count,
            embedding, embed_model, created_at
            """;

    private static final String INSERT = "insert into code_chunk (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " cast(? as vector), ?, ?) on conflict (run_id, symbol_id, embed_model) do update set"
            + " file_id = excluded.file_id, relative_path = excluded.relative_path,"
            + " qualified_name = excluded.qualified_name, start_line = excluded.start_line,"
            + " end_line = excluded.end_line, text = excluded.text, token_count = excluded.token_count,"
            + " embedding = excluded.embedding, created_at = excluded.created_at";

    private static final String NEAREST = """
            select symbol_id, file_id, relative_path, qualified_name, start_line, end_line, text, token_count,
                   embed_model, embedding <=> cast(? as vector) as distance
            from code_chunk
            where run_id = ? and embed_model = ?
            order by distance
            limit ?
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcCodeChunkStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int replaceAll(long runId, String embedModel, List<EmbeddedChunk> chunks) {
        jdbc.update("delete from code_chunk where run_id = ? and embed_model = ?", runId, embedModel);
        if (chunks.isEmpty()) {
            return 0;
        }
        Timestamp now = Timestamp.from(clock.instant());
        List<Object[]> rows = new ArrayList<>(chunks.size());
        for (EmbeddedChunk chunk : chunks) {
            CodeChunkDraft draft = chunk.draft();
            rows.add(new Object[] {
                    runId,
                    draft.symbolId(),
                    draft.fileId(),
                    draft.relativePath(),
                    draft.qualifiedName(),
                    draft.startLine(),
                    draft.endLine(),
                    draft.text(),
                    draft.tokenCount(),
                    VectorLiteral.of(chunk.embedding()),
                    embedModel,
                    now});
        }
        jdbc.batchUpdate(INSERT, rows);
        return rows.size();
    }

    @Override
    public long count(long runId, String embedModel) {
        Long count = jdbc.queryForObject(
                "select count(*) from code_chunk where run_id = ? and embed_model = ?", Long.class, runId, embedModel);
        return count == null ? 0L : count;
    }

    @Override
    public List<StoredChunk> nearest(long runId, String embedModel, float[] query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbc.query(NEAREST, (rs, rowNum) -> new StoredChunk(
                rs.getLong("symbol_id"),
                rs.getLong("file_id"),
                rs.getString("relative_path"),
                rs.getString("qualified_name"),
                rs.getInt("start_line"),
                rs.getInt("end_line"),
                rs.getString("text"),
                rs.getInt("token_count"),
                rs.getString("embed_model"),
                rs.getDouble("distance")),
                VectorLiteral.of(query), runId, embedModel, limit);
    }
}
