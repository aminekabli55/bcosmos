package com.bcosmos.backend.web.dto;

/**
 * The body of {@code PUT /api/settings/ai}.
 *
 * <p>{@code enabled} is nullable on purpose. A {@code PUT} replaces the stored representation, so a missing value
 * means "store no override" and the deployment property decides again — which is how the Settings screen offers
 * "follow the deployment default" without a second endpoint.</p>
 *
 * @param enabled {@code true} or {@code false} to store an override; {@code null} to clear it
 */
public record UpdateAiSettingsRequest(Boolean enabled) {
}
