package com.toolpick.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toolpick.IntegrationTest;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * AnalysisService 상태 결정 표(9장) 전체 경우와 실패 처리(429 중단, 451 격리, 403 중단)를 검증한다.
 * <p>
 * [CS] 경계값·분기 테스트: if 분기마다 최소 한 경우씩 넣는다(공개, 거절, 확신도 낮음, 없는 카테고리, 파싱 실패...).
 * 가짜 AiAnalyzer가 정해진 결과를 돌려주므로 AI의 변덕과 상관없이 "규칙"만 정확히 검사할 수 있다.
 * [문법] eq("a/x"), any(): Mockito 인자 매처. "첫 인자가 a/x면 이 결과를 돌려줘"처럼 조건별 응답을 정한다.
 */
class AnalysisServiceTest extends IntegrationTest {

    @Autowired
    AnalysisService analysisService;

    @Test
    void decidesStatusForEveryCase() {
        insertRepo("a/published", "2026-10-20T00:00:00Z");
        insertRepo("a/rejected", "2026-10-20T00:00:00Z");
        insertRepo("a/low-confidence", "2026-10-20T00:00:00Z");
        insertRepo("a/bad-slug", "2026-10-20T00:00:00Z");
        insertRepo("a/parse-failed", "2026-10-20T00:00:00Z");
        insertRepo("a/useless-unsure", "2026-10-20T00:00:00Z");
        stub("a/published", result(true, "dev-tools", 0.9));
        stub("a/rejected", result(false, null, 0.95));
        stub("a/low-confidence", result(true, "dev-tools", 0.5));
        stub("a/bad-slug", result(true, "games", 0.99));
        stub("a/parse-failed", AnalysisResult.parseFailed("{\"error\": \"x\", \"text\": \"y\"}"));
        stub("a/useless-unsure", result(false, null, 0.3));

        int done = analyze();

        assertThat(done).isEqualTo(6);
        assertThat(statusOf("a/published")).isEqualTo("PUBLISHED");
        assertThat(statusOf("a/rejected")).isEqualTo("REJECTED");
        assertThat(statusOf("a/low-confidence")).isEqualTo("PENDING_REVIEW");
        assertThat(statusOf("a/bad-slug")).isEqualTo("PENDING_REVIEW");
        assertThat(statusOf("a/parse-failed")).isEqualTo("PENDING_REVIEW");
        assertThat(statusOf("a/useless-unsure")).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT c.slug FROM repo_analysis a JOIN repo r ON r.id = a.repo_id JOIN category c ON c.id = a.category_id
                WHERE r.full_name = 'a/published'""", String.class)).isEqualTo("dev-tools");
    }

    @Test
    void stopsOnQuotaAndKeepsRestForTomorrow() {
        insertRepo("a/first", "2026-10-20T00:00:00Z");
        insertRepo("a/second", "2026-10-20T00:00:00Z");
        insertRepo("a/third", "2026-10-20T00:00:00Z");
        stub("a/first", result(true, "dev-tools", 0.9));
        when(aiAnalyzer.analyze(eq("a/second"), any(), any())).thenThrow(new AiQuotaExceededException("429", null));

        int done = analyze();

        assertThat(done).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repo_analysis", Integer.class)).isEqualTo(1);
        verify(aiAnalyzer, never()).analyze(eq("a/third"), any(), any());
    }

    @Test
    void blockedRepoIsQuarantinedAndDoesNotBlockOthers() {
        // 맨 앞 레포의 README가 저작권 차단(451)되어도 뒤의 레포는 판별되어야 한다
        insertRepo("a/blocked", "2026-10-20T00:00:00Z");
        insertRepo("a/next", "2026-10-20T00:00:00Z");
        when(gitHubClient.fetchReadme("a/blocked"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS, "blocked", null, null, null));
        stub("a/next", result(true, "dev-tools", 0.9));

        assertThat(analyze()).isEqualTo(2);
        assertThat(statusOf("a/blocked")).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT a.raw ->> 'error' FROM repo_analysis a JOIN repo r ON r.id = a.repo_id
                WHERE r.full_name = 'a/blocked'""", String.class)).isEqualTo("HTTP 451");
        assertThat(statusOf("a/next")).isEqualTo("PUBLISHED");
    }

    @Test
    void authErrorStopsInsteadOfMarkingEveryRepo() {
        // API 키 오류(403)는 모든 레포에 똑같이 나므로 레포마다 오류 결과를 쌓지 말고 멈춰야 한다
        insertRepo("a/first", "2026-10-20T00:00:00Z");
        insertRepo("a/second", "2026-10-20T00:00:00Z");
        when(aiAnalyzer.analyze(any(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "bad key", null, null, null));

        assertThat(analyze()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repo_analysis", Integer.class)).isZero();
    }

    @Test
    void invalidGeminiKey400Stops() {
        // Gemini는 잘못된 키를 400으로 알린다. 일반 400(레포 하나 문제)처럼 격리하면 모든 레포가 오류로 저장된다
        insertRepo("a/first", "2026-10-20T00:00:00Z");
        insertRepo("a/second", "2026-10-20T00:00:00Z");
        byte[] body = "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"details\": [{\"reason\": \"API_KEY_INVALID\"}]}}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(aiAnalyzer.analyze(any(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad key", null, body, null));

        assertThat(analyze()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repo_analysis", Integer.class)).isZero();
    }

    @Test
    void repeatedErrorsStopInsteadOfMarkingEveryRepo() {
        // 연속 오류 중단 부분: 모든 레포가 400이면 2개만 실패로 남기고 3번째에서 멈춘다
        for (String name : List.of("a/e1", "a/e2", "a/e3", "a/last")) {
            insertRepo(name, "2026-10-20T00:00:00Z");
        }
        when(aiAnalyzer.analyze(any(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "quota", null, null, null));

        assertThat(analyze()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repo_analysis", Integer.class)).isEqualTo(2);
        verify(gitHubClient, never()).fetchReadme("a/last");
    }

    @Test
    void blockedRepo403IsQuarantinedButRateLimit403Stops() {
        // 한도가 남은 403은 약관 위반으로 막힌 레포 하나의 문제 → 격리하고 계속, 한도가 0인 403은 전체 문제 → 멈춤
        insertRepo("a/tos-blocked", "2026-10-20T00:00:00Z");
        insertRepo("a/next", "2026-10-20T00:00:00Z");
        insertRepo("a/limited", "2026-10-20T00:00:00Z");
        insertRepo("a/last", "2026-10-20T00:00:00Z");
        when(gitHubClient.fetchReadme("a/tos-blocked")).thenThrow(forbidden("4999"));
        stub("a/next", result(true, "dev-tools", 0.9));
        when(gitHubClient.fetchReadme("a/limited")).thenThrow(forbidden("0"));

        assertThat(analyze()).isEqualTo(2);
        assertThat(statusOf("a/tos-blocked")).isEqualTo("PENDING_REVIEW");
        assertThat(statusOf("a/next")).isEqualTo("PUBLISHED");
        verify(gitHubClient, never()).fetchReadme("a/last");
    }

    @Test
    void timeoutSkipsRepoButConsecutiveTimeoutsStop() {
        // 가끔 느린 응답은 그 레포만 내일로 미루고 계속, 3번 연달아 느리면 서비스 장애로 보고 멈춘다
        insertRepo("a/slow", "2026-10-20T00:00:00Z");
        insertRepo("a/next", "2026-10-20T00:00:00Z");
        insertRepo("a/t1", "2026-10-20T00:00:00Z");
        insertRepo("a/t2", "2026-10-20T00:00:00Z");
        insertRepo("a/t3", "2026-10-20T00:00:00Z");
        insertRepo("a/last", "2026-10-20T00:00:00Z");
        stub("a/next", result(true, "dev-tools", 0.9));
        for (String name : List.of("a/slow", "a/t1", "a/t2", "a/t3")) {
            when(aiAnalyzer.analyze(eq(name), any(), any())).thenThrow(timeout());
        }

        assertThat(analyze()).isEqualTo(1);
        assertThat(statusOf("a/next")).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repo_analysis", Integer.class)).isEqualTo(1); // 건너뛴 레포는 저장 안 함 → 내일 재시도
        verify(gitHubClient, never()).fetchReadme("a/last");
    }

    @Test
    void installCommandNotInReadmeIsDropped() {
        // AI가 README에 없는 명령(다른 패키지 이름)을 지어내면 저장하지 않는다
        insertRepo("a/real", "2026-10-20T00:00:00Z");
        insertRepo("a/made-up", "2026-10-20T00:00:00Z");
        when(gitHubClient.fetchReadme("a/real")).thenReturn("## 설치\n```\nnpm i x\n```");
        when(gitHubClient.fetchReadme("a/made-up")).thenReturn("## 설치\n```\nnpm i -g @acme/x\n```");
        stub("a/real", result(true, "dev-tools", 0.9));
        stub("a/made-up", result(true, "dev-tools", 0.9));

        analyze();

        assertThat(installOf("a/real")).isEqualTo("npm i x");
        assertThat(installOf("a/made-up")).isNull();
    }

    @Test
    void analyzedReposAreNotAnalyzedAgain() {
        long id = insertRepo("a/done", "2026-10-20T00:00:00Z");
        insertAnalysis(id, "dev-tools", "PUBLISHED", true);

        assertThat(analyze()).isZero();
        verify(aiAnalyzer, never()).analyze(any(), any(), any());
    }

    private void stub(String fullName, AnalysisResult r) {
        when(aiAnalyzer.analyze(eq(fullName), any(), any())).thenReturn(r);
    }

    private static AnalysisResult result(boolean useful, String slug, double confidence) {
        return new AnalysisResult(useful, slug, "요약", "npm i x", "이유", confidence, "{\"useful\": " + useful + "}");
    }

    /** 실제 읽기 시간 초과와 같은 모양: 해석 오류로 감싸진 SocketTimeoutException */
    private static RestClientException timeout() {
        return new RestClientException("Error while extracting response for type [JsonNode] and content type [application/octet-stream]",
                new SocketTimeoutException("Read timed out"));
    }

    private static HttpClientErrorException forbidden(String rateLimitRemaining) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", rateLimitRemaining);
        return HttpClientErrorException.create(HttpStatus.FORBIDDEN, "forbidden", headers, null, null);
    }

    private String installOf(String fullName) {
        return jdbc.queryForObject(
                "SELECT a.install_cmd FROM repo_analysis a JOIN repo r ON r.id = a.repo_id WHERE r.full_name = ?",
                String.class, fullName);
    }

    private String statusOf(String fullName) {
        return jdbc.queryForObject(
                "SELECT a.status FROM repo_analysis a JOIN repo r ON r.id = a.repo_id WHERE r.full_name = ?",
                String.class, fullName);
    }

    static final LocalDate TODAY = LocalDate.parse("2026-10-30");

    /** 마감 시각 없이 오늘 판별을 돌린다 */
    private int analyze() {
        return analysisService.analyzePending(TODAY, Instant.MAX);
    }
}
