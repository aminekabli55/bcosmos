package com.bcosmos.backend.ai.explanation;

/** No finding has this id, or its file row is gone. */
public class FindingNotFoundException extends RuntimeException {

    private final long findingId;

    public FindingNotFoundException(long findingId) {
        super("no finding with id " + findingId);
        this.findingId = findingId;
    }

    public long findingId() {
        return findingId;
    }
}
