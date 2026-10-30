package com.toolpick.collector;

import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.repo.Repo;
import com.toolpick.repo.RepoRepository;
import com.toolpick.repo.StarSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 배치 DISCOVER·COLLECT 단계.
 * [CS] GitHub 호출(네트워크)을 먼저 끝내고, 저장은 짧은 트랜잭션으로 나눠 한다. 둘 다 upsert라 같은 날 다시 돌려도 결과가 같다.
 */
@Slf4j
@Service
public class CollectorService {

    static final int SAVE_CHUNK = 100;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final GitHubClient gitHubClient;
    private final RepoRepository repoRepository;
    private final StarSnapshotRepository snapshotRepository;
    private final int trackPushedWithinDays;
    private final TransactionTemplate tx;

    public CollectorService(GitHubClient gitHubClient, RepoRepository repoRepository,
                            StarSnapshotRepository snapshotRepository, ToolPickProperties props,
                            PlatformTransactionManager txManager) {
        this.gitHubClient = gitHubClient;
        this.repoRepository = repoRepository;
        this.snapshotRepository = snapshotRepository;
        this.trackPushedWithinDays = props.candidate().trackPushedWithinDays();
        this.tx = new TransactionTemplate(txManager);
    }

    /** 새로 뜨는 후보를 찾아 저장한다. 라이선스 없음·보관됨·포크는 뺀다 */
    public int discoverCandidates(LocalDate date) {
        List<GitHubRepoDto> candidates = gitHubClient.searchCandidates(date).stream()
                .filter(dto -> dto.license() != null && !dto.archived() && !dto.fork())
                .toList();
        tx.executeWithoutResult(status -> candidates.forEach(this::saveOrUpdate));
        log.info("후보 발견: {}개", candidates.size());
        return candidates.size();
    }

    /**
     * 추적 대상 레포의 오늘 스타 수를 저장한다 (재설계 04).
     * v1은 모든 레포를 매일 수집해 star_snapshot이 계속 늘었다. v2는 거절됐거나 오래 방치된 레포를 뺀다(북마크는 항상 수집).
     * 기준 시각은 배치 날짜 기준 KST 자정이다(last_pushed_at은 TIMESTAMPTZ라 Instant로 넘긴다).
     */
    public int collectSnapshots(LocalDate date) {
        Instant since = date.minusDays(trackPushedWithinDays).atStartOfDay(KST).toInstant();
        List<String> fullNames = repoRepository.findTrackableFullNames(since);
        List<GitHubRepoDto> fetched = gitHubClient.fetchRepos(fullNames);
        for (int from = 0; from < fetched.size(); from += SAVE_CHUNK) {
            List<GitHubRepoDto> chunk = fetched.subList(from, Math.min(from + SAVE_CHUNK, fetched.size()));
            tx.executeWithoutResult(status -> chunk.forEach(dto -> {
                Repo repo = saveOrUpdate(dto);
                snapshotRepository.upsert(repo.getId(), date, dto.stars());
            }));
        }
        log.info("스타 수집: {}/{}개 (전체 {}개 중 추적 대상)", fetched.size(), fullNames.size(), repoRepository.countActive());
        return fetched.size();
    }

    private Repo saveOrUpdate(GitHubRepoDto dto) {
        releaseNameIfTaken(dto);
        return repoRepository.findByGithubId(dto.githubId())
                .map(repo -> {
                    repo.updateFrom(dto);
                    return repo;
                })
                .orElseGet(() -> repoRepository.save(Repo.from(dto)));
    }

    /** 다른 레포(githubId가 다름)가 같은 이름을 쓰고 있으면 옛 행 이름을 비켜 둔다 (Repo.releaseName) */
    private void releaseNameIfTaken(GitHubRepoDto dto) {
        repoRepository.findByFullName(dto.fullName())
                .filter(other -> !other.getGithubId().equals(dto.githubId()))
                .ifPresent(other -> {
                    log.warn("이름 충돌: {} (옛 id={}, 새 id={}) → 옛 행 이름을 비켜 둠",
                            dto.fullName(), other.getGithubId(), dto.githubId());
                    other.releaseName();
                    repoRepository.saveAndFlush(other);
                });
    }
}
