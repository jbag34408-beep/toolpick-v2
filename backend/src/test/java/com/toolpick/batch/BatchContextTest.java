package com.toolpick.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.toolpick.TestcontainersConfig;
import com.toolpick.analysis.AiAnalyzer;
import com.toolpick.collector.GitHubClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

/**
 * batch 프로필로 앱이 웹 서버 없이 뜨는지 (docs/design/v2-00-backend.md 4.1).
 * 보안 설정(HttpSecurity가 필요)이나 MCP 서버가 웹이 아닌 컨텍스트에서 만들어지면 여기서 깨진다.
 * batch 프로필은 Flyway를 끄므로, 빈 테스트 DB에 테이블을 만들려고 이 테스트에서만 켠다.
 */
@SpringBootTest(properties = "spring.flyway.enabled=true")
@ActiveProfiles({"test", "batch"})
@Import(TestcontainersConfig.class)
class BatchContextTest {

    @MockitoBean
    GitHubClient gitHubClient;

    @MockitoBean
    AiAnalyzer aiAnalyzer;

    @Autowired
    ApplicationContext context;

    @Test
    void startsWithoutWebServerSecurityOrMcp() throws Exception {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DailyJob.class)).hasSize(1);
        // MCP 서버 본체(spring.ai.mcp.server.enabled=false). 도구 정의 빈(toolPickMcpTools)은 남아도 아무 일도 하지 않는다
        assertThat(context.getBeanNamesForType(Class.forName("io.modelcontextprotocol.server.McpSyncServer"))).isEmpty();
        // run-on-startup=false(application-test.yml)라 배치가 저절로 돌지 않는다
        assertThat(context.getBeanNamesForType(BatchJobRunner.class)).isEmpty();
    }
}
