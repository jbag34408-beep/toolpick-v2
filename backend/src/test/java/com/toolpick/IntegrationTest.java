package com.toolpick;

import com.toolpick.analysis.AiAnalyzer;
import com.toolpick.collector.GitHubClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 통합 테스트 공통 부모. 스프링 컨텍스트를 하나만 띄우도록 테스트마다 설정을 바꾸지 않는다.
 * 외부 API(GitHub·Gemini)는 각 테스트에서 WireMock 또는 가짜 구현으로 대신한다.
 * <p>
 * [CS] 단위 테스트 vs 통합 테스트: 단위 테스트는 클래스 하나를 떼어 빠르게, 통합 테스트는 스프링·DB까지 실제로 엮어 검사한다.
 * 스프링 컨텍스트는 뜨는 데 수 초가 걸리지만, 설정이 같으면 테스트 클래스끼리 재사용(context caching)한다.
 * 그래서 모든 통합 테스트가 이 부모를 상속해 같은 설정(같은 @MockitoBean 목록)을 쓰게 한다. 하나라도 다르면 컨텍스트가 새로 뜬다.
 * <p>
 * [CS] 테스트 격리(isolation): 테스트마다 시작 전에 테이블을 비우고(TRUNCATE) 캐시도 비운다.
 * 앞 테스트가 남긴 데이터에 다음 테스트 결과가 좌우되면 실행 순서에 따라 통과·실패가 바뀐다.
 * <p>
 * [문법] @MockitoBean: 스프링 빈을 Mockito 가짜 객체로 바꿔 끼운다. 테스트가 끝날 때마다 정해 둔 응답은 초기화된다.
 * abstract 클래스라 직접 실행되지 않고 상속한 테스트 클래스에서만 쓰인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected GitHubClient gitHubClient;

    @MockitoBean
    protected AiAnalyzer aiAnalyzer;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE bookmark, app_user, daily_ranking, repo_analysis, star_snapshot, repo, batch_run"
                + " RESTART IDENTITY CASCADE");
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    protected long insertRepo(String fullName, String pushedAtSql) {
        return jdbc.queryForObject("""
                INSERT INTO repo (github_id, full_name, html_url, license, last_pushed_at)
                VALUES ((SELECT COALESCE(MAX(github_id), 0) + 1 FROM repo), ?, ?, 'MIT', CAST(? AS timestamptz))
                RETURNING id""", Long.class, fullName, "https://github.com/" + fullName, pushedAtSql);
    }

    protected void insertSnapshot(long repoId, String date, int stars) {
        jdbc.update("INSERT INTO star_snapshot (repo_id, snapshot_date, stars) VALUES (?, CAST(? AS date), ?)",
                repoId, date, stars);
    }

    protected long insertAnalysis(long repoId, String categorySlug, String status, boolean useful) {
        return jdbc.queryForObject("""
                INSERT INTO repo_analysis (repo_id, useful, category_id, summary_ko, install_cmd, confidence, status, raw)
                VALUES (?, ?, (SELECT id FROM category WHERE slug = ?), '요약', 'npm i x', 0.9, ?, '{}'::jsonb)
                RETURNING id""", Long.class, repoId, useful, categorySlug, status);
    }

    /** 북마크 한 건 (사용자도 함께 만든다). 수집 범위 테스트용 */
    protected void insertBookmark(long repoId) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO app_user (github_id, login, role)
                VALUES ((SELECT COALESCE(MAX(github_id), 0) + 1 FROM app_user), 'u', 'USER')
                RETURNING id""", Long.class);
        jdbc.update("INSERT INTO bookmark (user_id, repo_id) VALUES (?, ?)", userId, repoId);
    }
}
