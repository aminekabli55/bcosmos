package com.acme.quality;

import java.util.ArrayList;
import java.util.List;

/** Fixture: exactly one empty catch block, so the PMD baseline must report exactly one EmptyCatchBlock. */
public class EmptyCatchSample {

    private final List<String> names = new ArrayList<>();

    public void loadQuietly() {
        try {
            names.add("first");
        } catch (IllegalStateException e) {
        }
    }

    public int size() {
        return names.size();
    }
}
