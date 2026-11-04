package com.acme.quality;

/** Fixture: one unused private field, so the PMD baseline must report exactly one UnusedPrivateField. */
public class UnusedFieldSample {

    private String unusedNote;

    public String describe() {
        return "describe";
    }
}
