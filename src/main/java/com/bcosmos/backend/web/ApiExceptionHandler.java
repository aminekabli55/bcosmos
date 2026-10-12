package com.bcosmos.backend.web;

import com.bcosmos.backend.jobs.AnalysisRunNotFoundException;
import com.bcosmos.backend.repository.DuplicateRepositoryException;
import com.bcosmos.backend.repository.InvalidRepositoryPathException;
import com.bcosmos.backend.repository.RepositoryNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One error shape for the whole API (spec v2 section 8: "consistent errors with correlation IDs", DTOs only).
 *
 * <p>Messages never repeat the offending filesystem path: what the caller sent is echoed nowhere (spec sections 8
 * and 10). Unexpected failures return a correlation id that also appears in the log line, so a user report can be
 * traced without dumping internals into the response.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRepositoryPathException.class)
    public ProblemDetail handleInvalidPath(InvalidRepositoryPathException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid repository path", exception.getMessage());
    }

    @ExceptionHandler(DuplicateRepositoryException.class)
    public ProblemDetail handleDuplicate(DuplicateRepositoryException exception) {
        return problem(HttpStatus.CONFLICT, "Repository already registered", exception.getMessage());
    }

    @ExceptionHandler(RepositoryNotFoundException.class)
    public ProblemDetail handleRepositoryNotFound(RepositoryNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Repository not found", exception.getMessage());
    }

    @ExceptionHandler(AnalysisRunNotFoundException.class)
    public ProblemDetail handleRunNotFound(AnalysisRunNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Analysis run not found", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "the request body did not pass validation");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        String correlationId = UUID.randomUUID().toString();
        log.error("unexpected API failure, correlationId={}", correlationId, exception);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "the request could not be completed; quote the correlation id when reporting this");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        return problem;
    }
}
