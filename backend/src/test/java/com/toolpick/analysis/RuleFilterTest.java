package com.toolpick.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.toolpick.TestProps;
import org.junit.jupiter.api.Test;

/** 재설계 03: 이름의 단어 단위로만 거른다 (부분 문자열 오탐 방지) */
class RuleFilterTest {

    private final RuleFilter filter = new RuleFilter(TestProps.withBaseUrl("http://unused"));

    @Test
    void matchesWholeWordsOfRepoName() {
        assertThat(filter.match("awesome-go")).contains("awesome");
        assertThat(filter.match("Awesome_Python")).contains("awesome"); // 대소문자 무시
        assertThat(filter.match("dotfiles")).contains("dotfiles");
        assertThat(filter.match("react.tutorial")).contains("tutorial");
        assertThat(filter.match("developer-roadmap")).contains("roadmap");
    }

    @Test
    void doesNotMatchSubstrings() {
        // 부분 문자열 비교였다면 concourse(CI 도구)가 걸렸을 것이다
        assertThat(filter.match("concourse")).isEmpty();
        assertThat(filter.match("awesomeness")).isEmpty();
        assertThat(filter.match("next.js")).isEmpty();
        assertThat(filter.match(null)).isEmpty();
    }
}
