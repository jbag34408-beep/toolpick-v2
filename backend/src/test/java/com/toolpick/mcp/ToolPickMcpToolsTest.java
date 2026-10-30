package com.toolpick.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.toolpick.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MCP 도구 등록·호출 검증: 도구 3개가 이름대로 등록되고, JSON 인자로 호출하면 서비스 데이터가 JSON으로 나오는지.
 * [CS] AI 클라이언트가 실제로 하는 것(tools/list로 목록 받기 → tools/call로 JSON 인자 넣어 호출)을 같은 경로로 흉내 낸다.
 */
class ToolPickMcpToolsTest extends IntegrationTest {

    @Autowired
    ToolCallbackProvider toolPickTools;

    @Autowired
    com.toolpick.ranking.RankingCalculator calculator;

    @Test
    void registersThreeTools() {
        assertThat(Arrays.stream(toolPickTools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .containsExactlyInAnyOrder("getTopTools", "getToolDetail", "listCategories");
    }

    @Test
    void toolsReturnServiceData() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        long id = insertRepo("cli/tool", today + "T00:00:00Z");
        insertAnalysis(id, "dev-tools", "PUBLISHED", true);
        insertSnapshot(id, today.minusDays(7).toString(), 10);
        insertSnapshot(id, today.toString(), 30);
        calculator.calculate(today);

        assertThat(call("getTopTools", "{\"category\": \"dev-tools\"}")).contains("cli/tool").contains("\"starGain\":20");
        assertThat(call("getTopTools", "{}")).contains("ai-agent").contains("cli/tool");
        assertThat(call("getToolDetail", "{\"fullName\": \"cli/tool\"}")).contains("npm i x").contains("starHistory30d");
        assertThat(call("listCategories", "{}")).contains("productivity");
    }

    private String call(String name, String json) {
        ToolCallback tool = Arrays.stream(toolPickTools.getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals(name)).findFirst().orElseThrow();
        return tool.call(json);
    }
}
