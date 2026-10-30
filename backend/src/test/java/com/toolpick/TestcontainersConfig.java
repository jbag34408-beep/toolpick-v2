package com.toolpick;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL을 Docker 컨테이너로 띄운다. 모든 통합 테스트가 같은 컨테이너 하나를 쓴다.
 * <p>
 * [CS] 테스트도 운영과 같은 DB(PostgreSQL 17)로 돌린다. H2 같은 메모리 DB로 대신하면 ON CONFLICT, LATERAL, jsonb 같은
 * PostgreSQL 전용 문법을 검증할 수 없고 "테스트는 통과했는데 운영에서 실패"하는 일이 생긴다.
 * [문법] @ServiceConnection: 컨테이너의 주소·계정을 스프링 datasource 설정에 자동으로 연결한다(application-test.yml의 url은 무시됨).
 * proxyBeanMethods = false: @Bean 메서드끼리 호출할 일이 없어 프록시를 만들지 않는다(조금 빠름).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
