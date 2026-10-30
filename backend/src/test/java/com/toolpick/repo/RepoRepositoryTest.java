package com.toolpick.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.toolpick.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 재설계 01(판별 순서)·04(수집 범위) 쿼리 */
class RepoRepositoryTest extends IntegrationTest {

    static final Instant SINCE = Instant.parse("2026-08-31T00:00:00Z");
    static final String RECENT = "2026-10-20T00:00:00Z";
    static final String STALE = "2026-06-01T00:00:00Z";

    @Autowired
    RepoRepository repoRepository;

    @Test
    void unanalyzedOrderedByLatestStars() {
        long early = insertRepo("a/early-low", RECENT);       // 먼저 발견(id 작음), 스타 적음
        insertSnapshot(early, "2026-10-29", 50);
        insertSnapshot(early, "2026-10-30", 120);
        long late = insertRepo("a/late-high", RECENT);        // 나중에 발견, 스타 많음
        insertSnapshot(late, "2026-10-30", 900);
        insertRepo("a/no-snapshot", RECENT);                  // 스냅샷 없음 → 맨 뒤
        long released = insertRepo("a/old#7", RECENT);        // 이름을 비켜 둔 옛 행 → 제외
        insertSnapshot(released, "2026-10-30", 5000);
        long analyzed = insertRepo("a/analyzed", RECENT);     // 이미 판별됨 → 제외
        insertAnalysis(analyzed, "dev-tools", "PUBLISHED", true);
        long stale = insertRepo("a/stale", STALE);            // 60일 넘게 푸시 없음 → 제외
        insertSnapshot(stale, "2026-10-30", 9999);

        assertThat(repoRepository.findUnanalyzedByStars(SINCE, 10)).extracting(Repo::getFullName)
                .containsExactly("a/late-high", "a/early-low", "a/no-snapshot");
        assertThat(repoRepository.findUnanalyzedByStars(SINCE, 1)).extracting(Repo::getFullName)
                .containsExactly("a/late-high");
    }

    @Test
    void trackableScope() {
        long publishedStale = insertRepo("a/published-stale", STALE);
        insertAnalysis(publishedStale, "dev-tools", "PUBLISHED", true);
        long bookmarkedRejected = insertRepo("a/bookmarked-rejected", STALE);
        insertAnalysis(bookmarkedRejected, null, "REJECTED", false);
        insertBookmark(bookmarkedRejected);
        long rejectedRecent = insertRepo("a/rejected-recent", RECENT);
        insertAnalysis(rejectedRecent, null, "REJECTED", false);
        long pendingRecent = insertRepo("a/pending-recent", RECENT);
        insertAnalysis(pendingRecent, "dev-tools", "PENDING_REVIEW", true);
        insertRepo("a/unanalyzed-recent", RECENT);
        insertRepo("a/unanalyzed-stale", STALE);
        insertRepo("a/old#9", RECENT);
        jdbc.update("INSERT INTO repo (github_id, full_name, html_url) VALUES (999, 'a/never-pushed', 'https://x')");

        assertThat(repoRepository.findTrackableFullNames(SINCE)).containsExactlyInAnyOrder(
                "a/published-stale", "a/bookmarked-rejected", "a/pending-recent", "a/unanalyzed-recent", "a/never-pushed");
        assertThat(repoRepository.countActive()).isEqualTo(7); // `#` 행 제외
    }
}
