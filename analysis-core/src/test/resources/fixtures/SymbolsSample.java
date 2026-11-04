package com.acme.samples;

/** Fixture: every symbol kind the Java adapter reports, plus one nested type per kind. */
public class SymbolsSample implements Runnable {

    private static final String NAME = "symbols";

    private final int count;

    public SymbolsSample(int count) {
        this.count = count;
    }

    @Override
    public void run() {
        helper(NAME + count);
    }

    private void helper(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value");
        }
    }

    static final class Nested {

        void nested() {
            // deliberately empty: this class only exists to be found as a nested symbol
        }
    }

    enum Kind {
        FIRST,
        SECOND
    }

    record Point(int x, int y) {
    }

    @interface Marker {
    }
}

interface SampleContract {

    void run();
}
