package com.bcosmos.backend.ai.gateway;

/** A versioned prompt file is missing, unreadable, or cannot be filled in as asked. */
public class PromptTemplateException extends LlmException {

    public PromptTemplateException(String message, Throwable cause) {
        super("prompt-template", message, false, cause);
    }

    public PromptTemplateException(String message) {
        super("prompt-template", message, false);
    }
}
