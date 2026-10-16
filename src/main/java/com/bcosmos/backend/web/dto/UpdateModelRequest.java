package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.ModelEdit;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An edit of one registry row (spec v2 section 8, {@code PUT /api/models/{id}}).
 *
 * <p>Every field is optional and a missing one means "leave it": the settings screen flips one switch at a time, and
 * a full-row update would let two open tabs overwrite each other.</p>
 *
 * @param enabled       per-model opt-in for sending code to the provider
 * @param contextWindow tokens the provider accepts per request
 * @param costPer1kIn   estimated price per 1000 input tokens
 * @param costPer1kOut  estimated price per 1000 output tokens
 * @param notes         free text shown in the settings screen
 */
public record UpdateModelRequest(
        Boolean enabled,
        @Positive Integer contextWindow,
        @PositiveOrZero BigDecimal costPer1kIn,
        @PositiveOrZero BigDecimal costPer1kOut,
        @Size(max = 500) String notes) {

    public ModelEdit toEdit() {
        return new ModelEdit(enabled, contextWindow, costPer1kIn, costPer1kOut, notes);
    }
}
