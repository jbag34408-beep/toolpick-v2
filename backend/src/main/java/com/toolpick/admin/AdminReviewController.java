package com.toolpick.admin;

import com.toolpick.admin.dto.ReviewItemResponse;
import com.toolpick.admin.dto.ReviewRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 검수 API. /api/admin/** 은 SecurityConfig에서 ROLE_ADMIN만 통과시킨다(USER는 403).
 * <p>
 * [CS] PATCH는 자원의 일부를 고칠 때 쓴다(상태·카테고리·요약만 변경). 전체를 바꾸는 PUT과 구분된다.
 * [문법] @RequestBody: 요청 본문 JSON을 ReviewRequest record로 변환한다. @Valid가 그 안의 @NotNull·@Size를 검사한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @GetMapping
    public List<ReviewItemResponse> pending() {
        return adminReviewService.getPending();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        adminReviewService.review(id, request);
        return ResponseEntity.noContent().build();
    }
}
