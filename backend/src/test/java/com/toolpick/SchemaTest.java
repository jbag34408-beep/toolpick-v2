package com.toolpick;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Flyway 마이그레이션 결과 검증: 테이블 8개와 기본 카테고리 8개가 만들어지는지.
 * [CS] 스모크 테스트(smoke test): 가장 기본적인 것(앱이 뜨고 스키마가 맞는가)만 빠르게 확인한다.
 * 스프링 컨텍스트가 뜨는 순간 Hibernate validate도 함께 돌아 엔티티와 테이블 정의가 어긋나면 여기서 실패한다.
 */
class SchemaTest extends IntegrationTest {

    @Test
    void flywayCreatesTablesAndCategories() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'""", Integer.class);
        Integer categories = jdbc.queryForObject("SELECT count(*) FROM category", Integer.class);

        assertThat(tables).isEqualTo(8);
        assertThat(categories).isEqualTo(8);
    }
}
