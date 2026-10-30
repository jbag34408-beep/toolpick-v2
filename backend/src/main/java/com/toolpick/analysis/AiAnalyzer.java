package com.toolpick.analysis;

/**
 * AI 판별기 인터페이스. 구현은 GroqAnalyzer·GeminiAnalyzer이고, AnalysisService에는 둘을 차례로 쓰는 FallbackAnalyzer(@Primary)가 들어간다.
 * <p>
 * [CS] 의존성 역전(DIP): AnalysisService는 "Gemini"가 아니라 이 인터페이스에만 의존한다.
 * 그래서 테스트에서는 가짜 구현(mock)으로 바꿔 끼워 AI 호출 없이 상태 결정 규칙만 검사할 수 있고,
 * 나중에 다른 AI로 바꿔도 AnalysisService는 고칠 필요가 없다.
 */
public interface AiAnalyzer {

    /**
     * 레포가 바로 설치해 쓸 수 있는 도구인지 판별한다.
     *
     * @throws AiQuotaExceededException AI 한도(429)에 걸려 오늘은 더 판별할 수 없을 때
     */
    AnalysisResult analyze(String fullName, String description, String readme);
}
