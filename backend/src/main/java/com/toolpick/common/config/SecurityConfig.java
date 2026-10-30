package com.toolpick.common.config;

import com.toolpick.user.GitHubOAuth2UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * 웹 보안 설정. 프론트엔드(frontend/lib/api.ts)가 기대는 동작을 v1 그대로 지킨다.
 * <ul>
 *   <li>CSRF 토큰을 응답마다 XSRF-TOKEN 쿠키(HttpOnly 아님)로 바로 내려준다. 프론트 JS가 읽어 X-XSRF-TOKEN 헤더로 돌려보낸다.
 *       [CS] Security 6+ 기본값은 토큰을 필요할 때만 만들어(지연 생성) 쿠키가 안 내려오고, 그러면 북마크·로그아웃이 403이 된다.
 *       setCsrfRequestAttributeName(null)이 즉시 생성으로 되돌린다.</li>
 *   <li>로그인이 필요한 요청은 로그인 화면으로 302가 아니라 401. 프론트 getMe가 이것으로 로그인 여부를 판단한다.</li>
 *   <li>로그아웃은 POST /api/logout → 204.</li>
 * </ul>
 * v2 변경: 배치 HTTP 트리거(/internal/**)가 없어졌다. batch 프로필은 웹 서버를 띄우지 않으므로 이 설정도 만들지 않는다.
 */
@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ToolPickProperties props,
                                            GitHubOAuth2UserService gitHubOAuth2UserService) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .cors(withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/mcp", "/mcp/**")) // MCP 클라이언트는 쿠키가 없고 읽기 전용 도구만 있다
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/rankings/**", "/api/repos/**").permitAll()
                        .requestMatchers("/mcp", "/mcp/**", "/actuator/health", "/oauth2/**", "/login/**", "/error")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/me", "/api/bookmarks/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(gitHubOAuth2UserService))
                        .defaultSuccessUrl(props.frontendOrigin(), true))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }
}
