package com.leafy.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${plant.id.api-key}")
    private String plantIdApiKey;

    @Value("${plant.id.base-url}")
    private String plantIdBaseUrl;

    /**
     * 1. 범용 WebClient (농사로 API용)
     * - XML 설정 제거 (서비스에서 수동 파싱함)
     * - 메모리 버퍼만 넉넉하게 설정
     */
    @Bean
    @Primary
    public WebClient webClient() {
        // 대용량 데이터 처리를 위해 메모리 버퍼 크기만 늘림
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024)) // 20MB
                .build();

        return WebClient.builder()
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * 2. Plant.id 전용 WebClient
     */
    @Bean
    public WebClient plantIdWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(plantIdBaseUrl)
                .defaultHeader("Api-Key", plantIdApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}