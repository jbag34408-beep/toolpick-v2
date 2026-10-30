package com.toolpick.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 툴픽 회원 (app_user 테이블). GitHub 로그인으로만 가입하므로 비밀번호를 저장하지 않는다.
 * <p>
 * [CS] 비밀번호를 아예 갖지 않으면 유출될 비밀번호도 없다. 인증은 GitHub에 맡기고(OAuth 위임),
 * 우리는 GitHub이 확인해 준 사용자 id만 저장한다. 클래스 이름이 User가 아닌 AppUser인 것은
 * PostgreSQL에서 user가 예약어라 테이블 이름으로 쓰기 불편하기 때문이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GitHub 사용자 고유 번호. 로그인 이름(login)은 바뀔 수 있어 이것으로 사람을 구분한다 */
    @Column(nullable = false, unique = true)
    private Long githubId;

    @Column(nullable = false, length = 100)
    private String login;

    @Column(length = 300)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static AppUser create(Long githubId, String login, String avatarUrl, Role role) {
        AppUser u = new AppUser();
        u.githubId = githubId;
        u.createdAt = Instant.now();
        u.updateProfile(login, avatarUrl, role);
        return u;
    }

    /** 로그인할 때마다 GitHub 프로필과 관리자 여부를 다시 맞춘다 */
    public void updateProfile(String login, String avatarUrl, Role role) {
        this.login = login;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }
}
