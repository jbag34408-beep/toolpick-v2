package com.toolpick.ranking.dto;

import java.time.LocalDate;
import java.util.List;

/** date가 null이면 아직 계산된 순위가 없다 (frontend/lib/api.ts의 CategoryRanking) */
public record CategoryRankingResponse(String slug, String nameKo, LocalDate date, List<RankingItemResponse> items) {
}
