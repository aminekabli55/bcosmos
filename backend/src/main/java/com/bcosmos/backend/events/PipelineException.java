package com.bcosmos.backend.events;

/** Raised when an event cannot be published, including when the pipeline is switched off. */
public class PipelineException extends RuntimeException {

    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
