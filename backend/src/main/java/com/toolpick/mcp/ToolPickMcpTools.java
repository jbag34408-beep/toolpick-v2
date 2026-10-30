package com.toolpick.mcp;

import com.toolpick.category.CategoryResponse;
import com.toolpick.category.CategoryService;
import com.toolpick.ranking.RankingService;
import com.toolpick.ranking.dto.CategoryRankingResponse;
import com.toolpick.repo.RepoService;
import com.toolpick.repo.dto.RepoDetailResponse;
import com.toolpick.repo.dto.StarPointResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP(Model Context Protocol) 도구. Claude·Cursor 같은 AI가 툴픽 순위를 직접 조회할 수 있게 한다.
 * <p>
 * [CS] MCP는 AI 모델이 외부 기능(도구)을 부르는 표준 프로토콜이다. 내부적으로는 JSON-RPC 2.0 메시지
 * (initialize → tools/list → tools/call)를 HTTP로 주고받는다. AI는 tools/list로 받은 도구 이름·설명·파라미터를 보고
 * 언제 어떤 도구를 부를지 스스로 판단하므로, description을 사람이 아닌 AI가 읽는다고 생각하고 구체적으로 쓴다.
 * <p>
 * [문법] @Tool이 붙은 메서드를 Spring AI가 찾아 MCP 도구로 공개한다(McpConfig). 파라미터 타입·@ToolParam 설명으로
 * JSON 스키마가 자동 생성되고, 반환값(record)은 JSON으로 바뀌어 AI에게 전달된다.
 * <p>
 * 화면용 API와 같은 서비스를 그대로 호출한다 (로직을 따로 두지 않는다). 규칙이 한 곳에만 있어 화면과 AI의 답이 항상 같다.
 */
@Component
@RequiredArgsConstructor
public class ToolPickMcpTools {

    private final RankingService rankingService;
    private final RepoService repoService;
    private final CategoryService categoryService;

    /** [문법] 클래스 안에 선언한 record는 이 도구 전용 응답 모양이다(중첩 타입) */
    public record ToolDetail(RepoDetailResponse tool, List<StarPointResponse> starHistory30d) {}

    @Tool(description = """
            오늘 카테고리별로 쓸 만한 깃허브 도구 Top 10 (최근 7일 스타 증가량 순, AI가 바로 설치해 쓸 수 있는 도구만 고름).
            Today's top 10 practical GitHub tools per category, ranked by 7-day star gain. \
            Leave category empty to get every category.""")
    public List<CategoryRankingResponse> getTopTools(
            @ToolParam(required = false, description = "카테고리 slug (listCategories 참고). 비우면 전체 / category slug, empty for all")
            String category) {
        if (category == null || category.isBlank()) {
            return rankingService.getRankings(null);
        }
        return List.of(rankingService.getCategoryRanking(category.strip(), null));
    }

    @Tool(description = """
            도구의 한국어 요약, 설치 명령, 최근 30일 스타 추이.
            Korean summary, install command and the last 30 days of star history for a tool.""")
    public ToolDetail getToolDetail(
            @ToolParam(description = "owner/name 형식의 레포 이름 / repository full name, e.g. vercel/next.js") String fullName) {
        // AI가 보낸 값도 외부 입력이라 형식을 검사한다. 틀리면 예외 메시지가 AI에게 오류 결과로 전달된다
        String[] parts = fullName == null ? new String[0] : fullName.strip().split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("fullName은 owner/name 형식이어야 합니다: " + fullName);
        }
        return new ToolDetail(repoService.getDetail(parts[0], parts[1]),
                repoService.getStarHistory(parts[0], parts[1], 30));
    }

    @Tool(description = "카테고리 목록과 slug / List of categories and their slugs.")
    public List<CategoryResponse> listCategories() {
        return categoryService.getAll();
    }
}
