package com.toolpick.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.toolpick.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CollectorService 통합 테스트: 같은 이름의 다른 레포(삭제 후 재생성)가 와도 수집·발견이 실패하지 않는지.
 * [CS] 회귀 테스트(regression test): 한 번 고친 버그가 나중에 다시 생기지 않게 그 상황을 테스트로 남긴다.
 * GitHubClient는 Mockito 가짜(IntegrationTest의 @MockitoBean)로 바꿔 원하는 응답을 준다. DB는 진짜 PostgreSQL이다.
 */
class CollectorServiceTest extends IntegrationTest {

    static final LocalDate DATE = LocalDate.of(2026, 10, 26);

    @Autowired
    CollectorService collectorService;

    @Test
    void recreatedRepoWithSameNameDoesNotBreakCollect() {
        // 옛 a/x(github_id 1)를 추적 중인데, 지우고 다시 만든 a/x(github_id 999)가 돌아오는 상황
        long oldId = insertRepo("a/x", "2026-10-20T00:00:00Z");
        insertSnapshot(oldId, "2026-10-25", 500);
        when(gitHubClient.fetchRepos(anyList())).thenReturn(List.of(dto(999, "a/x", 120)));

        collectorService.collectSnapshots(DATE);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT github_id, full_name FROM repo ORDER BY id");
        assertThat(rows).extracting(r -> r.get("full_name")).containsExactly("a/x#1", "a/x");
        // 옛 기록은 옛 행에 남고, 새 레포의 스냅샷은 새 행에 쌓인다
        assertThat(jdbc.queryForObject("SELECT stars FROM star_snapshot WHERE repo_id = ?", Integer.class, oldId))
                .isEqualTo(500);
        assertThat(jdbc.queryForObject("""
                SELECT s.stars FROM star_snapshot s JOIN repo r ON r.id = s.repo_id WHERE r.github_id = 999""",
                Integer.class)).isEqualTo(120);
    }

    @Test
    void recreatedRepoWithSameNameDoesNotBreakDiscover() {
        insertRepo("a/x", "2026-10-20T00:00:00Z");
        when(gitHubClient.searchCandidates(any())).thenReturn(List.of(dto(999, "a/x", 120)));

        assertThat(collectorService.discoverCandidates(DATE)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT full_name FROM repo ORDER BY id", String.class))
                .containsExactly("a/x#1", "a/x");
    }

    private static GitHubRepoDto dto(long githubId, String fullName, int stars) {
        return new GitHubRepoDto(githubId, fullName, "새 레포", "Go", "MIT", "https://github.com/" + fullName,
                Instant.parse("2026-10-25T00:00:00Z"), false, false, stars);
    }
}
