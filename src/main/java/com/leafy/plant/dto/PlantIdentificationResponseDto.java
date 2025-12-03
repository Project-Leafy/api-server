package com.leafy.plant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.util.List;

/**
 * 식물 식별 후, MyPlant 등록을 위해 프론트엔드로 반환하는 DTO
 */
@Builder
public record PlantIdentificationResponseDto(
        Long userId, // 현재 사용자 ID (DB 저장 시 필요)
        Long speciesId, // 식별된 PlantSpecies의 DB ID
        String scientificName, // 식별된 학명 (예: Monstera Deliciosa)
        // ✅ [추가 1] 일반명 필드 추가
        @JsonProperty("common_name")
        String commonName, // 식별된 일반명 (예: 몬스테라)
        String imageUrl, // S3에 저장된 이미지 URL
        Double probability,
        // 추가적으로 API에서 받은 관리 팁, 물주기 주기 등도 포함할 수 있음
        // ✅ [추가] 후보 목록 전체를 담을 리스트
        @JsonProperty("suggestions")
        List<Suggestion> suggestions,
        // ✅ [추가 2] 상세 설명을 위한 필드 추가
        @JsonProperty("description")
        String description
) {
    // ✅ [추가] 후보 하나하나의 정보를 담을 내부 레코드
    @Builder
    public record Suggestion(
            String name, // 일반명
            @JsonProperty("scientific_name")
            String scientificName, // 학명
            Double probability, // 확률
            @JsonProperty("image_url") // (선택) 해당 종의 대표 이미지 URL
            String imageUrl,
            // ✅ [추가 1] 일반명 필드 추가
            @JsonProperty("common_name")
            String commonName,
            // ✅ [추가 2] 상세 설명을 위한 필드 추가
            @JsonProperty("description")
            String description
    ) {}
}
