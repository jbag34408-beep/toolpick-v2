package com.toolpick.analysis;

import com.toolpick.category.Category;
import com.toolpick.category.CategoryRepository;
import com.toolpick.collector.GitHubClient;
import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.repo.Repo;
import com.toolpick.repo.RepoName;
import com.toolpick.repo.RepoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 미판별 레포를 판별한다 (배치 ANALYZE 단계).
 * <ol>
 *   <li>규칙 필터: 이름으로 걸리는 레포를 AI 없이 REJECTED로 저장한다. 외부 호출이 없고 AI 한도에도 세지 않는다 (재설계 03)</li>
 *   <li>AI 판별: 남은 미판별 레포를 최근 스타 수가 많은 순으로 하루 max-per-day개까지 (재설계 01)</li>
 * </ol>
 * [CS] 오류 분류(v1에서 검증): 한도·인증 오류는 그날 전체를 멈추고, 레포 하나의 오류는 건너뛴다.
 * 외부 API를 부르는 동안에는 트랜잭션을 열지 않고, 결과만 레포마다 짧은 트랜잭션으로 저장한다(커넥션 풀 5개).
 */
@Slf4j
@Service
public class AnalysisService {

    static final int MAX_CONSECUTIVE_TIMEOUTS = 3;
    static final int MAX_CONSECUTIVE_ERRORS = 3;
    static final int LOG_BODY_MAX = 300; // 오류 응답 본문은 앞부분만 남긴다 (키가 섞일 위험·로그 크기)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RepoRepository repoRepository;
    private final RepoAnalysisRepository analysisRepository;
    private final CategoryRepository categoryRepository;
    private final GitHubClient gitHubClient;
    private final AiAnalyzer aiAnalyzer;
    private final RuleFilter ruleFilter;
    private final ObjectMapper objectMapper;
    private final ToolPickProperties.Analysis props;
    private final int trackPushedWithinDays;
    private final TransactionTemplate tx;

