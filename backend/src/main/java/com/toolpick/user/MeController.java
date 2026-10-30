package com.toolpick.user;

import com.toolpick.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/me — 내 정보. 로그인 안 했으면 SecurityConfig가 컨트롤러에 닿기 전에 401을 준다.
 * <p>
 * [문법] @AuthenticationPrincipal: 세션에 저장된 "현재 로그인한 사용자" 객체를 인자로 바로 받는다.
 * [CS] 클라이언트가 보낸 사용자 id를 믿지 않고, 서버 세션에 있는 신원으로만 판단한다(신원 위조 방지).
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final AppUserRepository userRepository;

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal OAuth2User principal) {
        return userRepository.findByGithubId(CurrentUser.githubId(principal))
                .map(MeResponse::from)
                .orElseThrow(() -> new NotFoundException("사용자가 없습니다"));
    }
}
