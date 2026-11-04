package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.SymbolKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * A code symbol found by one run (spec v2 section 4.1, table {@code symbol}).
 *
 * <p>The stored vocabulary is {@link SymbolKind} from {@code analysis-core}: the adapter decides what a symbol is,
 * the backend only stores it.</p>
 */
@Entity
@Table(name = "symbol")
public class SymbolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private SymbolKind kind;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "qualified_name", nullable = false, length = 1000)
    private String qualifiedName;

    @Column(name = "parent_qualified_name", length = 1000)
    private String parentQualifiedName;

    @Column(name = "start_line", nullable = false)
    private int startLine;

    @Column(name = "start_column", nullable = false)
    private int startColumn;

    @Column(name = "end_line", nullable = false)
    private int endLine;

    @Column(name = "end_column", nullable = false)
    private int endColumn;

    @Column(name = "signature_hash", nullable = false, length = 64)
    private String signatureHash;

    /** For JPA only. */
    protected SymbolEntity() {
    }

    public SymbolEntity(Long runId, Long fileId, SymbolKind kind, String name, String qualifiedName,
            String parentQualifiedName, Span span, String signatureHash) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.qualifiedName = Objects.requireNonNull(qualifiedName, "qualifiedName");
        this.parentQualifiedName = parentQualifiedName;
        this.startLine = span.startLine();
        this.startColumn = span.startColumn();
        this.endLine = span.endLine();
        this.endColumn = span.endColumn();
        this.signatureHash = Objects.requireNonNull(signatureHash, "signatureHash");
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getFileId() {
        return fileId;
    }

    public SymbolKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getParentQualifiedName() {
        return parentQualifiedName;
    }

    public Span getSpan() {
        return new Span(startLine, startColumn, endLine, endColumn);
    }

    public String getSignatureHash() {
        return signatureHash;
    }
}
