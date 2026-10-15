package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.persistence.SuggestionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/suggestions/{id}/review} (spec v2 section 8: "Accept / reject / defer").
 *
 * <p>{@code PROPOSED} is a legal value on purpose: it puts a suggestion back to undecided. Everything else is
 * rejected by the enum before any code runs.</p>
 *
 * @param status the reviewer's decision
 */
public record ReviewSuggestionRequest(@NotNull SuggestionStatus status) {
}
