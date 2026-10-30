package com.toolpick.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 웹 프로세스의 메모리 캐시.
 * [CS] v2에서는 배치가 다른 프로세스라 웹 캐시를 직접 비울 수 없다(v1의 evictAll 불가).
 * 그래서 순위 캐시는 짧은 TTL(10분)로 스스로 갱신되게 한다. 순위 조회는 인덱스 쿼리 하나라 DB 부담이 작다.
 * 분야 목록은 거의 안 바뀌므로 1시간.
 */
@EnableCaching
@Configuration
public class CacheConfig {

    public static final String RANKINGS = "rankings";
    public static final String CATEGORIES = "categories";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(RANKINGS,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(500).build());
        manager.registerCustomCache(CATEGORIES,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10).build());
        return manager;
    }
}
