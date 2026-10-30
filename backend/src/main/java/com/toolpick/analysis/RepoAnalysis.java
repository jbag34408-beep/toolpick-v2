package com.toolpick.analysis;

import com.toolpick.category.Category;
import com.toolpick.repo.Repo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** 레포 하나의 판별 결과 (레포당 한 행). raw에는 AI 응답 원문 또는 규칙 필터 근거를 JSON으로 남긴다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepoAnalysis {

    static final int INSTALL_MAX = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", unique = true)
    private Repo repo;

    @Column(nullable = false)
    private boolean useful;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(columnDefinition = "text")
    private String summaryKo;

    @Column(length = INSTALL_MAX)
    private String installCmd;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String raw;

    @Column(nullable = false)
    private Instant analyzedAt;

    public static RepoAnalysis of(Repo repo, AnalysisResult result, Category category, AnalysisStatus status) {
        RepoAnalysis a = new RepoAnalysis();
        a.repo = repo;
        a.useful = result.useful();
        a.category = category;
        a.summaryKo = result.summaryKo();
        a.installCmd = result.install() == null || result.install().length() <= INSTALL_MAX ? result.install() : null;
        double clamped = Math.max(0, Math.min(1, result.confidence()));
        a.confidence = BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
        a.status = status;
        a.raw = result.rawJson();
        a.analyzedAt = Instant.now();
        return a;
    }

    public void approve(Category category, String summaryKo) {
        this.useful = true;
        this.category = category;
        if (summaryKo != null && !summaryKo.isBlank()) {
            this.summaryKo = summaryKo;
        }
        this.status = AnalysisStatus.PUBLISHED;
    }

    public void reject() {
        this.useful = false;
        this.status = AnalysisStatus.REJECTED;
    }
}
