package com.toolpick.collector;

import java.time.Instant;

/**
 * GitHub API 응답에서 필요한 값만 뽑은 DTO.
 * <p>
 * [CS] 외부 API 응답 형식(REST의 full_name, GraphQL의 nameWithOwner처럼 서로 다름)을 우리 쪽 공통 형식 하나로 바꿔 둔다.
 * 외부 형식이 바뀌어도 GitHubClient 안의 변환 코드만 고치면 되고, 나머지 코드는 영향을 받지 않는다
 * (부패 방지 계층, anti-corruption layer).
 */
public record GitHubRepoDto(
        long githubId,
        String fullName,
        String description,
        String language,
        String license,
        String htmlUrl,
        Instant pushedAt,
        boolean archived,
        boolean fork,
        int stars) {
}
