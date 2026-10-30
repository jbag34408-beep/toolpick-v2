package com.toolpick.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 프론트 출처 하나만 허용한다. 브라우저는 보통 Vercel rewrite로 같은 출처 요청을 하므로 CORS가 필요 없지만,
 * 로컬 개발이나 직접 호출에서 쿠키를 보내려면 정확한 출처를 적어야 한다(allowCredentials와 * 는 함께 못 씀).
 */
@Configuration
@ConditionalOnWebApplication
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(ToolPickProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(props.frontendOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
