package com.toolpick.collector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.toolpick.TestProps;
import com.toolpick.common.config.ToolPickProperties;
import com.toolpick.common.http.HttpRetry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * GitHubClient 단위 테스트. 진짜 GitHub 대신 WireMock(가짜 HTTP 서버)을 띄워 응답을 마음대로 정한다.
 * <p>
 * [CS] 테스트 더블(test double): 외부 시스템을 흉내 내는 대역. 진짜 API를 부르면 느리고, 한도가 있고, 결과가 매번 달라
 * 테스트가 불안정해진다(flaky). 가짜 서버로는 429·503·깨진 응답처럼 진짜로는 일부러 만들기 어려운 실패 상황도 재현할 수 있다.
 * [문법] @RegisterExtension + WireMockExtension: 테스트 클래스 동안 가짜 서버를 빈 포트(dynamicPort)에 띄우고 끝나면 내린다.
 * stubFor(요청 조건).willReturn(응답): "이런 요청이 오면 이렇게 답하라". verify(n, 요청): 실제로 몇 번 불렸는지 확인한다.
 * 스프링 없이 객체를 직접 new로 만들어 빠르게 돈다.
 */
class GitHubClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private GitHubClient client;

    @BeforeEach
    void setUp() {
        ToolPickProperties props = TestProps.withBaseUrl(wm.baseUrl());
        client = new GitHubClient(RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).baseUrl(wm.baseUrl()).build(), new HttpRetry(props), props);
    }

    @Test
    void searchCandidatesParsesItems() {
        wm.stubFor(get(urlPathEqualTo("/search/repositories")).willReturn(okJson("""
                {"items": [
                  {"id": 1, "full_name": "a/tool", "description": "도구", "language": "Go",
                   "license": {"spdx_id": "MIT", "name": "MIT License"}, "html_url": "https://github.com/a/tool",
                   "pushed_at": "2026-10-20T00:00:00Z", "archived": false, "fork": false, "stargazers_count": 500},
                  {"id": 2, "full_name": "b/nolicense", "description": null, "language": null, "license": null,
                   "html_url": "https://github.com/b/nolicense", "pushed_at": "2026-10-20T00:00:00Z",
                   "archived": false, "fork": false, "stargazers_count": 300}
                ]}""")));

        List<GitHubRepoDto> result = client.searchCandidates(LocalDate.of(2026, 10, 26));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).license()).isEqualTo("MIT");
        assertThat(result.get(0).stars()).isEqualTo(500);
        assertThat(result.get(1).license()).isNull();
        wm.verify(getRequestedFor(urlPathEqualTo("/search/repositories"))
                .withQueryParam("q", equalTo("stars:>=100 created:>=2026-07-28 pushed:>=2026-09-26 archived:false fork:false"))
                .withQueryParam("sort", equalTo("stars")));
    }

    @Test
    void fetchReposSkipsMissingRepos() {
        wm.stubFor(post("/graphql").willReturn(okJson("""
                {"data": {
                  "r0": {"databaseId": 1, "nameWithOwner": "a/tool", "description": "x", "primaryLanguage": {"name": "Go"},
                         "licenseInfo": {"spdxId": "MIT", "name": "MIT"}, "url": "https://github.com/a/tool",
                         "pushedAt": "2026-10-20T00:00:00Z", "isArchived": false, "isFork": false, "stargazerCount": 777},
                  "r1": null
                }}""")));

        List<GitHubRepoDto> result = client.fetchRepos(List.of("a/tool", "gone/repo", "bad\"name/x"));

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.fullName()).isEqualTo("a/tool");
            assertThat(dto.stars()).isEqualTo(777);
            assertThat(dto.language()).isEqualTo("Go");
        });
    }

    @Test
    void fetchReposFailsWhenWholeGraphQlRequestFails() {
        // GraphQL은 한도 초과도 HTTP 200 + errors로 알린다. 0개 수집으로 넘어가지 말고 예외가 나야 한다
        wm.stubFor(post("/graphql").willReturn(okJson("""
                {"data": null, "errors": [{"type": "RATE_LIMITED", "message": "API rate limit exceeded"}]}""")));

        assertThatThrownBy(() -> client.fetchRepos(List.of("a/tool")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API rate limit exceeded");
    }

    @Test
    void fetchReadmeTruncatesAndHandlesMissing() {
        wm.stubFor(get("/repos/a/tool/readme").willReturn(aResponse().withStatus(200).withBody("x".repeat(9000))));
        wm.stubFor(get("/repos/a/none/readme").willReturn(aResponse().withStatus(404)));

        assertThat(client.fetchReadme("a/tool")).hasSize(GitHubClient.README_MAX_CHARS);
        assertThat(client.fetchReadme("a/none")).isNull();
    }

    @Test
    void retriesAfter429ThenSucceeds() {
        wm.stubFor(get("/repos/a/tool/readme").inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429)).willSetStateTo("ok"));
        wm.stubFor(get("/repos/a/tool/readme").inScenario("retry").whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withBody("# README")));

        assertThat(client.fetchReadme("a/tool")).isEqualTo("# README");
        wm.verify(2, getRequestedFor(urlPathEqualTo("/repos/a/tool/readme")));
    }

    @Test
    void throwsAfterThreeRetries() {
        wm.stubFor(get("/repos/a/tool/readme").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.fetchReadme("a/tool")).isInstanceOf(HttpServerErrorException.class);
        // 첫 호출 1번 + 재시도 3번
        wm.verify(4, getRequestedFor(urlPathEqualTo("/repos/a/tool/readme")));
    }
}
