package com.toolpick.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 서버에 도구를 등록한다.
 * <p>
 * [문법] MethodToolCallbackProvider가 객체의 @Tool 메서드를 리플렉션(reflection, 실행 중에 클래스 구조를 읽는 기능)으로 찾아
 * 도구 목록(ToolCallback)을 만든다. 이 빈이 있으면 Spring AI MCP 서버 자동 설정이 이를 가져다 POST /mcp 로 공개한다.
 */
@Configuration
public class McpConfig {

    @Bean
    ToolCallbackProvider toolPickTools(ToolPickMcpTools toolPickMcpTools) {
        return MethodToolCallbackProvider.builder().toolObjects(toolPickMcpTools).build();
    }
}
