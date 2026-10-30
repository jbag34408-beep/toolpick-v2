package com.toolpick.batch;

import com.toolpick.analysis.AnalysisService;
import com.toolpick.collector.CollectorService;
import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.ranking.RankingCalculator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 하루 배치 (docs/design/v2-00-backend.md 4.2).
 * <pre>
 * DISCOVER → COLLECT → RANK → ANALYZE → RANK(다시) → DONE
 * </pre>
 * <ul>
 *   <li>RANK를 판별 앞에 둔다 (재설계 02). 판별이 실패하거나 끊겨도 오늘 순위는 이미 공개돼 있다.
 *       판별 뒤 RANK를 한 번 더 해서 오늘 새로 공개된 레포를 반영한다(RankingCalculator가 멱등).</li>
 *   <li>v1과 달리 웹 서버 안의 비동기 스레드가 아니라, 이 메서드를 부른 프로세스(batch 프로필)에서 동기로 돈다.</li>
 *   <li>판별에는 마감 시각을 둔다. 시작 후 stale-after(70분)가 지나면 다른 실행이 이 기록을 끊긴 것으로 보고 이어받으므로,
 *       그보다 10분 먼저 판별을 멈춰 두 실행이 함께 판별(AI 한도 2배)하는 일을 막는다.</li>
 * </ul>
 */
@Slf4j
@Component
public class DailyJob {

    static final Duration ANALYZE_MARGIN = Duration.ofMinutes(10);

    private final BatchRunStore store;
    private final CollectorService collectorService;
    private final AnalysisService analysisService;
    private final RankingCalculator rankingCalculator;
    private final Duration staleAfter;

    /** 지금 맡고 있는 실행. 종료 신호를 받으면 FAILED로 남기려고 기억한다 */
    private volatile BatchRunStore.Claim current;
    private volatile BatchStep currentStep;

    public DailyJob(BatchRunStore store, CollectorService collectorService, AnalysisService analysisService,
                    RankingCalculator rankingCalculator, ToolPickProperties props) {
        this.store = store;
        this.collectorService = collectorService;
        this.analysisService = analysisService;
        this.rankingCalculator = rankingCalculator;
        this.staleAfter = props.job().staleAfter();
    }

    public BatchOutcome run(LocalDate date) {
        BatchRunStore.Decision decision = store.claim(date, staleAfter);
        if (decision.claim() == null) {
            log.info("배치 건너뜀: date={}, 이유={}", date, decision.skip());
            return decision.skip() == BatchRunStore.Skip.ALREADY_DONE
                    ? BatchOutcome.ALREADY_DONE : BatchOutcome.SKIPPED_IN_PROGRESS;
        }
        BatchRunStore.Claim claim = decision.claim();
        current = claim;
        log.info("배치 시작: id={}, date={}", claim.id(), date);
        try {
            moveTo(claim, BatchStep.DISCOVER);
            collectorService.discoverCandidates(date);
            moveTo(claim, BatchStep.COLLECT);
            collectorService.collectSnapshots(date);
            moveTo(claim, BatchStep.RANK);
            rankingCalculator.calculate(date);
            log.info("오늘 순위 공개: date={}", date);

            moveTo(claim, BatchStep.ANALYZE);
            Instant deadline = claim.startedAt().toInstant().plus(staleAfter).minus(ANALYZE_MARGIN);
            int analyzed = analysisService.analyzePending(date, deadline);

            if (analyzed > 0) {
                moveTo(claim, BatchStep.RANK); // 판별 뒤 다시 계산. v1 호환을 위해 RERANK가 아니라 RANK로 기록
                rankingCalculator.calculate(date);
            }
            if (!store.succeed(claim)) {
                throw new StaleRunException();
            }
            log.info("배치 성공: id={}", claim.id());
            return BatchOutcome.COMPLETED;
        } catch (StaleRunException e) {
            log.warn("배치 중단: id={}는 다른 실행이 이어받았다 (이 실행 시작 {})", claim.id(), claim.startedAt());
            return BatchOutcome.SKIPPED_IN_PROGRESS;
        } catch (Exception e) {
            log.error("배치 실패: id={}, step={}", claim.id(), currentStep, e);
            recordFailure(claim, currentStep + " 단계 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return BatchOutcome.FAILED;
        } finally {
            current = null;
        }
    }

    /**
     * 실행 환경이 시간 초과·취소로 SIGTERM을 보내면 스프링이 컨텍스트를 닫으며 이 메서드를 부른다.
     * RUNNING으로 남기면 70분 동안 아무도 다시 돌리지 못하므로 바로 FAILED로 남긴다.
     */
    @PreDestroy
    void onShutdown() {
        BatchRunStore.Claim claim = current;
        if (claim != null) {
            log.warn("종료 신호로 배치 중단: id={}, step={}", claim.id(), currentStep);
            recordFailure(claim, "프로세스 종료 신호로 중단됨 (" + currentStep + " 단계)");
        }
    }

    private void moveTo(BatchRunStore.Claim claim, BatchStep step) {
        log.info("배치 단계: id={}, step={}", claim.id(), step);
        currentStep = step;
        if (!store.moveTo(claim, step)) {
            throw new StaleRunException();
        }
    }

    private void recordFailure(BatchRunStore.Claim claim, String message) {
        try {
            store.fail(claim, message);
        } catch (RuntimeException e) {
            log.error("실패 기록도 실패: id={}", claim.id(), e);
        }
    }

    /** 다른 실행이 이 기록을 다시 시작했다 (started_at이 바뀜) */
    private static class StaleRunException extends RuntimeException {
    }
}
