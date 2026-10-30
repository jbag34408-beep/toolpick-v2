package com.toolpick.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RepoRepository extends JpaRepository<Repo, Long> {

    Optional<Repo> findByGithubId(Long githubId);

    Optional<Repo> findByFullName(String fullName);

    /**
     * 아직 판별 안 된 레포를 가장 최근 스냅샷 스타 수가 많은 순으로 (재설계 01, docs/design/v2-00-backend.md 4.3).
     * [CS] v1은 id(발견 순)로 꺼내 오늘 뜨는 레포가 대기열 뒤로 밀렸다. 7일 증가량 순은 오늘 발견된 레포가
     * 스냅샷 1개라 증가량 0이 되어 오히려 맨 뒤로 가므로, 후보 조건(생성 90일·스타 100 이상)을 이미 통과한
     * 레포의 현재 스타 수를 "뜨는 정도"로 쓴다. 스냅샷이 없으면 0으로 보아 맨 뒤.
     * 수집 범위(findTrackableFullNames)와 같은 푸시 조건을 건다. 오래 방치된 레포는 판별해도 순위에 못 오르고(30일 푸시 조건)
     * 스냅샷도 멈춰 있어, AI 한도를 쓰면 손해다. 동점은 id 순(발견 순)이라 스냅샷이 없을 때는 v1과 같은 순서다.
     * [CS] LATERAL: 바깥 행(r)마다 안쪽 쿼리를 돌린다. star_snapshot의 (repo_id, snapshot_date) UNIQUE 인덱스를 거꾸로 탄다.
     */
    @Query(value = """
            SELECT r.* FROM repo r
            LEFT JOIN LATERAL (
                SELECT s.stars FROM star_snapshot s
                WHERE s.repo_id = r.id
                ORDER BY s.snapshot_date DESC
                LIMIT 1
            ) latest ON TRUE
            WHERE NOT EXISTS (SELECT 1 FROM repo_analysis a WHERE a.repo_id = r.id)
              AND r.full_name NOT LIKE '%#%'
              AND (r.last_pushed_at IS NULL OR r.last_pushed_at >= :since)
            ORDER BY COALESCE(latest.stars, 0) DESC, r.id
            LIMIT :limit""", nativeQuery = true)
    List<Repo> findUnanalyzedByStars(@Param("since") Instant since, @Param("limit") int limit);

    /** 규칙 필터 대상의 이름만: 아직 판별 안 된 모든 레포 (`#` 옛 행 제외). 대기열이 커도 가볍게 읽도록 엔티티 대신 두 열만 */
    @Query("""
            select new com.toolpick.repo.RepoName(r.id, r.fullName) from Repo r
            where not exists (select 1 from RepoAnalysis a where a.repo = r)
              and r.fullName not like '%#%'
            order by r.id""")
    List<RepoName> findAllUnanalyzedNames();

    /**
     * 매일 스타를 수집할 레포 이름 (재설계 04, docs/design/v2-00-backend.md 4.5).
     * <ul>
     *   <li>북마크된 레포와 공개(PUBLISHED)된 레포는 항상 — 상세 화면의 스타 수·그래프가 멈추지 않게, 다시 활동하면 순위에 오르게</li>
     *   <li>그 밖에는 거절(REJECTED)되지 않았고 since 이후 푸시된 레포만 — 미판별·검수 대기 레포가 여기에 든다</li>
     * </ul>
     * [CS] bookmark.repo_id에는 단독 인덱스가 없다(UNIQUE(user_id, repo_id)의 두 번째 열). 북마크 수가 작아 해시 조인으로 충분하다.
     */
    @Query(value = """
            SELECT r.full_name FROM repo r
            WHERE r.full_name NOT LIKE '%#%'
              AND (
                    EXISTS (SELECT 1 FROM bookmark b WHERE b.repo_id = r.id)
                 OR EXISTS (SELECT 1 FROM repo_analysis a WHERE a.repo_id = r.id AND a.status = 'PUBLISHED')
                 OR (
                        NOT EXISTS (SELECT 1 FROM repo_analysis a WHERE a.repo_id = r.id AND a.status = 'REJECTED')
                    AND (r.last_pushed_at IS NULL OR r.last_pushed_at >= :since)
                 )
              )
            ORDER BY r.id""", nativeQuery = true)
    List<String> findTrackableFullNames(@Param("since") Instant since);

    @Query(value = "SELECT count(*) FROM repo WHERE full_name NOT LIKE '%#%'", nativeQuery = true)
    long countActive();
}
