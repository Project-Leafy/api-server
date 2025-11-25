// com/leafy/plant/domain/PlantSpecies.java

package com.leafy.plant.domain;

import com.leafy.global.common.BaseTimeEntity;
import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.PlantSize;
import com.leafy.global.type.WaterFrequency;
import jakarta.persistence.*;
import lombok.*;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // ⬅️ 추가
@Builder // ⬅️ 추가
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "plant_species")
public class PlantSpecies extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "species_id")
    private Long speciesId;

    // 1. 학명(scientific_name)은 API 연동의 핵심 키이므로 unique = true
    @Column(name = "scientific_name", unique = true, nullable = false, length = 255)
    private String scientificName;

    @Column(name = "korean_name", nullable = false, length = 255)
    private String koreanName;

    @Column(name = "family_name", length = 100)
    private String familyName;

    @Column(name = "genus_name", length = 100)
    private String genusName;

    @Enumerated(EnumType.STRING)
    @Column(name = "watering_cycle_code", nullable = false)
    private WaterFrequency wateringFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "sunlight_level_code", nullable = false)
    private LightLevel sunlightLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level")
    private DifficultyLevel difficultyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_code")
    private PlantSize sizeCode; // 설문조사의 '식물 크기'와 매칭

    @Column(name = "is_pet_friendly")
    public Boolean isPetFriendly; // 반려동물 안전 여부

    @Column(name = "optimal_temp_celsius", length = 50)
    private String optimalTempCelsius;

    @Column(name = "management_tip_detail", columnDefinition = "TEXT")
    private String managementTipDetail;


    @Column(name = "toxicity_info", columnDefinition = "TEXT")
    private String toxicityInfo;

    @Column(name = "official_image_url", length = 2048)
    private String officialImageUrl;

    @Builder.Default
    @Column(name = "is_verified_by_admin", nullable = false)
    private Boolean isVerifiedByAdmin = false;

    @Column(name = "rda_data_id", length = 100)
    private String rdaDataId;
}