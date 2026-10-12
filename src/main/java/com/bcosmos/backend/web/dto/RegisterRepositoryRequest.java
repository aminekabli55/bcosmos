package com.bcosmos.backend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/repositories} (spec v2 section 8, UC1).
 *
 * @param localPath absolute or relative path of the repository folder on this machine; it is validated against the
 *                  configured allowed roots and stored canonically, never executed
 * @param name      optional display name; defaults to the folder name
 */
public record RegisterRepositoryRequest(
        @NotBlank(message = "a repository path is required")
        @Size(max = 1000, message = "the repository path is too long")
        String localPath,

        @Size(max = 200, message = "the repository name is too long")
        String name) {
}
