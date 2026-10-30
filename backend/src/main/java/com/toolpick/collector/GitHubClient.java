package com.toolpick.collector;

import tools.jackson.databind.JsonNode;
import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.common.http.HttpRetry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * GitHub API 호출을 모아 둔 클래스. 검색(REST), 묶음 조회(GraphQL), README(REST) 세 가지를 한다.
 * <p>
 * [CS] REST vs GraphQL
 * <ul>
 *   <li>REST: 자원마다 URL이 있고(/repos/a/b), 정해진 모양의 응답을 준다. 레포 300개를 조회하려면 300번 요청해야 한다.</li>
 *   <li>GraphQL: 한 요청 안에 "무엇을 얼마나" 원하는지 질의문으로 적는다. 레포 100개를 한 번에 묻고 필요한 필드만 받는다.
 *       요청 수가 1/100로 줄어 한도(rate limit)를 아끼고 네트워크 왕복(round trip)도 줄어든다.</li>
 * </ul>
 * [문법] JsonNode(Jackson)는 JSON을 트리 구조로 다룬다. path("a")는 키가 없어도 null 대신 "없음(MissingNode)"을
 * 돌려주므로 중간에 NullPointerException 없이 path("a").path("b")처럼 안전하게 파고들 수 있다.
 */
@Slf4j
@Component
public class GitHubClient {

    static final int PAGE_SIZE = 100;
    static final int README_MAX_CHARS = 4_000; // 숫자 사이 _는 읽기 쉽게 끊는 표기. AI 무료 토큰 한도에 맞춰 설치 방법이 있는 앞부분만
    /**
     * GraphQL 쿼리에 문자열을 그대로 넣으므로 GitHub 이름 규칙에 맞는 것만 허용한다.
     * [CS] 인젝션(injection) 방어: 사용자·외부 입력을 쿼리 문자열에 이어 붙이면, 입력에 " 같은 문자가 섞였을 때
     * 쿼리 구조 자체가 바뀔 수 있다. 허용 문자 목록(allowlist) 정규식으로 미리 걸러 낸다.
     * Pattern.compile은 정규식을 한 번만 컴파일해 재사용한다(static final 상수).
     */
    private static final Pattern FULL_NAME = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");

    private final RestClient restClient;
    private final HttpRetry retry;
    private final ToolPickProperties.Candidate candidate;

    // @Qualifier: RestClient 빈이 두 개(GitHub용, Gemini용)라 이름으로 골라 받는다
    public GitHubClient(@Qualifier("gitHubRestClient") RestClient restClient, HttpRetry retry, ToolPickProperties props) {
        this.restClient = restClient;
        this.retry = retry;
        this.candidate = props.candidate();
    }

