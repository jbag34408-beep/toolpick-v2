package com.toolpick.user;

import java.time.Instant;

/** 북마크 목록 한 줄. Bookmark + Repo + RepoAnalysis(요약)를 합친 화면용 DTO */
public record BookmarkResponse(Long repoId, String fullName, String summaryKo, String language, String htmlUrl,
                               Instant createdAt) {
}
