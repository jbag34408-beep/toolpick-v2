package com.toolpick.user;

/** 로그인한 내 정보. 프론트는 role로 관리자 메뉴를 보여줄지 정한다 (실제 차단은 서버가 한다) */
public record MeResponse(String login, String avatarUrl, Role role) {

    public static MeResponse from(AppUser u) {
        return new MeResponse(u.getLogin(), u.getAvatarUrl(), u.getRole());
    }
}
