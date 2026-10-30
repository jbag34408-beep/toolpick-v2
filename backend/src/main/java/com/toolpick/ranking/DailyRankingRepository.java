package com.toolpick.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyRankingRepository extends JpaRepository<DailyRanking, Long> {

    @Query("select max(d.rankingDate) from DailyRanking d")
    Optional<LocalDate> findLatestDate();

    @Query("""
            select d from DailyRanking d join fetch d.repo join fetch d.category c
            where d.rankingDate = :date
            order by c.sortOrder, d.rankNo""")
    List<DailyRanking> findWithRepo(@Param("date") LocalDate date);

    @Query("""
            select d from DailyRanking d join fetch d.repo join fetch d.category
            where d.rankingDate = :date and d.category.id = :categoryId
            order by d.rankNo""")
    List<DailyRanking> findWithRepo(@Param("date") LocalDate date, @Param("categoryId") Long categoryId);
}
