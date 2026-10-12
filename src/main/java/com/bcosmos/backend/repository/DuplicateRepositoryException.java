package com.bcosmos.backend.repository;

/** The same canonical path is already registered. Message stays path-free (spec v2 sections 8 and 10). */
public class DuplicateRepositoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateRepositoryException(String message) {
        super(message);
    }
}
