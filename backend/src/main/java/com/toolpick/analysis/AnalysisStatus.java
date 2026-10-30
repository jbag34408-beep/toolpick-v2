package com.toolpick.analysis;

/** PUBLISHED만 순위·상세 화면에 나온다. PENDING_REVIEW는 관리자 검수 대기 */
public enum AnalysisStatus {
    PENDING_REVIEW, PUBLISHED, REJECTED
}
