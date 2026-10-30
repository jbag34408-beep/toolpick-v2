package com.toolpick.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toolpick.IntegrationTest;
import com.toolpick.analysis.AnalysisResult;
import com.toolpick.collector.GitHubRepoDto;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 배치 실행 순서(재설계 02)와 시작 판단·중복 방지(docs/design/v2-00-backend.md 4.2).
 * 외부 API는 IntegrationTest의 가짜(GitHubClient, AiAnalyzer)로 대신한다.
 */
class DailyJobTest extends IntegrationTest {

    static final LocalDate DATE = LocalDate.parse("2026-10-30");
    static final Instant PUSHED = Instant.parse("2026-10-25T00:00:00Z");
    static final AnalysisResult USEFUL = new AnalysisResult(true, "dev-tools", "새 도구", null, "근거", 0.95, "{}");

    @Autowired
    DailyJob dailyJob;
    @Autowired
    BatchRunStore store;

    /** a/tool: 이미 공개, 7일 전 100 → 오늘 200 / b/cli: 미판별, 6일 전 10 → 오늘 50 */
    private void givenOnePublishedAndOneNewRepo() {
        long tool = insertRepo("a/tool", "2026-10-25T00:00:00Z");
        insertAnalysis(tool, "dev-tools", "PUBLISHED", true);
        insertSnapshot(tool, "2026-10-23", 100);
        long cli = insertRepo("b/cli", "2026-10-25T00:00:00Z");
        insertSnapshot(cli, "2026-10-24", 10);
        when(gitHubClient.searchCandidates(DATE)).thenReturn(List.of());
        when(gitHubClient.fetchRepos(anyList())).thenReturn(List.of(dto(1, "a/tool", 200), dto(2, "b/cli", 50)));
        when(gitHubClient.fetchReadme(anyString())).thenReturn("readme");
    }

    @Test
    void rankIsPublishedBeforeAnalyzeAndRecalculatedAfter() {
        givenOnePublishedAndOneNewRepo();
        AtomicLong rowsWhenAiCalled = new AtomicLong(-1);
        when(aiAnalyzer.analyze(anyString(), any(), any())).thenAnswer(inv -> {
            rowsWhenAiCalled.set(rankingRows());
            return USEFUL;
        });

        assertThat(dailyJob.run(DATE)).isEqualTo(BatchOutcome.COMPLETED);

        // v1은 판별이 끝난 뒤에야 순위를 계산해 이 시점에 0행이었다
        assertThat(rowsWhenAiCalled.get()).isEqualTo(1);
        // 판별 뒤 다시 계산해 오늘 공개된 b/cli도 순위에 들어간다
        assertThat(jdbc.queryForList("""
                SELECT r.full_name FROM daily_ranking d JOIN repo r ON r.id = d.repo_id
                WHERE d.ranking_date = DATE '2026-10-30' ORDER BY d.rank_no""", String.class))
                .containsExactly("a/tool", "b/cli");
        assertThat(jdbc.queryForMap("SELECT status, step, finished_at FROM batch_run WHERE run_date = DATE '2026-10-30'"))
                .containsEntry("status", "SUCCESS").containsEntry("step", "DONE")
                .hasEntrySatisfying("finished_at", v -> assertThat(v).isNotNull());
    }

    @Test
    void analyzeFailureKeepsTodaysRanking() {
        givenOnePublishedAndOneNewRepo();
        when(aiAnalyzer.analyze(anyString(), any(), any())).thenThrow(new IllegalStateException("예상 못 한 오류"));

        BatchOutcome outcome = dailyJob.run(DATE);

        assertThat(outcome).isEqualTo(BatchOutcome.FAILED);
        assertThat(outcome.exitCode()).isEqualTo(1);
        assertThat(rankingRows()).isEqualTo(1); // RANK에서 공개한 오늘 순위는 남는다
        assertThat(jdbc.queryForMap("SELECT status, step, error FROM batch_run WHERE run_date = DATE '2026-10-30'"))
                .containsEntry("status", "FAILED").containsEntry("step", "ANALYZE")
                .hasEntrySatisfying("error", e -> assertThat((String) e).contains("ANALYZE").contains("예상 못 한 오류"));
    }

