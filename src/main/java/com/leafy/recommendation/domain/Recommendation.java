// com/leafy/recommendation/domain/Recommendation.java

package com.leafy.recommendation.domain;

import com.leafy.global.common.BaseTimeEntity;
import com.leafy.user.domain.User;
import io.hypersistence.utils.hibernate.type.json.JsonType; // 1. jsonb 타입을 위한 라이브러리 (implementation 'io.hypersistence:hypersistence-utils-hibernate-60:3.5.2' 와 같은 의존성 추가 필요)
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "recommendation")
public class Recommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    // 2. 1:1 관계 (Recommendation(1) -> User(1))
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false) // 3. FK이면서 Unique
    private User user;

    // 4. jsonb 타입 매핑 (Map<String, Object> 또는 특정 DTO 클래스 사용)
    @Type(JsonType.class)
    @Column(name = "survey_environment_data", columnDefinition = "jsonb")
    private Map<String, Object> surveyEnvironmentData;

    @Type(JsonType.class)
    @Column(name = "survey_experience_data", columnDefinition = "jsonb")
    private Map<String, Object> surveyExperienceData;

    // ... (survey_preference_data) ...

    @Column(name = "analyzed_light_level_code", length = 50)
    private String analyzedLightLevelCode;

    @Column(name = "analyzed_watering_pattern_code", length = 50)
    private String analyzedWateringPatternCode;

    // 5. jsonb (List<String> 예시)
    @Type(JsonType.class)
    @Column(name = "analyzed_success_traits", columnDefinition = "jsonb")
    private List<String> analyzedSuccessTraits;

    // ... (analyzed_failure_traits) ...

    private LocalDateTime lastAnalyzedAt;

    // 6. jsonb (List<Long> 예시 - species_id 목록)
    @Type(JsonType.class)
    @Column(name = "recommended_species_ids", columnDefinition = "jsonb")
    private List<Long> recommendedSpeciesIds;

    private LocalDateTime lastRecommendedAt;
}