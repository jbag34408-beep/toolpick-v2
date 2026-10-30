package com.toolpick.analysis;

import com.toolpick.common.config.ToolPickProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI를 부르기 전에 레포 이름만 보고 명백한 비도구(awesome 리스트, 튜토리얼, dotfiles 등)를 거른다 (재설계 03).
 * <p>
 * 이름(owner/name의 name)을 -, _, . 로 나눈 단어 중 하나가 목록과 같으면 걸린다.
 * [CS] 부분 문자열로 비교하면 "concourse"(CI 도구)가 "course"에, 설명의 "an awesome CLI"가 "awesome"에 걸린다.
 * 걸린 레포는 판별도 수집도 순위도 다시 받지 못하므로(REJECTED), 오탐을 줄이려고 이름의 단어 단위로만 본다.
 */
@Component
public class RuleFilter {

    private final Set<String> words;

    public RuleFilter(ToolPickProperties props) {
        this.words = props.analysis().rejectNameWords().stream()
                .map(w -> w.strip().toLowerCase(Locale.ROOT))
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 걸린 단어. 안 걸리면 empty */
    public Optional<String> match(String repoName) {
        if (repoName == null || words.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(repoName.toLowerCase(Locale.ROOT).split("[-_.]"))
                .filter(words::contains)
                .findFirst();
    }
}
