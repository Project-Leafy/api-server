package com.leafy.plant.dto;

import com.fasterxml.jackson.annotation.JsonProperty; // 임포트 추가
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.dto.DiagnosisResponseDto;
import com.leafy.plant.domain.MyPlant;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlantDetailResponseDto {

    private Long plantId;
    private String nickname;

    // ✅ [추가 1] 여기가 제일 중요하다 이다! 이미지 주소 필드
    @JsonProperty("image_url")
    private String imageUrl;

    private LocalDate adoptionDate; // ✅ 이 필드 추가
    private List<IdentificationCandidateDto> identificationCandidates;
    private DiagnosisResponseDto latestDiagnosis;

    public record IdentificationCandidateDto(
            String name,
            BigDecimal probability,
            List<String> common_names,
            String url,
            @JsonProperty("common_name") // ✅ 일반명 추가!
            String commonName,
            @JsonProperty("description") // ✅ 상세 설명 추가!
            String description // 상세 설명
    ) {}

    public static PlantDetailResponseDto of(MyPlant myPlant, Optional<DiagnosisHistory> latestHistory, ObjectMapper objectMapper) {
        List<IdentificationCandidateDto> candidates = Collections.emptyList();
        if (myPlant.getIdentificationResult() != null && !myPlant.getIdentificationResult().isEmpty()) {
            try {
                candidates = objectMapper.readValue(myPlant.getIdentificationResult(), new TypeReference<List<IdentificationCandidateDto>>() {});
            } catch (IOException e) {
                log.error("Failed to parse identificationResult JSON for plantId: {}", myPlant.getPlantId(), e);
            }
        }

        return PlantDetailResponseDto.builder()
                .plantId(myPlant.getPlantId())
                .nickname(myPlant.getNickname())
                .imageUrl(myPlant.getImageUrl())
                .identificationCandidates(candidates)
                .adoptionDate(myPlant.getAdoptionDate()) // ✅ 이 부분 추가
                .latestDiagnosis(latestHistory.map(DiagnosisResponseDto::from).orElse(null))
                .build();
    }
}
