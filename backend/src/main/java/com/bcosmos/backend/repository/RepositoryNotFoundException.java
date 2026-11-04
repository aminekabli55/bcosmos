package com.bcosmos.backend.repository;

/** No registered repository with that id. */
public class RepositoryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RepositoryNotFoundException(long repositoryId) {
        super("no repository with id " + repositoryId);
    }
}
