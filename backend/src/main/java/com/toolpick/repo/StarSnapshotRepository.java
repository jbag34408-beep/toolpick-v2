package com.toolpick.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StarSnapshotRepository extends JpaRepository<StarSnapshot, Long> {

    /** [CS] upsert: 같은 날 두 번 수집해도 행이 하나로 남는다(배치 멱등성) */
    @Modifying
    @Query(value = """
            INSERT INTO star_snapshot (repo_id, snapshot_date, stars)
            VALUES (:repoId, :date, :stars)
            ON CONFLICT (repo_id, snapshot_date) DO UPDATE SET stars = EXCLUDED.stars""",
            nativeQuery = true)
    void upsert(@Param("repoId") Long repoId, @Param("date") LocalDate date, @Param("stars") int stars);

    @Query("""
            select s from StarSnapshot s
            where s.repo.id = :repoId and s.snapshotDate >= :from
            order by s.snapshotDate""")
    List<StarSnapshot> findHistory(@Param("repoId") Long repoId, @Param("from") LocalDate from);

    Optional<StarSnapshot> findFirstByRepoIdOrderBySnapshotDateDesc(Long repoId);
}
