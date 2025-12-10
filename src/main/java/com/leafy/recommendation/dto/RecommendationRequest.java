package com.leafy.recommendation.dto;

import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.PlantSize;
import com.leafy.global.type.WaterFrequency;
import com.leafy.global.type.GrowthSpeed;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecommendationRequest {
    // 1. 채광량 (LOW, MEDIUM, HIGH)
    private LightLevel preferredLight;

    // 2. 물주기 선호도 (FREQUENT, NORMAL, RARE)
    private WaterFrequency preferredWater;

    // 3. 식물 키우기 숙련도 (EASY, NORMAL, HARD)
    private DifficultyLevel userSkill;

    // 4. 선호하는 식물 성장 속도 (SLOW, NORMAL, FAST)
    private GrowthSpeed growthSpeed;

    // 5. 반려동물 여부 (true/false)
    private boolean hasPet;
}