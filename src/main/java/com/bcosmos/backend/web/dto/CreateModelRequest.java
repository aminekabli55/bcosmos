package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.ModelDraft;
import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A new model registry row (spec v2 section 8, {@code POST /api/models}).
 *
 * <p>Only the provider id, the model name and — for an embedding model — the vector width are really required:
 * everything else has a sensible default, so adding a model is one line in the settings screen rather than a form
 * full of guesses.</p>
 *
 * @param providerId         the provider row that owns the endpoint and the credential
 * @param modelName          exactly as the provider spells it, for example {@code deepseek-chat}
 * @param enabled            whether the opt-in is granted now; defaults to off
 * @param embeddingDimension required for EMBEDDING, ignored for CHAT
 */
public record CreateModelRequest(
        @NotNull @Positive Long providerId,
        @NotBlank @Size(max = 120) String modelName,
        LlmModelKind kind,
        LlmModelCapability capability,
        @Positive Integer embeddingDimension,
        Boolean enabled,
        Boolean defaultModel,
        @Positive Integer contextWindow,
        @PositiveOrZero BigDecimal costPer1kIn,
        @PositiveOrZero BigDecimal costPer1kOut,
        @Size(max = 500) String notes) {

    public ModelDraft toDraft() {
        return new ModelDraft(providerId, modelName, kind, capability, embeddingDimension, enabled, defaultModel,
                contextWindow, costPer1kIn, costPer1kOut, notes);
    }
}
