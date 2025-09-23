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
                // 21: 서버의 CORS 는 FO 도메인과 PG 사 도메인만을 허용합니다.
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

    /**
     * 21: 서버의 CORS 는 FO 도메인과 PG 사 도메인만을 허용합니다.
     *
     * FO(Front-Office) 도메인과 PG(Payment Gateway) 도메인을 결합하여
     * CORS 허용 도메인 목록을 생성합니다.
     *
     * @param foOrigins FO 도메인 배열 (프론트엔드 도메인)
     * @param pgOrigins PG 도메인 배열 (결제 게이트웨이 도메인)
     * @return 결합된 허용 도메인 배열
     */
    private String[] combineOrigins(String[] foOrigins, String[] pgOrigins) {
        String[] combined = new String[foOrigins.length + pgOrigins.length];
        System.arraycopy(foOrigins, 0, combined, 0, foOrigins.length);
        System.arraycopy(pgOrigins, 0, combined, foOrigins.length, pgOrigins.length);
        return combined;
    }
}