    /**
     * 최근 생겨서 활발하고 스타가 붙는 레포를 스타 순으로 최대 max-per-day개 찾는다.
     * [CS] 페이지네이션(pagination): 결과가 많으면 서버는 한 번에 일부(100개)만 주고 page 번호로 다음을 받게 한다.
     * 받은 개수가 페이지 크기보다 적으면 마지막 페이지이므로 더 요청하지 않는다.
     */
    public List<GitHubRepoDto> searchCandidates(LocalDate today) {
        // String.formatted: %d(정수), %s(문자열) 자리에 값을 채운다. LocalDate는 "2026-07-28" 형식으로 들어간다
        String query = "stars:>=%d created:>=%s pushed:>=%s archived:false fork:false".formatted(
                candidate.minStars(),
                today.minusDays(candidate.createdWithinDays()),
                today.minusDays(candidate.pushedWithinDays()));
        // 올림 나눗셈: (a + b - 1) / b. 300개면 3페이지, 250개여도 3페이지
        int pages = (candidate.maxPerDay() + PAGE_SIZE - 1) / PAGE_SIZE;
        List<GitHubRepoDto> result = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            // 람다 안에서는 바뀌지 않는 변수만 쓸 수 있어서 반복 변수 page를 p로 복사한다
            int p = page;
            JsonNode body = withRetry(() -> restClient.get()
                    // uriBuilder가 쿼리 문자열의 공백·특수문자를 URL 인코딩한다(예: 공백 → %20)
                    .uri(u -> u.path("/search/repositories")
                            .queryParam("q", query)
                            .queryParam("sort", "stars")
                            .queryParam("order", "desc")
                            .queryParam("per_page", PAGE_SIZE)
                            .queryParam("page", p)
                            .build())
                    .retrieve() // 4xx·5xx면 예외(HttpClientErrorException 등)를 던진다
                    .body(JsonNode.class));
            JsonNode items = body.path("items");
            items.forEach(item -> result.add(fromRest(item)));
            if (items.size() < PAGE_SIZE) {
                break;
            }
        }
        return result.size() > candidate.maxPerDay() ? result.subList(0, candidate.maxPerDay()) : result;
    }

    /**
     * GraphQL로 100개씩 묶어 조회한다. 삭제·비공개된 레포는 결과에서 빠진다.
     * [CS] 배치 처리(batching): 큰 목록을 일정 크기 묶음(chunk)으로 나눠 처리한다. 한 요청이 너무 커지지 않게 하면서
     * 요청 수는 줄인다. GitHub GraphQL은 한 쿼리에 넣을 수 있는 노드 수에 제한이 있어 100개 단위로 끊는다.
     */
    public List<GitHubRepoDto> fetchRepos(List<String> fullNames) {
        List<String> valid = fullNames.stream().filter(n -> FULL_NAME.matcher(n).matches()).toList();
        List<GitHubRepoDto> result = new ArrayList<>();
        for (int from = 0; from < valid.size(); from += PAGE_SIZE) {
            // subList: 원본 목록의 [from, to) 구간을 복사 없이 보여주는 뷰(view)
            List<String> chunk = valid.subList(from, Math.min(from + PAGE_SIZE, valid.size()));
            String query = buildGraphQl(chunk);
            JsonNode body = withRetry(() -> restClient.post()
                    .uri("/graphql")
                    .body(Map.of("query", query)) // Map이 {"query": "..."} JSON으로 직렬화된다
                    .retrieve()
                    .body(JsonNode.class));
            JsonNode data = body.path("data");
            // GraphQL은 REST와 달리 실패해도 HTTP 200을 주고 본문의 errors 배열로 알린다.
            // - data가 있고 errors도 있음: 부분 실패(partial success). 일부 별칭(rN)만 null(삭제된 레포 등)이라 정상 처리한다.
            // - data가 null이고 errors만 있음: 요청 전체 실패(한도 초과 RATE_LIMITED, 쿼리 오류 등).
            //   이걸 "레포가 없음"으로 넘기면 조용히 0개 수집 후 SUCCESS가 되므로(silent failure),
            //   예외로 바꿔 배치를 FAILED로 남긴다(fail-fast: 문제를 숨기지 않고 즉시 드러냄).
            if (data.isMissingNode() || data.isNull()) {
                JsonNode errors = body.path("errors");
                String message = errors.path(0).path("message").asString("data와 errors가 모두 없음");
                throw new IllegalStateException("GitHub GraphQL 요청 실패: " + message);
            }
            for (int i = 0; i < chunk.size(); i++) {
                JsonNode node = data.path("r" + i);
                if (node.isMissingNode() || node.isNull()) {
                    log.debug("GitHub에서 찾을 수 없는 레포: {}", chunk.get(i));
                    continue;
                }
                result.add(fromGraphQl(node));
            }
        }
        return result;
    }

    /** README 원문 앞부분 최대 4,000자. 없으면 null */
    public String fetchReadme(String fullName) {
        // {owner}/{name}을 따로 넣는다. {fullName} 하나로 넣으면 URI 템플릿이 '/'를 %2F로 인코딩해 경로가 틀어진다
        String[] parts = fullName.split("/", 2);
        try {
            String text = withRetry(() -> restClient.get()
                    .uri("/repos/{owner}/{name}/readme", parts[0], parts[1])
                    // [CS] 콘텐츠 협상(content negotiation): Accept 헤더로 원하는 응답 형식을 고른다.
                    // raw를 고르면 Base64로 감싼 JSON 대신 README 원문 텍스트를 바로 받는다
                    .header("Accept", "application/vnd.github.raw+json")
                    .retrieve()
                    .body(String.class));
            // AI 입력 길이를 제한한다: 토큰(=비용·처리 시간)을 아끼고 모델 입력 한도를 넘지 않게
            return text == null || text.length() <= README_MAX_CHARS ? text : text.substring(0, README_MAX_CHARS);
        } catch (HttpClientErrorException e) {
            // 404는 "README가 없음"이라는 정상적인 결과라 null로 바꾼다. 다른 4xx는 호출한 쪽이 판단하게 다시 던진다
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private <T> T withRetry(Supplier<T> call) {
        return retry.call(call);
    }

    /**
     * 별칭(alias)으로 여러 레포를 한 쿼리에 묻는다:
     * query { r0: repository(owner: "a", name: "b") { ... } r1: repository(...) { ... } }
     * 응답도 data.r0, data.r1 로 와서 순서대로 짝지을 수 있다.
     * [문법] StringBuilder: 문자열을 반복해서 이어 붙일 때 쓴다. String + 를 반복하면 매번 새 문자열을 만들어 느리다.
     */
    private static String buildGraphQl(List<String> fullNames) {
        StringBuilder sb = new StringBuilder("query {");
        for (int i = 0; i < fullNames.size(); i++) {
            String[] parts = fullNames.get(i).split("/", 2);
            sb.append(" r").append(i)
                    .append(": repository(owner: \"").append(parts[0]).append("\", name: \"").append(parts[1]).append("\") {")
                    .append(" databaseId nameWithOwner description primaryLanguage { name } licenseInfo { spdxId name }")
                    .append(" url pushedAt isArchived isFork stargazerCount }");
        }
        return sb.append(" }").toString();
    }

    /** REST 검색 결과 한 항목 → DTO */
    private static GitHubRepoDto fromRest(JsonNode n) {
        JsonNode license = n.path("license");
        return new GitHubRepoDto(
                n.path("id").asLong(),
                n.path("full_name").asString(),
                text(n.path("description")),
                text(n.path("language")),
                license.isObject() ? licenseName(text(license.path("spdx_id")), text(license.path("name"))) : null,
                n.path("html_url").asString(),
                instant(n.path("pushed_at")),
                n.path("archived").asBoolean(),
                n.path("fork").asBoolean(),
                n.path("stargazers_count").asInt());
    }

    /** GraphQL 결과 한 노드 → DTO (같은 정보인데 필드 이름이 REST와 다르다) */
    private static GitHubRepoDto fromGraphQl(JsonNode n) {
        JsonNode license = n.path("licenseInfo");
        return new GitHubRepoDto(
                n.path("databaseId").asLong(),
                n.path("nameWithOwner").asString(),
                text(n.path("description")),
                text(n.path("primaryLanguage").path("name")),
                license.isObject() ? licenseName(text(license.path("spdxId")), text(license.path("name"))) : null,
                n.path("url").asString(),
                instant(n.path("pushedAt")),
                n.path("isArchived").asBoolean(),
                n.path("isFork").asBoolean(),
                n.path("stargazerCount").asInt());
    }

    /** "NOASSERTION"은 GitHub이 라이선스를 알아보지 못한 경우라 이름을 쓴다 */
    private static String licenseName(String spdxId, String name) {
        return spdxId != null && !"NOASSERTION".equals(spdxId) ? spdxId : name;
    }

    /** JSON null·누락을 자바 null로 통일한다 (asString()은 null을 빈 문자열 ""로 바꿔 버리기 때문) */
    private static String text(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? null : n.asString();
    }

    /** "2026-10-20T00:00:00Z" 같은 ISO-8601 문자열 → Instant */
    private static Instant instant(JsonNode n) {
        String s = text(n);
        return s == null ? null : Instant.parse(s);
    }
}
