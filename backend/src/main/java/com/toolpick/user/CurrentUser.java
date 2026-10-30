package com.toolpick.user;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 로그인 사용자 객체(OAuth2User)에서 GitHub id를 꺼내는 유틸.
 * <p>
 * [문법] final 클래스 + private 생성자: 상속도, 객체 생성도 막는다. static 메서드만 모아 둔 유틸리티 클래스의 관용구다.
 * [문법] instanceof 패턴 매칭(Java 16+): id instanceof Number n 은 "Number면 n이라는 이름으로 형 변환해서 쓴다"는 뜻이다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long githubId(OAuth2User principal) {
        Object id = principal.getAttribute("id");
        if (!(id instanceof Number n)) {
            throw new IllegalStateException("로그인 정보에 GitHub id가 없습니다");
        }
        return n.longValue();
    }
}
