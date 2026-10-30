package com.toolpick.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.toolpick.TestcontainersConfig;
import com.toolpick.analysis.AiAnalyzer;
import com.toolpick.collector.GitHubClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

/**
 * 프론트는 XSRF-TOKEN 쿠키를 JS로 읽어 X-XSRF-TOKEN 헤더로 돌려보낸다(frontend/lib/api.ts csrfHeader).
 * 그래서 첫 응답부터 쿠키가 HttpOnly 없이 내려와야 한다. 지연 생성이면 북마크·로그아웃·검수가 403이 된다.
 * <p>
 * [CS] MockMvc의 csrf() 후처리기는 공유 컨텍스트의 CSRF 저장소를 테스트용으로 바꿔 끼우므로,
 * 쿠키 발급은 실제 서버를 띄워(별도 컨텍스트) 브라우저와 같은 HTTP 응답으로 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class CsrfCookieHttpTest {

    @MockitoBean
    GitHubClient gitHubClient;

    @MockitoBean
    AiAnalyzer aiAnalyzer;

    @LocalServerPort
    int port;

    @Test
    void csrfTokenCookieIsIssuedImmediatelyAndReadableByJs() {
        ResponseEntity<String> res = RestClient.create("http://localhost:" + port)
                .get().uri("/api/categories").retrieve().toEntity(String.class);

        List<String> setCookies = res.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anySatisfy(c -> assertThat(c).startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly"));
    }
}
