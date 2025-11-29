package com.leafy.plant.dto;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty; // ⭐️ 이 import 필수!

public record CreateMyPlantRequest(
        Long speciesId,
        String nickname,
        String imageUrl,
        LocalDate adoptionDate,
        // ✅ 여기가 제일 중요함!
        @JsonProperty("identification_result") // JSON에서 identification_result 라고 오면 여기로 넣음
        String identificationResult
) {}