    public AnalysisService(RepoRepository repoRepository, RepoAnalysisRepository analysisRepository,
                           CategoryRepository categoryRepository, GitHubClient gitHubClient, AiAnalyzer aiAnalyzer,
                           RuleFilter ruleFilter, ObjectMapper objectMapper, ToolPickProperties props,
                           PlatformTransactionManager txManager) {
        this.repoRepository = repoRepository;
        this.analysisRepository = analysisRepository;
        this.categoryRepository = categoryRepository;
        this.gitHubClient = gitHubClient;
        this.aiAnalyzer = aiAnalyzer;
        this.ruleFilter = ruleFilter;
        this.objectMapper = objectMapper;
        this.props = props.analysis();
        this.trackPushedWithinDays = props.candidate().trackPushedWithinDays();
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * @param date     배치 날짜. 판별 대상의 푸시 기준일(수집 범위와 같음)을 정한다
     * @param deadline 이 시각이 지나면 남은 판별을 내일로 미룬다 (DailyJob: 다른 실행이 이어받기 전에 멈춤)
     * @return AI로 판별한 개수 (규칙 거절은 세지 않는다)
     */
    public int analyzePending(LocalDate date, Instant deadline) {
        if (props.maxPerDay() <= 0) {
            // 그림자 실행처럼 판별을 끈 경우. 규칙 필터도 DB에 쓰므로 함께 건너뛴다.
            // v1은 PageRequest.of(0, 0) 예외로 배치가 실패했다
            log.info("AI 판별 꺼짐(max-per-day={}): 판별 단계를 건너뜀", props.maxPerDay());
            return 0;
        }
        rejectByRule();

        Map<String, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getSlug, Function.identity()));
        Instant since = date.minusDays(trackPushedWithinDays).atStartOfDay(KST).toInstant();
        List<Repo> repos = repoRepository.findUnanalyzedByStars(since, props.maxPerDay());
        int done = 0;
        int consecutiveTimeouts = 0;
        int consecutiveErrors = 0;
        for (Repo repo : repos) {
            if (Instant.now().isAfter(deadline)) {
                log.warn("판별 마감 시각 도달로 오늘 판별 중단: {}/{}개 완료", done, repos.size());
                break;
            }
            if (done > 0) {
                pause(props.requestInterval()); // 분당 요청 한도를 넘지 않게 요청 사이에 쉰다
            }
            AnalysisResult result;
            String readme = null;
            try {
                readme = gitHubClient.fetchReadme(repo.getFullName());
                result = aiAnalyzer.analyze(repo.getFullName(), repo.getDescription(), readme);
                consecutiveErrors = 0;
            } catch (AiQuotaExceededException e) {
                log.warn("AI 한도 초과로 오늘 판별 중단: {}/{}개 완료", done, repos.size());
                break;
            } catch (HttpClientErrorException e) {
                int code = e.getStatusCode().value();
                boolean badGeminiConfig = (code == 400 && e.getResponseBodyAsString().contains("API_KEY_INVALID")) || code == 404;
                if (code == 401 || code == 429 || (code == 403 && !isRepoBlocked(e)) || badGeminiConfig) {
                    log.warn("인증·한도 오류(HTTP {})로 오늘 판별 중단: {}/{}개 완료", code, done, repos.size());
                    break;
                }
                if (++consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    log.warn("오류가 {}번 연달아 나서 오늘 판별 중단: {}/{}개 완료 (HTTP {}: {})",
                            consecutiveErrors, done, repos.size(), code, head(e.getResponseBodyAsString()));
                    break;
                }
                log.warn("{} 판별 건너뜀: HTTP {} ({})", repo.getFullName(), code, head(e.getResponseBodyAsString()));
                result = AnalysisResult.parseFailed("{\"error\": \"HTTP " + code + "\"}");
            } catch (RestClientException e) {
                if (isTimeout(e) && ++consecutiveTimeouts < MAX_CONSECUTIVE_TIMEOUTS) {
                    log.warn("{} 판별 건너뜀: 응답 시간 초과 (내일 다시 시도)", repo.getFullName());
                    continue;
                }
                log.warn("외부 API 오류로 오늘 판별 중단: {}/{}개 완료 ({})", done, repos.size(), e.getMessage());
                break;
            }
            // AI가 지어낸 설치 명령을 막는다: README에 글자 그대로 있을 때만 남긴다
            if (result.install() != null && (readme == null || !readme.contains(result.install()))) {
                result = result.withoutInstall();
            }
            AnalysisResult analyzed = result;
            Category category = analyzed.category() == null ? null : categories.get(analyzed.category());
            AnalysisStatus status = decideStatus(analyzed, category != null, props.publishThreshold());
            if (!save(repo.getFullName(), () -> RepoAnalysis.of(repo, analyzed, category, status))) {
                continue;
            }
            done++;
            consecutiveTimeouts = 0;
        }
        log.info("AI 판별: {}/{}개", done, repos.size());
        return done;
    }

    /**
     * 이름 규칙에 걸리는 미판별 레포를 REJECTED로 저장한다. AI 판별 대상을 고르기 전에 커밋해야
     * 거절된 레포가 하루 max-per-day 칸을 차지하지 않는다.
     */
    int rejectByRule() {
        int rejected = 0;
        for (RepoName candidate : repoRepository.findAllUnanalyzedNames()) {
            String word = ruleFilter.match(candidate.name()).orElse(null);
            if (word == null) {
                continue;
            }
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("source", "rule");
            raw.put("rule", word);
            AnalysisResult result = AnalysisResult.ruleRejected(objectMapper.writeValueAsString(raw));
            // getReferenceById: 행을 다시 읽지 않고 외래 키만 쓰는 프록시. 같은 트랜잭션 안에서 만든다
            if (save(candidate.fullName(), () -> RepoAnalysis.of(repoRepository.getReferenceById(candidate.id()),
                    result, null, AnalysisStatus.REJECTED))) {
                rejected++;
            }
        }
        log.info("규칙 거절: {}개", rejected);
        return rejected;
    }

    /**
     * 레포 하나의 결과를 짧은 트랜잭션으로 저장한다.
     * repo_analysis.repo_id가 UNIQUE라, 다른 실행이 먼저 저장했으면 위반이 난다. 그 레포만 건너뛰고 계속한다.
     * [CS] 예외는 트랜잭션 밖에서 잡는다. 안에서 잡으면 트랜잭션이 rollback-only로 표시돼 커밋 때 다시 예외가 난다.
     */
    private boolean save(String fullName, Supplier<RepoAnalysis> analysis) {
        try {
            tx.executeWithoutResult(s -> analysisRepository.save(analysis.get()));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("{} 판별 결과가 이미 있어 건너뜀 (다른 실행이 먼저 저장)", fullName);
            return false;
        }
    }

    static AnalysisStatus decideStatus(AnalysisResult r, boolean validCategory, double threshold) {
        boolean confident = r.confidence() >= threshold;
        if (r.useful() && confident && validCategory) {
            return AnalysisStatus.PUBLISHED;
        }
        if (!r.useful() && confident) {
            return AnalysisStatus.REJECTED;
        }
        return AnalysisStatus.PENDING_REVIEW;
    }

    static String head(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= LOG_BODY_MAX ? body : body.substring(0, LOG_BODY_MAX) + "…";
    }

    /** GitHub 403 중 한도가 남아 있고 Retry-After도 없으면 그 레포만 막힌 것(DMCA 등)이라 건너뛴다 */
    private static boolean isRepoBlocked(HttpClientErrorException e) {
        HttpHeaders h = e.getResponseHeaders();
        String remaining = h == null ? null : h.getFirst("X-RateLimit-Remaining");
        return remaining != null && !remaining.equals("0") && h.getFirst(HttpHeaders.RETRY_AFTER) == null;
    }

    static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static void pause(Duration d) {
        if (d.isZero()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("판별 대기 중 중단됨", e);
        }
    }
}
