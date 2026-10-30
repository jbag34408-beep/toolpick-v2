package com.toolpick.ranking;

import com.toolpick.analysis.RepoAnalysis;
import com.toolpick.analysis.RepoAnalysisRepository;
import com.toolpick.category.Category;
import com.toolpick.category.CategoryRepository;
import com.toolpick.category.CategoryService;
import com.toolpick.common.config.CacheConfig;
import com.toolpick.ranking.dto.CategoryRankingResponse;
import com.toolpick.ranking.dto.RankingItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 순위 조회 (웹 API·MCP 공용). date가 없으면 가장 최근 계산일을 보여 준다.
 * [CS] 캐시는 TTL(10분)로만 갱신된다. 순위를 쓰는 배치는 다른 프로세스라 이 캐시를 비울 수 없다(CacheConfig 참고).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final DailyRankingRepository rankingRepository;
    private final RepoAnalysisRepository analysisRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    @Cacheable(value = CacheConfig.RANKINGS, key = "'all:' + #date")
    public List<CategoryRankingResponse> getRankings(LocalDate date) {
        LocalDate resolved = resolve(date);
        List<DailyRanking> rows = resolved == null ? List.of() : rankingRepository.findWithRepo(resolved);
        Map<Long, RepoAnalysis> analyses = analysesOf(rows);
        Map<Long, List<DailyRanking>> byCategory = rows.stream()
                .collect(Collectors.groupingBy(d -> d.getCategory().getId()));
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new CategoryRankingResponse(c.getSlug(), c.getNameKo(), resolved,
                        toItems(byCategory.getOrDefault(c.getId(), List.of()), analyses)))
                .toList();
    }

    @Cacheable(value = CacheConfig.RANKINGS, key = "#slug + ':' + #date")
    public CategoryRankingResponse getCategoryRanking(String slug, LocalDate date) {
        Category category = categoryService.getBySlug(slug);
        LocalDate resolved = resolve(date);
        List<DailyRanking> rows = resolved == null ? List.of() : rankingRepository.findWithRepo(resolved, category.getId());
        return new CategoryRankingResponse(category.getSlug(), category.getNameKo(), resolved,
                toItems(rows, analysesOf(rows)));
    }

    private LocalDate resolve(LocalDate date) {
        return date != null ? date : rankingRepository.findLatestDate().orElse(null);
    }

    private Map<Long, RepoAnalysis> analysesOf(List<DailyRanking> rows) {
        return analysisRepository.findByRepoIdIn(rows.stream().map(d -> d.getRepo().getId()).toList()).stream()
                .collect(Collectors.toMap(a -> a.getRepo().getId(), Function.identity()));
    }

    private static List<RankingItemResponse> toItems(List<DailyRanking> rows, Map<Long, RepoAnalysis> analyses) {
        return rows.stream().map(d -> {
            RepoAnalysis a = analyses.get(d.getRepo().getId());
            return new RankingItemResponse(d.getRankNo(), d.getRepo().getId(), d.getRepo().getFullName(),
                    a == null ? null : a.getSummaryKo(), a == null ? null : a.getInstallCmd(),
                    d.getStarGain(), d.getRepo().getLanguage(), d.getRepo().getHtmlUrl());
        }).toList();
    }
}
