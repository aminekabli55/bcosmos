package com.bcosmos.backend.jobs;

import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.Relationship;
import com.bcosmos.core.model.Symbol;
import java.util.List;

/**
 * What one adapter produced for one file.
 *
 * @param parseStatus    whether the file parsed
 * @param lineCount      lines in the source (0 when the file was never read)
 * @param symbols        symbols found (empty when the file did not parse)
 * @param relationships  structural and call relationships (empty when the file did not parse)
 * @param findings       normalized findings (empty when the file did not parse or the tool failed)
 * @param error          why the file failed, or {@code null}; stored on the file record so a failure is never silent
 */
public record FileAnalysis(
        ParseStatus parseStatus,
        int lineCount,
        List<Symbol> symbols,
        List<Relationship> relationships,
        List<Finding> findings,
        String error) {

    public FileAnalysis {
        symbols = List.copyOf(symbols);
        relationships = List.copyOf(relationships);
        findings = List.copyOf(findings);
    }

    public FileAnalysis(ParseStatus parseStatus, int lineCount, List<Symbol> symbols, List<Finding> findings, String error) {
        this(parseStatus, lineCount, symbols, List.of(), findings, error);
    }

    public static FileAnalysis parsed(int lineCount, List<Symbol> symbols, List<Finding> findings) {
        return new FileAnalysis(ParseStatus.OK, lineCount, symbols, List.of(), findings, null);
    }

    public static FileAnalysis parsed(int lineCount, List<Symbol> symbols, List<Relationship> relationships, List<Finding> findings) {
        return new FileAnalysis(ParseStatus.OK, lineCount, symbols, relationships, findings, null);
    }

    /** A file the parser rejected: its diagnostics are summarised into one message for the file record. */
    public static FileAnalysis notParsed(int lineCount, String error) {
        return new FileAnalysis(ParseStatus.FAILED, lineCount, List.of(), List.of(), List.of(), error);
    }

    /** A file the analyzer could not process (tool failure, unreadable file, size limit). */
    public static FileAnalysis failed(int lineCount, String error) {
        return new FileAnalysis(ParseStatus.FAILED, lineCount, List.of(), List.of(), List.of(), error);
    }

    public boolean parsed() {
        return parseStatus == ParseStatus.OK;
    }
}
