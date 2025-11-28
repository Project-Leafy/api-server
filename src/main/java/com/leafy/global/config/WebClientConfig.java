// 경로: api-server/src/main/java/com/leafy/global/config/WebClientConfig.java
package com.leafy.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 외부 API 통신을 위한 WebClient 설정
 */
@Configuration
public class WebClientConfig {

    @Value("${plant.id.api-key}")
    private String plantIdApiKey;

    @Value("${plant.id.base-url}")
    private String plantIdBaseUrl;

    @Bean
    public WebClient plantIdWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(plantIdBaseUrl)
                .defaultHeader("Api-Key", plantIdApiKey)
                // S3(URL) 방식은 application/json을 사용
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}