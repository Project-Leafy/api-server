package com.leafy.recommendation.domain;

import com.leafy.global.common.BaseTimeEntity;
import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.GrowthSpeed;
import com.leafy.global.type.WaterFrequency;
import com.leafy.user.domain.User;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "recommendation")
public class Recommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    // --- [수정] 1. 설문조사 결과 (JSON Map 대신 명시적 Enum 컬럼 사용) ---
    // 이유: 추천 알고리즘 로직(Service)에서 비교 연산을 쉽게 하기 위함

    @Enumerated(EnumType.STRING)
    private LightLevel preferredLight;       // 선호 광량

    @Enumerated(EnumType.STRING)
    private WaterFrequency preferredWater;   // 선호 물주기 빈도

    @Enumerated(EnumType.STRING)
    private DifficultyLevel userSkill;       // 사용자 숙련도

    @Enumerated(EnumType.STRING)
    private GrowthSpeed preferredGrowthSpeed;         // 선호 식물 성장 속도

    private boolean hasPet;                  // 반려동물 여부

    // --- 2. 분석 결과 (배치 작업 업데이트 영역) ---

    // 배치 분석 결과도 Enum으로 관리하여 비교 용이성 확보
    @Enumerated(EnumType.STRING)
    @Column(name = "analyzed_light_level_code")
    private LightLevel analyzedLightLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "analyzed_watering_pattern_code")
    private WaterFrequency analyzedWateringPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "analyzed_user_skill_code")
    private DifficultyLevel analyzedUserSkill;

    // 성공/실패 특성은 리스트 형태이므로 JSONB 유지
    @Type(JsonType.class)
    @Column(name = "analyzed_success_traits", columnDefinition = "jsonb")
    private List<String> analyzedSuccessTraits;

    @Type(JsonType.class)
    @Column(name = "analyzed_failure_traits", columnDefinition = "jsonb")
    private List<String> analyzedFailureTraits;

    private LocalDateTime lastAnalyzedAt;

    // --- 3. 추천 결과 저장 ---
    @Type(JsonType.class)
    @Column(name = "recommended_species_ids", columnDefinition = "jsonb")
    private List<Long> recommendedSpeciesIds;

    private LocalDateTime lastRecommendedAt;

    // --- 편의 메서드 ---

    // 1. 설문 결과 업데이트 (유저가 설문 다시 했을 때)
    public void updateSurvey(LightLevel light, WaterFrequency water, DifficultyLevel skill, GrowthSpeed growthSpeed, boolean hasPet) {
        this.preferredLight = light;
        this.preferredWater = water;
        this.userSkill = skill;
        this.preferredGrowthSpeed = growthSpeed;
        this.hasPet = hasPet;
    }

    // 2. 분석 결과 업데이트 (배치 스케줄러 용)
    public void updateAnalysis(LightLevel light, WaterFrequency water, DifficultyLevel skill, List<String> successTraits, List<String> failureTraits) {
        if (light != null) this.analyzedLightLevel = light;
        if (water != null) this.analyzedWateringPattern = water;
        if (skill != null) this.analyzedUserSkill = skill;
        if (successTraits != null) this.analyzedSuccessTraits = successTraits;
        if (failureTraits != null) this.analyzedFailureTraits = failureTraits;
        this.lastAnalyzedAt = LocalDateTime.now();
    }

    // 3. 추천 결과 업데이트
    public void updateRecommendations(List<Long> speciesIds) {
        this.recommendedSpeciesIds = speciesIds;
        this.lastRecommendedAt = LocalDateTime.now();
    }
}