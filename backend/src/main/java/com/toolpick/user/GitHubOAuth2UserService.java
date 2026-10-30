package com.toolpick.user;

import com.toolpick.common.config.ToolPickProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * GitHub 로그인 시 사용자를 저장·갱신하고, ADMIN_LOGINS에 있으면 ROLE_ADMIN을 준다.
 * <p>
 * [문법] extends DefaultOAuth2UserService(상속): "access token으로 GitHub 사용자 정보를 받아 오는 일"은 부모 클래스가 이미 한다.
 * 우리는 loadUser를 재정의(override)해서 그 결과를 받은 뒤 DB 저장·권한 부여만 덧붙인다(템플릿 메서드를 확장).
 * <p>
 * [CS] 권한(authority)은 로그인할 때 정해져 세션에 저장된다. ADMIN_LOGINS를 바꾸면 그 사용자가 다시 로그인해야 반영된다.
 */
@Service
public class GitHubOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository userRepository;
    private final List<String> adminLogins;

    public GitHubOAuth2UserService(AppUserRepository userRepository, ToolPickProperties props) {
        this.userRepository = userRepository;
        // " a , b " 같은 입력의 공백과 빈 값을 정리해 둔다
        this.adminLogins = props.adminLogins().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        return register(super.loadUser(request).getAttributes()); // super: 부모 클래스의 원래 구현을 호출
    }

    /** loadUser에서 자기 호출되므로 트랜잭션 대신 save로 바로 저장한다 */
    public OAuth2User register(Map<String, Object> attributes) {
        // attributes는 GitHub /user API 응답 JSON을 Map으로 바꾼 것: {"id": 123, "login": "...", "avatar_url": "..."}
        // JSON 숫자는 Integer일 수도 Long일 수도 있어 공통 부모 Number로 받아 longValue()로 바꾼다
        Long githubId = ((Number) attributes.get("id")).longValue();
        String login = (String) attributes.get("login");
        String avatarUrl = (String) attributes.get("avatar_url");
        // GitHub 로그인 이름은 대소문자를 구분하지 않으므로 비교도 대소문자 무시
        Role role = adminLogins.stream().anyMatch(a -> a.equalsIgnoreCase(login)) ? Role.ADMIN : Role.USER;

        AppUser user = userRepository.findByGithubId(githubId)
                .orElseGet(() -> AppUser.create(githubId, login, avatarUrl, role));
        user.updateProfile(login, avatarUrl, role);
        userRepository.save(user);

        // 스프링 시큐리티의 권한 이름은 "ROLE_" 접두사 규칙을 쓴다. hasRole("ADMIN")은 "ROLE_ADMIN"을 찾는다
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (role == Role.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        // "id": 사용자 이름으로 쓸 속성(name attribute). 세션에 저장되는 로그인 사용자 객체가 된다
        return new DefaultOAuth2User(authorities, attributes, "id");
    }
}
