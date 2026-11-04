package com.bcosmos.backend.persistence;

/** Where a repository comes from (spec v2 section 4.1, {@code repository.source_type}). */
public enum RepositorySourceType {
    /** Registered by path on this machine; nothing is cloned and nothing is executed. */
    LOCAL,
    /** Cloned from an allowlisted public host by {@code miner} (phase 4). */
    MINED
}
