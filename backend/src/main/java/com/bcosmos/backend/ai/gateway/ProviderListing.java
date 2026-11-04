package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.ProviderProtocol;
import java.time.Instant;

/**
 * One provider row as the settings screen needs it.
 *
 * <p>No key material of any kind: {@code credentialSource} says where the credential would come from,
 * {@code credentialHint} is the last four characters of a stored one, and {@code credentialEnvironmentVariable} is
 * a variable <em>name</em>. The value never leaves the process (spec v2 sections 9 and 10).</p>
 *
 * @param credentialHint                 last four characters of a stored key, or {@code null}
 * @param credentialEnvironmentVariable  variable the row points at, or {@code null}
 * @param modelCount                     registry rows attached to this provider, which is why it cannot be deleted
 */
public record ProviderListing(
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
}
