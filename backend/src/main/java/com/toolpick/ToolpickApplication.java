package com.toolpick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

/**
 * 툴픽 v2 진입점. 같은 jar를 두 가지로 띄운다(docs/design/v2-00-backend.md 4.1).
 * <ul>
 *   <li>기본 프로필: 웹 API·로그인·MCP. 요청을 계속 받는다.</li>
 *   <li>batch 프로필: 웹 서버 없이 오늘 배치를 한 번 실행하고(batch.BatchJobRunner), 결과를 종료 코드로 남기고 끝난다.</li>
 * </ul>
 * v1의 @EnableAsync는 없앴다. 배치가 웹 안의 스레드가 아니라 별도 프로세스로 돌기 때문이다.
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class ToolpickApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(ToolpickApplication.class, args);
        if (ctx.getEnvironment().acceptsProfiles(Profiles.of("batch"))) {
            // ExitCodeGenerator(BatchJobRunner)의 값으로 컨텍스트를 닫고 프로세스를 끝낸다
            System.exit(SpringApplication.exit(ctx));
        }
    }
}
