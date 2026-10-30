package com.toolpick.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toolpick.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

/**
 * frontend/lib/api.ts가 기대는 보안 동작 (v2 설계 리뷰 지적: 기존 테스트로는 잡히지 않던 것).
 * CSRF 쿠키 발급은 실제 HTTP로 확인한다(CsrfCookieHttpTest).
 */
class FrontendContractTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GitHubOAuth2UserService oAuth2UserService;

    @Test
    void logoutReturnsNoContent() throws Exception {
        OAuth2User user = oAuth2UserService.register(Map.of("id", 300, "login", "someone"));

        mvc.perform(post("/api/logout").with(oauth2Login().oauth2User(user)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void anonymousMeIsUnauthorizedNotRedirect() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void batchTriggerEndpointIsGone() throws Exception {
        // v2는 배치를 HTTP로 시작하지 않는다 (batch 프로필 프로세스로 실행)
        mvc.perform(post("/internal/jobs/daily").header("X-Job-Secret", "anything"))
                .andExpect(status().is4xxClientError());
    }
}
