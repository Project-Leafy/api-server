package com.leafy.plant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.plant.dto.PlantDataDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String openAiApiKey;

    private static final String GPT_MODEL = "gpt-4o";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public Mono<PlantDataDto> getCareInfoForNewPlant(String koreanName, String scientificName) {
        log.info("[OpenAI] 새로운 식물 '{}'({})의 관리 정보 조회를 시작합니다.", koreanName, scientificName);
        
        String systemPrompt = "You are an expert botanist. Based on the provided plant name, return detailed care information as a JSON object. The JSON response must strictly follow the provided format, with all fields present. All descriptive text must be in Korean. Do not include markdown formatting. For numeric cycle fields, provide an integer representing the average number of days.";

        String userPrompt = String.format(
            "Provide care information for the plant: Korean Name = '%s', Scientific Name = '%s'. " +
            "Ensure the output is a valid JSON object with the following fields: " +
            "'koreanName', 'scientificName', 'description', 'flowerFruitInfo', 'lightLux', 'sunlightTip', " +
            "'temp', 'tempTip', 'humidity', 'humidityTip', 'soil', 'waterSpring', 'waterSummer', " +
            "'waterAutumn', 'waterWinter', 'waterTip', 'fertilizerCycleDays', 'fertilizerTip', " +
            "'repottingCycleYears', 'repottingTip', 'growthSpeed', 'displayDifficulty', 'isToxic', " +
            "'toxicityInfo', 'airPurificationKeywords', 'airPurificationInfo', 'checkPointList', 'keywordTags'.",
            koreanName, scientificName
        );

        WebClient client = WebClient.create();

        Map<String, Object> requestBody = Map.of(
            "model", GPT_MODEL,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "response_format", Map.of("type", "json_object"),
            "temperature", 0.2
        );

        return client.post()
            .uri(OPENAI_API_URL)
            .header("Authorization", "Bearer " + openAiApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(responseBody -> {
                try {
                    log.debug("[OpenAI] API 응답 수신: {}", responseBody);
                    JsonNode root = objectMapper.readTree(responseBody);
                    String content = root.path("choices").get(0).path("message").path("content").asText();
                    PlantDataDto dto = objectMapper.readValue(content, PlantDataDto.class);
                    log.info("[OpenAI] '{}' 정보 파싱 성공.", koreanName);
                    return Mono.just(dto);
                } catch (Exception e) {
                    log.error("[OpenAI] '{}' 정보 파싱 중 오류 발생", koreanName, e);
                    return Mono.error(new RuntimeException("Failed to parse OpenAI response for plant: " + koreanName, e));
                }
            })
            .doOnError(e -> log.error("[OpenAI] API 호출 실패: {}", e.getMessage()));
    }
}
