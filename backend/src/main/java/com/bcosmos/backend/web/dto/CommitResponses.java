package com.bcosmos.backend.web.dto;

import java.time.Instant;
import java.util.List;

public final class CommitResponses {

    private CommitResponses() {
    }

    public record CommitItem(
            String hash,
            String shortHash,
            String message,
            String author,
            Instant committedAt,
            List<String> parentHashes,
            Long reviewId) {
    }

    public record CommitListResponse(
            Long repositoryId,
            List<CommitItem> commits) {
    }

    public record CreateCommitReviewRequest(
            String fromCommit,
            String toCommit,
            Long modelId) {
    }

    public record WhatToCheckItem(
            String file,
            int startLine,
            Integer endLine,
            String description) {
    }

    public record RiskSignal(
            String code,
            String title,
            String description,
            String severity,
            boolean isHeuristic) {
    }

    public record CommitFacts(
            int filesChangedCount,
            List<String> filesChanged,
            int newFindingsCount,
            int resolvedFindingsCount,
            int persistentFindingsCount,
            int uncertainFindingsCount,
            int affectedDependentsCount,
            int unresolvedEdgesCount) {
    }

    public record CommitReviewResponse(
            Long id,
            Long repositoryId,
            String fromCommit,
            String toCommit,
            CommitFacts facts,
            List<RiskSignal> riskSignals,
            String aiSummary,
            List<WhatToCheckItem> whatToCheck,
            List<String> testsToRun,
            String aiModelName,
            Instant createdAt) {
    }
}
