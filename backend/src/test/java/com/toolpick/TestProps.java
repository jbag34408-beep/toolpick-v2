package com.toolpick;

import com.toolpick.common.config.ToolPickProperties;
import java.time.Duration;
import java.util.List;

/** 스프링 없이 도는 단위 테스트용 설정값 */
public final class TestProps {

    private TestProps() {
    }

    public static ToolPickProperties withBaseUrl(String baseUrl) {
        return new ToolPickProperties(
                "http://localhost:3000",
                List.of(),
                Duration.ofMillis(10),
                new ToolPickProperties.Github("test-token", baseUrl),
                new ToolPickProperties.Gemini("test-key", "gemini-test", baseUrl),
                new ToolPickProperties.Groq("test-key", "groq-test", baseUrl),
                new ToolPickProperties.Job(Duration.ofMinutes(70)),
                new ToolPickProperties.Candidate(100, 90, 30, 300, 60),
                new ToolPickProperties.Analysis(0.8, 200, Duration.ZERO, List.of("awesome", "tutorial", "tutorials", "dotfiles", "roadmap")));
    }
}
