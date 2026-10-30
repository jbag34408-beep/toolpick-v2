package com.toolpick.admin.dto;

/** 관리자 검수 결정. JSON의 "APPROVE"/"REJECT" 문자열이 enum으로 자동 변환되고, 다른 값이면 400이 된다 */
public enum ReviewDecision {
    APPROVE, REJECT
}
