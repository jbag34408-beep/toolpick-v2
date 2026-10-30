package com.toolpick.repo.dto;

import java.time.Instant;

/** frontend/lib/api.ts의 RepoDetail과 같은 모양 */
public record RepoDetailResponse(
        Long id,
        String fullName,
        String description,
        String language,
        String license,
        String htmlUrl,
        Integer stars,
        String categorySlug,
        String categoryNameKo,
        String summaryKo,
        String installCmd,
        Instant lastPushedAt) {
}
