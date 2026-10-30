package com.toolpick.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * 분야별 7일 스타 증가량 Top 10을 계산한다 (v1에서 검증된 SQL 그대로).
 * [CS] 멱등성: 그날 순위를 지우고 다시 넣는 것을 한 트랜잭션에서 한다. v2는 하루에 RANK·RERANK 두 번 부르는데,
 * 두 번째는 그 사이 공개된 레포를 반영할 뿐 결과 모양은 같다.
 * 기준점은 "최근 7일 안에서 가장 오래된 스냅샷"이라 수집이 하루 빠져도 계산된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCalculator {

    static final String DELETE_SQL = "DELETE FROM daily_ranking WHERE ranking_date = CAST(:date AS date)";

    static final String INSERT_SQL = """
            INSERT INTO daily_ranking (ranking_date, category_id, rank_no, repo_id, star_gain)
            SELECT CAST(:date AS date), category_id, rank_no, repo_id, gain
            FROM (
                SELECT g.repo_id, g.category_id, g.gain,
                       ROW_NUMBER() OVER (PARTITION BY g.category_id ORDER BY g.gain DESC, g.repo_id) AS rank_no
                FROM (
                    SELECT t.repo_id, a.category_id, t.stars - base.stars AS gain
                    FROM star_snapshot t
                    JOIN LATERAL (
                        SELECT b.stars
                        FROM star_snapshot b
                        WHERE b.repo_id = t.repo_id
                          AND b.snapshot_date >= CAST(:date AS date) - 7
                        ORDER BY b.snapshot_date
                        LIMIT 1
                    ) base ON TRUE
                    JOIN repo_analysis a ON a.repo_id = t.repo_id
                    JOIN repo r          ON r.id = t.repo_id
                    WHERE t.snapshot_date = CAST(:date AS date)
                      AND a.useful = TRUE
                      AND a.status = 'PUBLISHED'
                      AND r.last_pushed_at >= CAST(:date AS date) - INTERVAL '30 days'
                ) g
                WHERE g.gain > 0
            ) ranked
            WHERE rank_no <= 10
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public int calculate(LocalDate date) {
        Map<String, Object> params = Map.of("date", date);
        jdbc.update(DELETE_SQL, params);
        int inserted = jdbc.update(INSERT_SQL, params);
        log.info("순위 계산: {} → {}행", date, inserted);
        return inserted;
    }
}
