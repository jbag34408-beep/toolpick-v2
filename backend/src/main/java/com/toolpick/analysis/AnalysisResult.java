package com.toolpick.analysis;

/** AI(또는 규칙 필터)가 낸 판별 결과. rawJson은 그대로 repo_analysis.raw에 저장된다 */
public record AnalysisResult(
        boolean useful,
        String category,
        String summaryKo,
        String install,
        String reason,
        double confidence,
        String rawJson) {

    public static AnalysisResult parseFailed(String rawJson) {
        return new AnalysisResult(false, null, null, null, null, 0, rawJson);
    }

    /** 규칙 필터로 거절: 확신도 1.00이라 decideStatus에서 REJECTED가 된다 */
    public static AnalysisResult ruleRejected(String rawJson) {
        return new AnalysisResult(false, null, null, null, null, 1.0, rawJson);
    }

    public AnalysisResult withoutInstall() {
        return new AnalysisResult(useful, category, summaryKo, null, reason, confidence, rawJson);
    }
}
