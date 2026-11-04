package com.bcosmos.backend.ai.gateway;


public interface LlmClient {

    /** Free text. */
    ChatResult chat(ChatRequest request);

    /** An answer that must validate against a JSON schema, retried once with the validation error. */
    <T> StructuredResult<T> structured(StructuredRequest<T> request);

    /** Embedding vectors, one per input text. */
    EmbedResult embed(EmbedRequest request);
}
