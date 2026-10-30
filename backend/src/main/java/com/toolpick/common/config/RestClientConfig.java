package com.toolpick.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 외부 API별 HTTP 클라이언트. 연결 5초, 응답은 API 성격에 맞춰 30~60초에서 끊는다 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient gitHubRestClient(ToolPickProperties props) {
        return RestClient.builder()
                .baseUrl(props.github().baseUrl())
                .requestFactory(factory(Duration.ofSeconds(30)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.github().token())
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Bean
    RestClient geminiRestClient(ToolPickProperties props) {
        return RestClient.builder()
                .baseUrl(props.gemini().baseUrl())
                .requestFactory(factory(Duration.ofSeconds(60)))
                .defaultHeader("x-goog-api-key", props.gemini().apiKey()) // URL(?key=)에 넣으면 로그에 남는다
                .build();
    }

    @Bean
    RestClient groqRestClient(ToolPickProperties props) {
        return RestClient.builder()
                .baseUrl(props.groq().baseUrl())
                .requestFactory(factory(Duration.ofSeconds(60)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.groq().apiKey())
                .build();
    }

    private static SimpleClientHttpRequestFactory factory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
