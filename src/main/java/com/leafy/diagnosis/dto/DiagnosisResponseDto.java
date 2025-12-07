package com.leafy.diagnosis.dto;

import com.leafy.diagnosis.domain.DiagnosisHistory;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat; // ✅ 추가 필요
@Builder
public record DiagnosisResponseDto(
        @JsonProperty("diagnosis_id")
        Long diagnosisId,

        @JsonProperty("diagnosis_datetime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime diagnosisDatetime,

        @JsonProperty("request_image_url")
        String requestImageUrl, // 진단 요청했던 사진

        @JsonProperty("disease_name")
        String diseaseName,     // 병명

        @JsonProperty("disease_probability")
        BigDecimal diseaseProbability, // 확률

        @JsonProperty("is_healthy")
        Boolean isHealthy,      // 건강 여부

        @JsonProperty("solution_detail")
        String solutionDetail   // 상세 솔루션
) {
    public static DiagnosisResponseDto from(DiagnosisHistory entity) {
        return DiagnosisResponseDto.builder()
                .diagnosisId(entity.getDiagnosisId())
                .diagnosisDatetime(entity.getDiagnosisDatetime())
                .requestImageUrl(entity.getRequestImageUrl())
                .diseaseName(entity.getDiseaseName())
                .diseaseProbability(entity.getDiseaseProbability())
                .isHealthy(entity.getIsHealthy())
                .solutionDetail(entity.getSolutionDetail())
                .build();
    }
}