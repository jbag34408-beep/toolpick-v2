package com.toolpick.analysis;

/**
 * Gemini 429: 그날 판별을 멈추고 남은 레포는 다음 날로 넘긴다.
 * <p>
 * [CS] HTTP 라이브러리 예외(HttpClientErrorException.TooManyRequests)를 업무 의미가 있는 예외로 바꿔 던진다(예외 변환).
 * AnalysisService는 HTTP를 몰라도 "한도 초과"라는 의미만 보고 처리할 수 있다.
 * 생성자의 cause에 원래 예외를 담아 두면 로그에서 원인까지 거슬러 볼 수 있다(예외 연쇄, exception chaining).
 */
public class AiQuotaExceededException extends RuntimeException {

    public AiQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
