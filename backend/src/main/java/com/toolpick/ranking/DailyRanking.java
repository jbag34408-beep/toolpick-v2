package com.toolpick.ranking;

import com.toolpick.category.Category;
import com.toolpick.repo.Repo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 날짜·분야별 Top 10 한 줄. RankingCalculator가 SQL로만 쓴다(읽기 전용 엔티티) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate rankingDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private int rankNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @Column(nullable = false)
    private int starGain;
}