    @Test
    void failedRunIsRetriedFromStart() {
        givenOnePublishedAndOneNewRepo();
        when(aiAnalyzer.analyze(anyString(), any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThat(dailyJob.run(DATE)).isEqualTo(BatchOutcome.FAILED);

        // [문법] 예외를 던지게 스텁한 메서드는 when(...) 안에서 호출하면 그 예외가 나므로 doReturn으로 바꾼다
        doReturn(USEFUL).when(aiAnalyzer).analyze(anyString(), any(), any());
        assertThat(dailyJob.run(DATE)).isEqualTo(BatchOutcome.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_run", Integer.class)).isEqualTo(1);
    }

    @Test
    void successfulDayIsNotRunAgain() {
        jdbc.update("INSERT INTO batch_run (run_date, status, step) VALUES (DATE '2026-10-30', 'SUCCESS', 'DONE')");

        assertThat(dailyJob.run(DATE)).isEqualTo(BatchOutcome.ALREADY_DONE);
        verify(gitHubClient, never()).searchCandidates(any());
    }

    @Test
    void recentRunningRunIsLeftAloneAndReportedAsSkipped() {
        jdbc.update("""
                INSERT INTO batch_run (run_date, status, step, started_at)
                VALUES (DATE '2026-10-30', 'RUNNING', 'ANALYZE', now() - interval '10 minutes')""");

        BatchOutcome outcome = dailyJob.run(DATE);

        assertThat(outcome).isEqualTo(BatchOutcome.SKIPPED_IN_PROGRESS);
        assertThat(outcome.exitCode()).isEqualTo(2); // 성공(0)과 구분된다
        verify(gitHubClient, never()).searchCandidates(any());
    }

    @Test
    void staleRunningRunIsRestarted() {
        jdbc.update("""
                INSERT INTO batch_run (run_date, status, step, started_at)
                VALUES (DATE '2026-10-30', 'RUNNING', 'ANALYZE', now() - interval '71 minutes')""");
        when(gitHubClient.searchCandidates(DATE)).thenReturn(List.of());
        when(gitHubClient.fetchRepos(anyList())).thenReturn(List.of());

        // v1은 RUNNING이면 그날 다시 돌릴 수 없었다(서버 재시작 리스너가 정리해 줄 때까지)
        assertThat(dailyJob.run(DATE)).isEqualTo(BatchOutcome.COMPLETED);
    }

    @Test
    void runningRunOfAnotherDateBlocks() {
        jdbc.update("""
                INSERT INTO batch_run (run_date, status, step, started_at)
                VALUES (DATE '2026-10-29', 'RUNNING', 'ANALYZE', now() - interval '5 minutes')""");

        assertThat(dailyJob.run(DATE)).isEqualTo(BatchOutcome.SKIPPED_IN_PROGRESS);
    }

    @Test
    void runTakenOverByAnotherProcessStopsWithoutOverwriting() {
        when(gitHubClient.searchCandidates(DATE)).thenAnswer(inv -> {
            // 이 실행이 DISCOVER 중일 때 다른 프로세스가 기록을 다시 시작했다
            jdbc.update("UPDATE batch_run SET started_at = started_at + interval '1 second'");
            return List.of();
        });

        assertThat(dailyJob.run(DATE)).isEqualTo(BatchOutcome.SKIPPED_IN_PROGRESS);
        assertThat(jdbc.queryForObject("SELECT status FROM batch_run", String.class)).isEqualTo("RUNNING");
    }

    @Test
    void shutdownSignalMarksRunningRunFailed() {
        when(gitHubClient.searchCandidates(DATE)).thenAnswer(inv -> {
            dailyJob.onShutdown(); // 실행 중 SIGTERM으로 컨텍스트가 닫히는 상황
            return List.of();
        });

        dailyJob.run(DATE);

        // RUNNING으로 남으면 70분 동안 재시도가 막힌다
        assertThat(jdbc.queryForMap("SELECT status, error FROM batch_run"))
                .containsEntry("status", "FAILED")
                .hasEntrySatisfying("error", e -> assertThat((String) e).contains("종료 신호").contains("DISCOVER"));
    }

    @Test
    void restartIsConditional() {
        jdbc.update("INSERT INTO batch_run (run_date, status, step) VALUES (DATE '2026-10-30', 'FAILED', 'COLLECT')");

        assertThat(store.claim(DATE, Duration.ofMinutes(70)).claim()).isNotNull();
        // 두 번째는 이미 RUNNING(최근)이라 맡지 못한다
        assertThat(store.claim(DATE, Duration.ofMinutes(70)).skip()).isEqualTo(BatchRunStore.Skip.IN_PROGRESS);
    }

    @Test
    void concurrentStartsClaimOnlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            go.await();
            return store.claim(DATE, Duration.ofMinutes(70)).claim() != null;
        };
        List<Future<Boolean>> results = List.of(pool.submit(attempt), pool.submit(attempt),
                pool.submit(attempt), pool.submit(attempt));
        go.countDown();
        long claimed = 0;
        for (Future<Boolean> r : results) {
            claimed += r.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(claimed).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_run", Integer.class)).isEqualTo(1);
    }

    private long rankingRows() {
        return jdbc.queryForObject("SELECT count(*) FROM daily_ranking WHERE ranking_date = DATE '2026-10-30'", Long.class);
    }

    private static GitHubRepoDto dto(long githubId, String fullName, int stars) {
        return new GitHubRepoDto(githubId, fullName, "설명", "Go", "MIT", "https://github.com/" + fullName,
                PUSHED, false, false, stars);
    }
}
