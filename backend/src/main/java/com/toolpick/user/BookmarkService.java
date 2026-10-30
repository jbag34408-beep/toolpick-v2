package com.toolpick.user;

import com.toolpick.analysis.RepoAnalysis;
import com.toolpick.analysis.RepoAnalysisRepository;
import com.toolpick.common.exception.NotFoundException;
import com.toolpick.repo.Repo;
import com.toolpick.repo.RepoRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크 업무 로직.
 * <p>
 * [문법] 클래스에 @Transactional(쓰기), 조회 메서드에만 @Transactional(readOnly = true)를 덮어쓴다.
 * 메서드에 붙은 설정이 클래스 설정보다 우선한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final AppUserRepository userRepository;
    private final RepoRepository repoRepository;
    private final RepoAnalysisRepository analysisRepository;

    @Transactional(readOnly = true)
    public List<BookmarkResponse> list(Long githubId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdOrderByCreatedAtDesc(user(githubId).getId());
        // 북마크한 레포들의 요약을 IN 쿼리 한 번으로 가져온다(N+1 방지)
        Map<Long, RepoAnalysis> analyses = analysisRepository
                .findByRepoIdIn(bookmarks.stream().map(b -> b.getRepo().getId()).toList()).stream()
                .collect(Collectors.toMap(a -> a.getRepo().getId(), Function.identity()));
        return bookmarks.stream().map(b -> {
            Repo r = b.getRepo();
            RepoAnalysis a = analyses.get(r.getId());
            return new BookmarkResponse(r.getId(), r.getFullName(), a == null ? null : a.getSummaryKo(),
                    r.getLanguage(), r.getHtmlUrl(), b.getCreatedAt());
        }).toList();
    }

    /**
     * @return 새로 만들었으면 true, 이미 있었으면 false
     * [CS] 이미 있으면 오류 대신 성공(200)으로 답한다. 버튼을 두 번 눌러도 결과가 같아 사용자 경험이 자연스럽다(멱등한 동작).
     */
    public boolean add(Long githubId, Long repoId) {
        AppUser user = user(githubId);
        if (bookmarkRepository.existsByUserIdAndRepoId(user.getId(), repoId)) {
            return false;
        }
        Repo repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("레포가 없습니다: " + repoId));
        bookmarkRepository.save(Bookmark.of(user, repo));
        return true;
    }

    /** 없는 북마크를 지워도 오류 없이 끝난다(DELETE의 멱등성) */
    public void remove(Long githubId, Long repoId) {
        bookmarkRepository.deleteByUserIdAndRepoId(user(githubId).getId(), repoId);
    }

    private AppUser user(Long githubId) {
        return userRepository.findByGithubId(githubId).orElseThrow(() -> new NotFoundException("사용자가 없습니다"));
    }
}
