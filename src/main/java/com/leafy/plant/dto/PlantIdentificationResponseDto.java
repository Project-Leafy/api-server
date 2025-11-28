package com.leafy.plant.dto;

import lombok.Builder;

/**
 * 식물 식별 후, MyPlant 등록을 위해 프론트엔드로 반환하는 DTO
 */
@Builder
public record PlantIdentificationResponseDto(
        Long userId, // 현재 사용자 ID (DB 저장 시 필요)
        Long speciesId, // 식별된 PlantSpecies의 DB ID
        String scientificName, // 식별된 학명 (예: Monstera Deliciosa)
        String commonName, // 식별된 일반명 (예: 몬스테라)
        String imageUrl // S3에 저장된 이미지 URL
        // 추가적으로 API에서 받은 관리 팁, 물주기 주기 등도 포함할 수 있음
) {}
