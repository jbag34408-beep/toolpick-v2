package com.toolpick.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * application.yml의 toolpick.* 설정.
 * [CS] 키 끝에 줄바꿈이 섞이면 HTTP 헤더 오류로 배치가 실패한다(v1 10-29 사례). 그래서 비밀값은 생성할 때 strip()한다.
 */
@ConfigurationProperties("toolpick")
public record ToolPickProperties(
        @DefaultValue("http://localhost:3000") String frontendOrigin,
        @DefaultValue List<String> adminLogins,
        @DefaultValue("1s") Duration retryBaseDelay,
        Github github,
        Gemini gemini,
        Groq groq,
        @DefaultValue Job job,
        Candidate candidate,
        Analysis analysis) {

    private static String clean(String secret) {
        return secret == null ? null : secret.strip();
    }

    public record Github(String token, @DefaultValue("https://api.github.com") String baseUrl) {
        public Github {
            token = clean(token);
        }
    }

    public record Gemini(String apiKey, String model,
                         @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl) {
        public Gemini {
            apiKey = clean(apiKey);
        }
    }

    public record Groq(String apiKey, @DefaultValue("openai/gpt-oss-120b") String model,
                       @DefaultValue("https://api.groq.com") String baseUrl) {
        public Groq {
            apiKey = clean(apiKey);
        }
    }

    /** staleAfter: 이보다 오래된 RUNNING 기록은 끊긴 실행으로 보고 다시 시작한다 (v1 Actions timeout-minutes: 70) */
    public record Job(@DefaultValue("70m") Duration staleAfter) {
    }

    public record Candidate(int minStars, int createdWithinDays, int pushedWithinDays, int maxPerDay,
                            @DefaultValue("60") int trackPushedWithinDays) {
    }

    /** rejectNameWords: 레포 이름을 -, _, . 로 나눈 단어가 이 목록에 있으면 AI 없이 거절한다 (analysis.RuleFilter) */
    public record Analysis(double publishThreshold, int maxPerDay, @DefaultValue("0s") Duration requestInterval,
                           @DefaultValue List<String> rejectNameWords) {
    }
}
