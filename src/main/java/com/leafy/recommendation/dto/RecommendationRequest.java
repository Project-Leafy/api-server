package com.leafy.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.WaterFrequency;
import com.leafy.global.type.GrowthSpeed;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecommendationRequest {
    // 1. 채광량 (LOW, MEDIUM, HIGH)
    @JsonProperty("preferred_light")
    private LightLevel preferredLight;

    // 2. 물주기 선호도 (FREQUENT, NORMAL, RARE)
    @JsonProperty("preferred_water")
    private WaterFrequency preferredWater;

    // 3. 식물 키우기 숙련도 (EASY, NORMAL, HARD)
    @JsonProperty("user_skill")
    private DifficultyLevel userSkill;

    // 4. 선호하는 식물 성장 속도 (SLOW, NORMAL, FAST)
    @JsonProperty("growth_speed")
    private GrowthSpeed growthSpeed;

    // 5. 반려동물 여부 (true/false)
    @JsonProperty("has_pet")
    private boolean hasPet;
}