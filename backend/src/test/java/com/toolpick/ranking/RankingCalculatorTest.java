package com.toolpick.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.toolpick.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 순위 SQL 검증: 카테고리별 10개 제한, 증가량 순서, 공개·푸시 30일 조건, 새 레포 기준, 멱등성.
 * <p>
 * [CS] 이 프로젝트에서 가장 중요한 로직이 SQL 한 문장 안에 있다. SQL은 컴파일러가 의미를 검사해 주지 않으므로,
 * 입력 데이터를 직접 만들고(given) → 실행하고(when) → 결과 표를 확인하는(then) 방식으로 검증한다.
 * 날짜를 고정(2026-10-26)해서 언제 돌려도 같은 결과가 나오게 한다(결정적 테스트).
 */
class RankingCalculatorTest extends IntegrationTest {

    static final LocalDate DATE = LocalDate.of(2026, 10, 26);
    static final String RECENT_PUSH = "2026-10-20T00:00:00Z";

    @Autowired
    RankingCalculator calculator;

    @Test
    void ranksByWeeklyGainPerCategoryLimitedToTen() {
        // dev-tools에 12개: i번째 레포는 7일 동안 i*10 스타 증가
        for (int i = 1; i <= 12; i++) {
            long id = insertRepo("dev/tool" + i, RECENT_PUSH);
            insertAnalysis(id, "dev-tools", "PUBLISHED", true);
            insertSnapshot(id, "2026-10-19", 1000);
            insertSnapshot(id, "2026-10-26", 1000 + i * 10);
        }
        long ai = insertRepo("ai/agent", RECENT_PUSH);
        insertAnalysis(ai, "ai-agent", "PUBLISHED", true);
        insertSnapshot(ai, "2026-10-19", 50);
        insertSnapshot(ai, "2026-10-26", 80);

        assertThat(calculator.calculate(DATE)).isEqualTo(11);

        List<Map<String, Object>> devTools = rankingOf("dev-tools");
        assertThat(devTools).hasSize(10);
        assertThat(devTools.get(0)).containsEntry("full_name", "dev/tool12").containsEntry("star_gain", 120);
        assertThat(devTools.get(9)).containsEntry("full_name", "dev/tool3").containsEntry("rank_no", 10);
        assertThat(rankingOf("ai-agent")).singleElement().satisfies(r -> assertThat(r).containsEntry("star_gain", 30));
    }

    @Test
    void excludesUnpublishedStaleAndNonGrowingRepos() {
        long published = repoWithGain("ok/tool", RECENT_PUSH, "PUBLISHED", true, 100);
        repoWithGain("pending/tool", RECENT_PUSH, "PENDING_REVIEW", true, 500);
        repoWithGain("rejected/tool", RECENT_PUSH, "REJECTED", false, 500);
        repoWithGain("stale/tool", "2026-09-01T00:00:00Z", "PUBLISHED", true, 500);
        repoWithGain("flat/tool", RECENT_PUSH, "PUBLISHED", true, 0);

        calculator.calculate(DATE);

        assertThat(jdbc.queryForList("SELECT repo_id FROM daily_ranking", Long.class)).containsExactly(published);
    }

    @Test
    void newRepoUsesOldestSnapshotAsBase() {
        long id = insertRepo("new/tool", RECENT_PUSH);
        insertAnalysis(id, "frontend", "PUBLISHED", true);
        insertSnapshot(id, "2026-10-24", 10);
        insertSnapshot(id, "2026-10-25", 40);
        insertSnapshot(id, "2026-10-26", 70);
        // 7일보다 오래된 기록은 기준이 아니다
        long old = insertRepo("old/tool", RECENT_PUSH);
        insertAnalysis(old, "frontend", "PUBLISHED", true);
        insertSnapshot(old, "2026-10-01", 0);
        insertSnapshot(old, "2026-10-20", 100);
        insertSnapshot(old, "2026-10-26", 105);

        calculator.calculate(DATE);

        assertThat(rankingOf("frontend")).extracting(r -> r.get("full_name"), r -> r.get("star_gain"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("new/tool", 60),
                        org.assertj.core.groups.Tuple.tuple("old/tool", 5));
    }

    @Test
    void recalculatingSameDayGivesSameResult() {
        repoWithGain("ok/tool", RECENT_PUSH, "PUBLISHED", true, 100);

        calculator.calculate(DATE);
        calculator.calculate(DATE);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_ranking", Integer.class)).isEqualTo(1);
    }

    private long repoWithGain(String fullName, String pushedAt, String status, boolean useful, int gain) {
        long id = insertRepo(fullName, pushedAt);
        insertAnalysis(id, "dev-tools", status, useful);
        insertSnapshot(id, "2026-10-19", 1000);
        insertSnapshot(id, "2026-10-26", 1000 + gain);
        return id;
    }

    private List<Map<String, Object>> rankingOf(String slug) {
        return jdbc.queryForList("""
                SELECT r.full_name, d.rank_no, d.star_gain FROM daily_ranking d
                JOIN repo r ON r.id = d.repo_id JOIN category c ON c.id = d.category_id
                WHERE c.slug = ? AND d.ranking_date = '2026-10-26' ORDER BY d.rank_no""", slug);
    }
}
