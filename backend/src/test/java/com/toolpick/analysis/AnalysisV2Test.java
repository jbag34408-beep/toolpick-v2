package com.toolpick.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toolpick.IntegrationTest;
import com.toolpick.TestProps;
import com.toolpick.category.CategoryRepository;
import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.repo.RepoRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** v2에서 바뀐 판별 동작: 규칙 필터(03), 판별 끄기(05), 마감 시각, 로그 자르기 */
class AnalysisV2Test extends IntegrationTest {

    static final LocalDate TODAY = LocalDate.parse("2026-10-30");
    static final String RECENT = "2026-10-20T00:00:00Z";
    static final AnalysisResult USEFUL = new AnalysisResult(true, "dev-tools", "요약", null, "근거", 0.95, "{}");

    @Autowired
    AnalysisService analysisService;
    @Autowired
    RepoRepository repoRepository;
    @Autowired
    RepoAnalysisRepository analysisRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    RuleFilter ruleFilter;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void ruleRejectedWithoutAiCall() {
        insertRepo("a/awesome-go", RECENT);
        insertRepo("a/concourse", RECENT);
        when(aiAnalyzer.analyze(anyString(), any(), any())).thenReturn(USEFUL);

        assertThat(analysisService.analyzePending(TODAY, Instant.MAX)).isEqualTo(1); // AI로 판별한 것만 센다

        verify(aiAnalyzer, never()).analyze(eq("a/awesome-go"), any(), any());
        verify(gitHubClient, never()).fetchReadme("a/awesome-go"); // README도 안 읽는다
        assertThat(jdbc.queryForMap("""
                SELECT a.status, a.useful, a.confidence, a.raw->>'source' AS source, a.raw->>'rule' AS rule
                FROM repo_analysis a JOIN repo r ON r.id = a.repo_id WHERE r.full_name = 'a/awesome-go'"""))
                .containsEntry("status", "REJECTED")
                .containsEntry("useful", false)
                .containsEntry("source", "rule")
                .containsEntry("rule", "awesome")
                .hasEntrySatisfying("confidence", c -> assertThat(c.toString()).isEqualTo("1.00"));
    }

    @Test
    void ruleRejectedDoesNotUseDailyLimit() {
        insertRepo("a/awesome-list", RECENT);
        insertRepo("a/dotfiles", RECENT);
        insertRepo("a/real-tool", RECENT);
        when(aiAnalyzer.analyze(anyString(), any(), any())).thenReturn(USEFUL);

        // 하루 1개 한도: 규칙으로 걸린 두 개가 칸을 차지하지 않아 real-tool이 판별된다
        assertThat(serviceWithMaxPerDay(1).analyzePending(TODAY, Instant.MAX)).isEqualTo(1);
        verify(aiAnalyzer).analyze(eq("a/real-tool"), any(), any());
    }

    @Test
    void zeroMaxPerDaySkipsWithoutError() {
        insertRepo("a/awesome-list", RECENT);
        insertRepo("a/tool", RECENT);

        // v1은 PageRequest.of(0, 0)이 예외를 던져 배치가 실패했다
        assertThat(serviceWithMaxPerDay(0).analyzePending(TODAY, Instant.MAX)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repo_analysis", Integer.class)).isZero();
    }

    @Test
    void stopsAtDeadline() {
        insertRepo("a/tool", RECENT);

        assertThat(analysisService.analyzePending(TODAY, Instant.now().minusSeconds(1))).isZero();
        verify(aiAnalyzer, never()).analyze(anyString(), any(), any());
    }

    @Test
    void errorBodyIsTruncatedForLogs() {
        String body = "x".repeat(500);
        assertThat(AnalysisService.head(body)).hasSize(AnalysisService.LOG_BODY_MAX + 1).endsWith("…");
        assertThat(AnalysisService.head("short")).isEqualTo("short");
        assertThat(AnalysisService.head(null)).isEmpty();
    }

    private AnalysisService serviceWithMaxPerDay(int maxPerDay) {
        ToolPickProperties base = TestProps.withBaseUrl("http://unused");
        ToolPickProperties props = new ToolPickProperties(base.frontendOrigin(), base.adminLogins(), base.retryBaseDelay(),
                base.github(), base.gemini(), base.groq(), base.job(), base.candidate(),
                new ToolPickProperties.Analysis(0.8, maxPerDay, Duration.ZERO, List.of("awesome", "dotfiles")));
        return new AnalysisService(repoRepository, analysisRepository, categoryRepository, gitHubClient, aiAnalyzer,
                new RuleFilter(props), objectMapper, props, txManager);
    }
}
