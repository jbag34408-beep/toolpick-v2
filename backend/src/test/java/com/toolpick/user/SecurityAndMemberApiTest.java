package com.toolpick.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toolpick.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 권한·로그인 회원 기능 검증: 비로그인 401, USER의 관리자 API 403, CSRF 없으면 403, 북마크·검수 흐름.
 * <p>
 * [문법] spring-security-test의 요청 후처리기
 * <ul>
 *   <li>oauth2Login().oauth2User(u): 실제 GitHub 로그인 없이 "u로 로그인한 상태"의 요청을 만든다.</li>
 *   <li>csrf(): 올바른 CSRF 토큰을 붙인다. 빼면 변경 요청이 403이 되는 것으로 CSRF 방어가 켜져 있음을 확인한다.</li>
 * </ul>
 * [CS] 보안 테스트는 "허용되는 것"뿐 아니라 "막혀야 하는 것"을 반드시 검사한다(부정 테스트, negative test).
 */
class SecurityAndMemberApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GitHubOAuth2UserService oAuth2UserService;

    private RequestPostProcessor user;
    private RequestPostProcessor admin;

    @BeforeEach
    void registerUsers() {
        // 실제 로그인과 같은 경로로 사용자를 저장한다 (application-test.yml: admin-logins=admin-user)
        OAuth2User normal = oAuth2UserService.register(Map.of("id", 100, "login", "normal-user", "avatar_url", "a.png"));
        OAuth2User adminUser = oAuth2UserService.register(Map.of("id", 200, "login", "Admin-User"));
        user = oauth2Login().oauth2User(normal);
        admin = oauth2Login().oauth2User(adminUser);
    }

    @Test
    void registerAssignsRoleFromAdminLogins() {
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE github_id = 100", String.class)).isEqualTo("USER");
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE github_id = 200", String.class)).isEqualTo("ADMIN");

        oAuth2UserService.register(Map.of("id", 100, "login", "renamed"));
        assertThat(jdbc.queryForObject("SELECT login FROM app_user WHERE github_id = 100", String.class)).isEqualTo("renamed");
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookmarks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void userCannotUseAdminApi() throws Exception {
        mvc.perform(get("/api/admin/reviews").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reviews").with(admin)).andExpect(status().isOk());
    }

    @Test
    void meReturnsProfile() throws Exception {
        mvc.perform(get("/api/me").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("normal-user"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void bookmarkLifecycle() throws Exception {
        long repoId = insertRepo("a/tool", "2026-10-20T00:00:00Z");
        insertAnalysis(repoId, "dev-tools", "PUBLISHED", true);

        mvc.perform(post("/api/bookmarks/" + repoId).with(user)).andExpect(status().isForbidden()); // CSRF 토큰 없음
        mvc.perform(post("/api/bookmarks/" + repoId).with(user).with(csrf())).andExpect(status().isCreated());
        mvc.perform(post("/api/bookmarks/" + repoId).with(user).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/bookmarks/9999").with(user).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookmarks").with(user))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("a/tool"))
                .andExpect(jsonPath("$[0].summaryKo").value("요약"));
        mvc.perform(get("/api/bookmarks").with(admin)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/bookmarks/" + repoId).with(user).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/bookmarks").with(user)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void adminReviewApproveAndReject() throws Exception {
        long r1 = insertRepo("a/pending1", "2026-10-20T00:00:00Z");
        long r2 = insertRepo("a/pending2", "2026-10-20T00:00:00Z");
        long a1 = insertAnalysis(r1, "dev-tools", "PENDING_REVIEW", true);
        long a2 = insertAnalysis(r2, "dev-tools", "PENDING_REVIEW", true);

        mvc.perform(get("/api/admin/reviews").with(admin)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(patch("/api/admin/reviews/" + a1).with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"categorySlug\":\"security\",\"summaryKo\":\"고친 요약\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/admin/reviews/" + a2).with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/admin/reviews/" + a2).with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/reviews/" + a2).with(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForMap("""
                SELECT a.status, a.summary_ko, c.slug FROM repo_analysis a JOIN category c ON c.id = a.category_id
                WHERE a.id = ?""", a1))
                .containsEntry("status", "PUBLISHED").containsEntry("summary_ko", "고친 요약").containsEntry("slug", "security");
        assertThat(jdbc.queryForObject("SELECT status FROM repo_analysis WHERE id = ?", String.class, a2))
                .isEqualTo("REJECTED");
        mvc.perform(get("/api/admin/reviews").with(admin)).andExpect(jsonPath("$", hasSize(0)));
    }
}
