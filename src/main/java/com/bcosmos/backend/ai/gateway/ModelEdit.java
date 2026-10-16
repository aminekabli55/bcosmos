package com.bcosmos.backend.ai.gateway;

import java.math.BigDecimal;

/**
 * An edit sent to the registry by the settings screen (spec v2 section 8, {@code PUT /api/models/{id}}).
 *
 * <p>A {@code null} field means "leave it as it is", so a client can flip one switch without echoing the whole
 * row and racing another editor.</p>
 *
 * @param enabled       per-model opt-in for sending code to the provider
 * @param contextWindow tokens the provider accepts in one request
 * @param costPer1kIn   price estimate per 1000 input tokens
 * @param costPer1kOut  price estimate per 1000 output tokens
 * @param notes         free text for the UI
 */
public record ModelEdit(
        Boolean enabled,
        Integer contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut,
        String notes) {

    /** Whether this edit asks for nothing at all. */
    public boolean isEmpty() {
        return enabled == null && contextWindow == null && costPer1kIn == null
                && costPer1kOut == null && notes == null;
    }
}
