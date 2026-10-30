package com.toolpick.user;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 북마크 API. GET 목록, POST 추가, DELETE 삭제.
 * <p>
 * [CS] HTTP 메서드와 상태 코드의 의미
 * <ul>
 *   <li>201 Created: 새 자원을 만들었다 / 200 OK: 이미 있어서 그대로 / 204 No Content: 성공했고 돌려줄 본문은 없다</li>
 *   <li>POST·DELETE는 서버 상태를 바꾸므로 CSRF 토큰 헤더가 있어야 한다(SecurityConfig)</li>
 * </ul>
 * 누구의 북마크인지는 URL이 아니라 로그인 세션(principal)으로 정한다 → 남의 북마크를 고칠 수 없다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping
    public List<BookmarkResponse> list(@AuthenticationPrincipal OAuth2User principal) {
        return bookmarkService.list(CurrentUser.githubId(principal));
    }

    @PostMapping("/{repoId}")
    public ResponseEntity<Void> add(@AuthenticationPrincipal OAuth2User principal, @PathVariable Long repoId) {
        boolean created = bookmarkService.add(CurrentUser.githubId(principal), repoId);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    @DeleteMapping("/{repoId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal OAuth2User principal, @PathVariable Long repoId) {
        bookmarkService.remove(CurrentUser.githubId(principal), repoId);
        return ResponseEntity.noContent().build();
    }
}
