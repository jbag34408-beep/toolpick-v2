package com.toolpick.repo;

import com.toolpick.analysis.AnalysisStatus;
import com.toolpick.analysis.RepoAnalysis;
import com.toolpick.analysis.RepoAnalysisRepository;
import com.toolpick.common.exception.NotFoundException;
import com.toolpick.repo.dto.RepoDetailResponse;
import com.toolpick.repo.dto.StarPointResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 도구 상세 화면·MCP용. 공개(PUBLISHED)된 레포만 보여 준다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepoService {

    static final int MAX_DAYS = 90;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RepoRepository repoRepository;
    private final RepoAnalysisRepository analysisRepository;
    private final StarSnapshotRepository snapshotRepository;

    public RepoDetailResponse getDetail(String owner, String name) {
        Repo repo = findPublished(owner, name);
        RepoAnalysis a = analysisRepository.findByRepoId(repo.getId()).orElseThrow();
        Integer stars = snapshotRepository.findFirstByRepoIdOrderBySnapshotDateDesc(repo.getId())
                .map(StarSnapshot::getStars).orElse(null);
        return new RepoDetailResponse(repo.getId(), repo.getFullName(), repo.getDescription(), repo.getLanguage(),
                repo.getLicense(), repo.getHtmlUrl(), stars,
                a.getCategory() == null ? null : a.getCategory().getSlug(),
                a.getCategory() == null ? null : a.getCategory().getNameKo(),
                a.getSummaryKo(), a.getInstallCmd(), repo.getLastPushedAt());
    }

    public List<StarPointResponse> getStarHistory(String owner, String name, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("days는 1~" + MAX_DAYS + " 사이여야 합니다");
        }
        Repo repo = findPublished(owner, name);
        LocalDate from = LocalDate.now(KST).minusDays(days);
        return snapshotRepository.findHistory(repo.getId(), from).stream()
                .map(s -> new StarPointResponse(s.getSnapshotDate(), s.getStars()))
                .toList();
    }

    private Repo findPublished(String owner, String name) {
        String fullName = owner + "/" + name;
        return repoRepository.findByFullName(fullName)
                .filter(r -> analysisRepository.findByRepoId(r.getId())
                        .map(a -> a.getStatus() == AnalysisStatus.PUBLISHED).orElse(false))
                .orElseThrow(() -> new NotFoundException("도구를 찾을 수 없습니다: " + fullName));
    }
}
