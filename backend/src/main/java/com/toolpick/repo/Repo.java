package com.toolpick.repo;

import com.toolpick.collector.GitHubRepoDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 추적하는 깃허브 레포.
 * [CS] 정체성은 githubId다. full_name은 이름 변경·이전으로 바뀔 수 있어서, 새 레포가 옛 이름을 가져가면
 * 옛 행의 이름 뒤에 "#githubId"를 붙여 비켜 둔다(releaseName). 이런 행은 GitHub에 없으므로 수집·판별에서 뺀다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Repo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long githubId;

    @Column(nullable = false, unique = true, length = 200)
    private String fullName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 50)
    private String language;

    @Column(length = 100)
    private String license;

    @Column(nullable = false, length = 300)
    private String htmlUrl;

    private Instant lastPushedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Repo from(GitHubRepoDto dto) {
        Repo repo = new Repo();
        repo.githubId = dto.githubId();
        repo.createdAt = Instant.now();
        repo.updateFrom(dto);
        return repo;
    }

    public void updateFrom(GitHubRepoDto dto) {
        this.fullName = dto.fullName();
        this.description = dto.description();
        this.language = truncate(dto.language(), 50);
        this.license = truncate(dto.license(), 100);
        this.htmlUrl = dto.htmlUrl();
        this.lastPushedAt = dto.pushedAt();
    }

    public void releaseName() {
        this.fullName = this.fullName + "#" + this.githubId;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
