package com.bcosmos.backend.web;

import com.bcosmos.backend.ai.explanation.FindingNotFoundException;
import com.bcosmos.backend.ai.gateway.InvalidModelUpdateException;
import com.bcosmos.backend.ai.gateway.LlmDisabledException;
import com.bcosmos.backend.ai.gateway.LlmProviderException;
import com.bcosmos.backend.ai.gateway.LlmTimeoutException;
import com.bcosmos.backend.ai.gateway.MissingApiKeyException;
import com.bcosmos.backend.ai.gateway.ModelCapabilityException;
import com.bcosmos.backend.ai.gateway.ModelDisabledException;
import com.bcosmos.backend.ai.gateway.ModelNotConfiguredException;
import com.bcosmos.backend.ai.gateway.ModelNotFoundException;
import com.bcosmos.backend.ai.gateway.ProviderNotConfiguredException;
import com.bcosmos.backend.ai.gateway.StructuredOutputException;
import com.bcosmos.backend.ai.rag.EmbeddingDimensionException;
import com.bcosmos.backend.ai.suggestion.SuggestionNotPossibleException;
import com.bcosmos.backend.ai.suggestion.SuggestionNotFoundException;
import com.bcosmos.backend.jobs.AnalysisRunNotFoundException;
import com.bcosmos.backend.repository.DuplicateRepositoryException;
import com.bcosmos.backend.repository.InvalidRepositoryPathException;
import com.bcosmos.backend.repository.RepositoryBusyException;
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

    /** The repository still has an analysis in flight, so removing it now would race that runner (ADR 0037). */
    @ExceptionHandler(RepositoryBusyException.class)
    public ProblemDetail handleRepositoryBusy(RepositoryBusyException exception) {
        return problem(HttpStatus.CONFLICT, "Repository is busy", exception.getMessage());
    }

    @ExceptionHandler(com.bcosmos.backend.repository.MinedRepositoryAnalysisNotSupportedException.class)
    public ProblemDetail handleMinedRepositoryAnalysis(com.bcosmos.backend.repository.MinedRepositoryAnalysisNotSupportedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Analysis not supported for mined repository", exception.getMessage());
    }

    @ExceptionHandler(AnalysisRunNotFoundException.class)
    public ProblemDetail handleRunNotFound(AnalysisRunNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Analysis run not found", exception.getMessage());
    }

    @ExceptionHandler(FindingNotFoundException.class)
    public ProblemDetail handleFindingNotFound(FindingNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Finding not found", exception.getMessage());
    }

    @ExceptionHandler(ModelNotFoundException.class)
    public ProblemDetail handleModelNotFound(ModelNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Model not found", exception.getMessage());
    }

    @ExceptionHandler(SuggestionNotFoundException.class)
    public ProblemDetail handleSuggestionNotFound(SuggestionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Suggestion not found", exception.getMessage());
    }

    /**
     * The run cannot carry a suggestion: the stored source is missing (an analysis from before Phase 3) or the file
     * never parsed. The message names the way out - re-run the analysis.
     */
    @ExceptionHandler(SuggestionNotPossibleException.class)
    public ProblemDetail handleSuggestionNotPossible(SuggestionNotPossibleException exception) {
        return problem(HttpStatus.CONFLICT, "Suggestion not possible", exception.getMessage());
    }

    /**
     * The configured vector width and the embedding model disagree. It is a configuration error, and saying so is
     * better than an empty retrieval result that looks like "nothing similar in this repository".
     */
    @ExceptionHandler(EmbeddingDimensionException.class)
    public ProblemDetail handleEmbeddingDimension(EmbeddingDimensionException exception) {
        return problem(HttpStatus.CONFLICT, "Embedding index not usable", exception.getMessage());
    }

    /** The AI layer is switched off: nothing was sent anywhere, and the request can be repeated once it is on. */
    @ExceptionHandler(LlmDisabledException.class)
    public ProblemDetail handleAiDisabled(LlmDisabledException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI is switched off", exception.getMessage());
    }

    /**
     * The request is fine but the system is not set up for it: the model is not enabled, its provider has no key, no
     * model is the default yet, or a provider has no endpoint. Each message names what to configure, never a value.
     */
    @ExceptionHandler({ModelDisabledException.class, MissingApiKeyException.class, ModelNotConfiguredException.class,
            ProviderNotConfiguredException.class})
    public ProblemDetail handleModelNotUsable(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Model not usable", exception.getMessage());
    }

    /** The request named a model that cannot do what it asked: an embedding model for chat, or the other way round. */
    @ExceptionHandler(ModelCapabilityException.class)
    public ProblemDetail handleModelCapability(ModelCapabilityException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Model cannot do this", exception.getMessage());
    }

    /** A registry edit that cannot be applied as asked (clearing the default, a negative price). */
    @ExceptionHandler(InvalidModelUpdateException.class)
    public ProblemDetail handleInvalidModelUpdate(InvalidModelUpdateException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid model update", exception.getMessage());
    }

    /** The provider failed, or the answer never matched the schema. */
    @ExceptionHandler({LlmProviderException.class, StructuredOutputException.class})
    public ProblemDetail handleProviderFailure(RuntimeException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Model call failed", exception.getMessage());
    }

    @ExceptionHandler(LlmTimeoutException.class)
    public ProblemDetail handleModelTimeout(LlmTimeoutException exception) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "Model call timed out", exception.getMessage());
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
