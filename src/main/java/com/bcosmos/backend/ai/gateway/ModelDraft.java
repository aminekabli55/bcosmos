package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import java.math.BigDecimal;

/**
 * A new registry row, as the settings screen sends it (spec v2 section 8, {@code POST /api/models}).
 *
 * <p>The provider is named by id: the endpoint and the credential belong to the provider row, so a model here is
 * only a name, a capability and the prices used for cost accounting.</p>
 *
 * @param kind               {@code null} means HOSTED
 * @param capability         {@code null} means CHAT
 * @param embeddingDimension required for an EMBEDDING model, ignored for a chat model
 * @param enabled            whether the opt-in is granted at creation; a pasted-but-unused model starts off
 * @param contextWindow      {@code null} means the default, so a user does not have to know it to add a model
 */
public record ModelDraft(
        long providerId,
        String modelName,
        LlmModelKind kind,
        LlmModelCapability capability,
        Integer embeddingDimension,
        Boolean enabled,
        Integer contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut,
        String notes) {
}
