package com.bcosmos.core.model;

/**
 * Kind of structural or semantic relationship between code symbols (Phase 8, spec v2 section 3.1 & 4.1).
 */
public enum RelationshipType {
    /** Method or constructor invocation. */
    CALLS,
    /** Class or interface inheritance (extends). */
    EXTENDS,
    /** Interface implementation (implements). */
    IMPLEMENTS,
    /** Type reference in field declaration, return type or method parameter. */
    USES_TYPE
}
