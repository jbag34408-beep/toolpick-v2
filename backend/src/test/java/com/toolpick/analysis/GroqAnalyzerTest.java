package com.toolpick.analysis;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.toolpick.TestProps;
import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.common.http.HttpRetry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * GroqAnalyzer(WireMock): OpenAI 호환 응답 해석, 429 → AiQuotaExceededException.
 * FallbackAnalyzer(가짜 객체): Groq 한도가 끝나면 그날은 Gemini만 쓴다.
 */
class GroqAnalyzerTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    static final String PATH = "/openai/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GroqAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        ToolPickProperties props = TestProps.withBaseUrl(wm.baseUrl());
        RestClient client = RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).baseUrl(wm.baseUrl())
                .defaultHeader("Authorization", "Bearer test-key").build();
        analyzer = new GroqAnalyzer(client, new HttpRetry(props), objectMapper, props);
    }

    @Test
    void parsesOpenAiStyleResponse() {
        String content = """
                {"useful": true, "category": "dev-tools", "summary_ko": "CLI 도구", "install": "npm i -g x",
                 "reason": "설치 방법 있음", "confidence": 0.9}""";
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(objectMapper.writeValueAsString(Map.of("choices", List.of(
                Map.of("message", Map.of("role", "assistant", "content", content, "reasoning", "생각 내용"))))))));

        AnalysisResult r = analyzer.analyze("a/tool", "desc", "# README");

        assertThat(r.useful()).isTrue();
        assertThat(r.category()).isEqualTo("dev-tools");
        assertThat(r.confidence()).isEqualTo(0.9);
        wm.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("groq-test")))
                .withRequestBody(matchingJsonPath("$.response_format.type", equalTo("json_object"))));
    }

    @Test
    void quotaExceededAfterRetries() {
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> analyzer.analyze("a/tool", null, null)).isInstanceOf(AiQuotaExceededException.class);
    }

    @Test
    void fallbackUsesGeminiForRestOfDayAfterGroqQuota() {
        GroqAnalyzer groq = mock(GroqAnalyzer.class);
        GeminiAnalyzer gemini = mock(GeminiAnalyzer.class);
        AnalysisResult ok = new AnalysisResult(true, "dev-tools", null, null, null, 0.9, "{}");
        when(groq.isEnabled()).thenReturn(true);
        when(groq.analyze("a/1", null, null)).thenThrow(new AiQuotaExceededException("한도", null));
        when(gemini.analyze("a/1", null, null)).thenReturn(ok);
        when(gemini.analyze("a/2", null, null)).thenReturn(ok);
        FallbackAnalyzer fallback = new FallbackAnalyzer(groq, gemini);

        assertThat(fallback.analyze("a/1", null, null)).isSameAs(ok);
        assertThat(fallback.analyze("a/2", null, null)).isSameAs(ok);

        verify(groq, times(1)).analyze("a/1", null, null);
        verify(groq, never()).analyze("a/2", null, null); // 한도가 끝난 날에는 Groq을 다시 부르지 않는다
    }

    @Test
    void secretsAreStripped() {
        // 비밀값 공백·줄바꿈 제거 부분: Render에 붙여 넣을 때 섞인 줄바꿈이 헤더 오류를 내지 않게
        assertThat(new ToolPickProperties.Groq(" gsk_x\n", "m", "u").apiKey()).isEqualTo("gsk_x");
        assertThat(new ToolPickProperties.Gemini("k\r\n", "m", "u").apiKey()).isEqualTo("k");
        assertThat(new ToolPickProperties.Github("t\n", "u").token()).isEqualTo("t");
        assertThat(new ToolPickProperties.Groq(null, "m", "u").apiKey()).isNull();
    }
}
