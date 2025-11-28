package com.leafy.diagnosis.dto;

import com.leafy.diagnosis.domain.DiagnosisHistory;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record DiagnosisResponseDto(
        Long diagnosisId,
        LocalDateTime diagnosisDatetime,
        String requestImageUrl, // 진단 요청했던 사진
        String diseaseName,     // 병명
        BigDecimal diseaseProbability, // 확률
        Boolean isHealthy,      // 건강 여부
        String solutionDetail   // 상세 솔루션 (JSON String)
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