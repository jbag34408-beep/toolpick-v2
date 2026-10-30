package com.toolpick.admin;

import com.toolpick.admin.dto.ReviewDecision;
import com.toolpick.admin.dto.ReviewItemResponse;
import com.toolpick.admin.dto.ReviewRequest;
import com.toolpick.analysis.AnalysisStatus;
import com.toolpick.analysis.RepoAnalysis;
import com.toolpick.analysis.RepoAnalysisRepository;
import com.toolpick.category.Category;
import com.toolpick.category.CategoryService;
import com.toolpick.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 검수. AI가 확신하지 못한 결과(PENDING_REVIEW)를 사람이 승인하거나 거절한다.
 * <p>
 * 검수 결과는 다음 배치의 순위 계산(RANK)부터 반영된다.
 * [CS] review()는 엔티티를 읽어 상태 메서드(approve/reject)만 부른다. save()가 없어도 @Transactional이 끝날 때
 * Hibernate가 바뀐 필드를 감지해 UPDATE한다(변경 감지, dirty checking).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminReviewService {

    private final RepoAnalysisRepository analysisRepository;
    private final CategoryService categoryService;

    @Transactional(readOnly = true)
    public List<ReviewItemResponse> getPending() {
        return analysisRepository.findByStatusOrderByIdAsc(AnalysisStatus.PENDING_REVIEW).stream()
                .map(ReviewItemResponse::from)
                .toList();
    }

    public void review(Long analysisId, ReviewRequest request) {
        RepoAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("판별 결과가 없습니다: " + analysisId));
        if (request.decision() == ReviewDecision.REJECT) {
            analysis.reject();
            return; // 이른 반환(early return): 거절이면 아래 승인 로직을 읽을 필요가 없다
        }
        // 관리자가 카테고리를 고르면 그것을, 비웠으면 AI가 고른 것을 쓴다
        Category category = request.categorySlug() == null || request.categorySlug().isBlank()
                ? analysis.getCategory()
                : categoryService.getBySlug(request.categorySlug());
        if (category == null) {
            // 공개되는 도구는 반드시 카테고리가 있어야 순위에 들어갈 수 있다(불변 조건, invariant)
            throw new IllegalArgumentException("승인하려면 카테고리를 골라야 합니다");
        }
        analysis.approve(category, request.summaryKo());
    }
}
