package com.toolpick.ranking.dto;

public record RankingItemResponse(
        int rank,
        Long repoId,
        String fullName,
        String summaryKo,
        String installCmd,
        int starGain,
        String language,
        String htmlUrl) {
}
