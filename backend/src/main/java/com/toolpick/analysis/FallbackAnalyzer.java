package com.toolpick.analysis;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Groq(무료 한도 큼)을 먼저 쓰고, 오늘 Groq 한도가 끝나면 Gemini로 넘긴다. 둘 다 끝나면 한도 초과를 던져 오늘 판별을 멈춘다.
 * <p>
 * [문법] @Primary: AiAnalyzer 구현이 셋이라, AnalysisService처럼 타입으로만 주입받는 곳에는 이 빈이 들어간다.
 * [CS] 대체 경로(fallback): 한 공급자가 막혀도 다른 공급자로 서비스를 이어 간다.
 * 배치는 한 번에 하나만 돌아서(AsyncConfig) groqExhaustedOn 필드를 여러 스레드가 동시에 고치지 않는다.
 */
@Slf4j
@Primary
@Component
public class FallbackAnalyzer implements AiAnalyzer {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final GroqAnalyzer groq;
    private final GeminiAnalyzer gemini;
    /** Groq 한도가 끝난 날. 날짜가 바뀌면 다시 Groq부터 쓴다 */
    private LocalDate groqExhaustedOn;

    public FallbackAnalyzer(GroqAnalyzer groq, GeminiAnalyzer gemini) {
        this.groq = groq;
        this.gemini = gemini;
    }

    @Override
    public AnalysisResult analyze(String fullName, String description, String readme) {
        LocalDate today = LocalDate.now(KST);
        if (groq.isEnabled() && !today.equals(groqExhaustedOn)) {
            try {
                return groq.analyze(fullName, description, readme);
            } catch (AiQuotaExceededException e) {
                log.warn("Groq 한도 초과 → 오늘 남은 판별은 Gemini로");
                groqExhaustedOn = today;
            }
        }
        return gemini.analyze(fullName, description, readme);
    }
}
