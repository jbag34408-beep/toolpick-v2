package com.toolpick.admin.dto;

import com.toolpick.analysis.RepoAnalysis;
import java.math.BigDecimal;

/** raw는 Gemini 원본 응답(JSON 문자열). 판단 근거(reason)나 파싱 오류를 그대로 보여준다 */
public record ReviewItemResponse(
        Long id,
        Long repoId,
        String fullName,
        String description,
        String htmlUrl,
        boolean useful,
        String categorySlug,
        String summaryKo,
        String installCmd,
        BigDecimal confidence,
        String raw) {

    // a.getRepo()는 지연 로딩 필드지만, 조회 쿼리(findByStatusOrderByIdAsc)가 @EntityGraph로 미리 JOIN해 둬서 추가 쿼리가 없다
    public static ReviewItemResponse from(RepoAnalysis a) {
        return new ReviewItemResponse(a.getId(), a.getRepo().getId(), a.getRepo().getFullName(),
                a.getRepo().getDescription(), a.getRepo().getHtmlUrl(), a.isUseful(), // boolean 필드의 getter는 isXxx()
                a.getCategory() == null ? null : a.getCategory().getSlug(),
                a.getSummaryKo(), a.getInstallCmd(), a.getConfidence(), a.getRaw());
    }
}
