// 경로: api-server/src/main/java/com/leafy/diagnosis/dto/PlantIdResponseDto.java
package com.leafy.diagnosis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal; // DiagnosisHistory의 BigDecimal 타입과 맞추기 위해 import
import java.util.List;

/**
 * plant.id API 응답을 매핑하기 위한 DTO (Data Transfer Object)
 * record (Java 16+)를 사용하여 불변 객체로 정의
 * @JsonIgnoreProperties(ignoreUnknown = true) : DTO에 정의되지 않은 JSON 필드는 무시
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantIdResponseDto(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("input") Input input,
        @JsonProperty("result") Result result,
        @JsonProperty("status") String status
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Input(
            Double latitude,
            Double longitude,
            @JsonProperty("similar_images") boolean similarImages,
            List<String> images
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Classification(
            List<Suggestion> suggestions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Suggestion(
            String id,
            String name,
            double probability, // API가 double로 주므로 우선 double로 받음
            @JsonProperty("similar_images") List<SimilarImage> similarImages,
            Details details
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SimilarImage(
            String id,
            String url,
            @JsonProperty("license_name") String licenseName,
            @JsonProperty("license_url") String licenseUrl,
            String citation,
            double similarity,
            @JsonProperty("url_small") String urlSmall
    ) {}

    // 🔴 [수정됨] Details 레코드
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(
            String language,
            @JsonProperty("entity_id") String entityId,
            @JsonProperty("common_names") List<String> commonNames,
            String url,
            Description description, // 👈 String에서 Description 객체로 변경!
            DiseaseDetails diseaseDetails
    ) {}

    // 🟢 [추가됨] Description 레코드 (API의 설명 객체를 받기 위함)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(
            String value, // 실제 설명 텍스트
            String citation,
            @JsonProperty("license_name") String licenseName,
            @JsonProperty("license_url") String licenseUrl
    ) {}

    // --- (DiagnosisHistory 엔티티에 맞게 상세화된 부분) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("is_plant") IsPlant isPlant,
            Classification classification,
            // 건강 진단(health='all') 요청 시 받는 필드
            @JsonProperty("is_healthy") IsHealthy isHealthy,
            Disease disease
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IsPlant(
            // 엔티티가 BigDecimal이므로 DTO도 BigDecimal로 받음
            @JsonProperty("probability") BigDecimal isPlantProbability,
            boolean binary,
            double threshold
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IsHealthy(
            @JsonProperty("probability") BigDecimal healthProbability,
            boolean binary
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Disease(
            List<DiseaseSuggestion> suggestions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiseaseSuggestion(
            String id,
            String name,
            @JsonProperty("probability") BigDecimal diseaseProbability,
            DiseaseDetails details // (details=diseaseDetails 요청 시)
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiseaseDetails(
            // description, url, common_names 등도 API 스펙에 따라 여기에 올 수 있음
            Treatment treatment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Treatment(
            // 이 필드들은 DiagnosisService에서 JSON 문자열로 변환되어
            // DiagnosisHistory의 'solutionDetail' 필드에 저장됨
            List<String> prevention,
            List<String> biological,
            List<String> chemical
    ) {}
}