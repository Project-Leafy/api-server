// 경로: api-server/src/main/java/com/leafy/diagnosis/dto/PlantIdRequestDto.java
package com.leafy.diagnosis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * plant.id API (JSON 방식) 요청 DTO
 * (S3 업로드 후 URL을 전송할 때 사용)
 */
public record PlantIdRequestDto(
        List<String> images,
        Double latitude,
        Double longitude,
        @JsonProperty("similar_images") boolean similarImages,
        String health
        // @JsonProperty("api_key") String apiKey 제거 webconfig에서 이미 처리함
        // List<String> details <-- 이 필드를 삭제합니다.
) {
    // 생성자도 필드 개수에 맞게 수정해야 합니다.
    public PlantIdRequestDto(List<String> images, Double latitude, Double longitude, String health) {
        this(images, latitude, longitude, true, health);
    }
}