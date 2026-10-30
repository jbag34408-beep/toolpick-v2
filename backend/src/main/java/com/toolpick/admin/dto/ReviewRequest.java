package com.toolpick.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 승인할 때 카테고리·요약을 바꿀 수 있다 (비우면 AI 결과 그대로).
 * <p>
 * [문법] Bean Validation: @NotNull, @Size 같은 제약 어노테이션을 필드에 붙이고, 컨트롤러에서 @Valid로 검사를 켠다.
 * 규칙을 어기면 컨트롤러 메서드가 실행되기 전에 400 Bad Request가 응답된다.
 * [CS] 신뢰 경계(trust boundary)에서 입력을 검증한다. 관리자라도 요청 본문은 외부 입력이고,
 * 길이 제한이 없으면 아주 긴 문자열로 DB·화면을 망가뜨릴 수 있다.
 */
public record ReviewRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 50) String categorySlug,
        @Size(max = 200) String summaryKo) {
}
