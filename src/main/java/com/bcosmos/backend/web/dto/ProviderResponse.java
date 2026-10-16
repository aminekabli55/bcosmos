package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.CredentialSource;
import com.bcosmos.backend.ai.gateway.ProviderListing;
import com.bcosmos.backend.persistence.ProviderProtocol;
import java.time.Instant;

/**
 * One provider row (spec v2 section 8, {@code GET /api/providers}).
 *
 * <p>Adding a vendor is this resource: a code, the protocol that selects an adapter, the endpoint and a credential.
 * What comes back is never the credential: {@code credentialSource} says where one would come from,
 * {@code credentialHint} is the last four characters of a stored key, and {@code credentialEnvironmentVariable} is a
 * variable name (spec section 10).</p>
 *
 * @param credentialHint                last four characters of a stored key, or {@code null}
 * @param credentialEnvironmentVariable variable the row names, or {@code null}
 * @param modelCount                    registry rows attached to this provider; it cannot be deleted while one exists
 */
public record ProviderResponse(
        long id,
        String code,
        String label,
        ProviderProtocol protocol,
        String baseUrl,
        boolean apiKeyRequired,
        CredentialSource credentialSource,
        String credentialHint,
        String credentialEnvironmentVariable,
        long modelCount,
        Instant updatedAt) {

    public static ProviderResponse of(ProviderListing listing) {
        return new ProviderResponse(
                listing.id(),
                listing.code(),
                listing.label(),
                listing.protocol(),
                listing.baseUrl(),
                listing.apiKeyRequired(),
                listing.credentialSource(),
                listing.credentialHint(),
                listing.credentialEnvironmentVariable(),
                listing.modelCount(),
                listing.updatedAt());
    }
}
