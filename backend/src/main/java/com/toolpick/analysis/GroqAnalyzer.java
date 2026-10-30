package com.toolpick.analysis;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.common.http.HttpRetry;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Groq API(OpenAI 호환 형식)로 레포를 판별하는 AiAnalyzer 구현. 프롬프트와 응답 해석은 GeminiAnalyzer와 같다.
 * <p>
 * [CS] OpenAI 호환 API: 요청은 messages 배열, 응답은 choices[0].message.content. 많은 AI 회사가 같은 형식을 써서
 * 주소와 모델 이름만 바꾸면 다른 회사 모델로 갈아탈 수 있다.
 * gpt-oss는 답 전에 생각하는 추론 모델이라 reasoning_effort=low로 생각 토큰(무료 한도에서 차감)을 줄인다.
 * 생각 내용은 message.reasoning에 따로 오고 content에는 답(JSON)만 온다.
 */
@Component
public class GroqAnalyzer implements AiAnalyzer {

    private final RestClient restClient;
    private final HttpRetry retry;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean enabled;

    public GroqAnalyzer(@Qualifier("groqRestClient") RestClient restClient, HttpRetry retry,
                        ObjectMapper objectMapper, ToolPickProperties props) {
        this.restClient = restClient;
        this.retry = retry;
        this.objectMapper = objectMapper;
        this.model = props.groq().model();
        this.enabled = props.groq().apiKey() != null && !props.groq().apiKey().isBlank();
    }

    /** 키가 없으면 쓰지 않는다 (FallbackAnalyzer가 바로 Gemini로 넘긴다) */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public AnalysisResult analyze(String fullName, String description, String readme) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", GeminiAnalyzer.buildPrompt(fullName, description, readme))),
                "response_format", Map.of("type", "json_object"),
                "reasoning_effort", "low",
                "temperature", 0.2);

        JsonNode response;
        try {
            response = retry.call(() -> restClient.post()
                    .uri("/openai/v1/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 분당 한도는 Retry-After를 따르는 재시도로 넘기므로, 그래도 429면 하루 한도를 다 쓴 것으로 본다
            throw new AiQuotaExceededException("Groq 한도 초과", e);
        }
        return GeminiAnalyzer.parse(objectMapper, response.path("choices").path(0).path("message").path("content").asString(""));
    }
}
