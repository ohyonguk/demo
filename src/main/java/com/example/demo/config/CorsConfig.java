package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed.origins}")
    private String[] allowedOrigins;

    @Value("${cors.allowed.pg.domains}")
    private String[] allowedPgDomains;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // 모든 허용된 도메인을 합쳐서 설정
                String[] allAllowedOrigins = combineOrigins(allowedOrigins, allowedPgDomains);

                registry.addMapping("/**")
                        .allowedOriginPatterns(allAllowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    private String[] combineOrigins(String[] foOrigins, String[] pgOrigins) {
        String[] combined = new String[foOrigins.length + pgOrigins.length];
        System.arraycopy(foOrigins, 0, combined, 0, foOrigins.length);
        System.arraycopy(pgOrigins, 0, combined, foOrigins.length, pgOrigins.length);
        return combined;
    }
}