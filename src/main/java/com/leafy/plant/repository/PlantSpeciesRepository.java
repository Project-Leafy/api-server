package com.leafy.plant.repository;

import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.user.domain.User;
import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.PlantSize;
import com.leafy.global.type.WaterFrequency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;


public interface PlantSpeciesRepository extends JpaRepository<PlantSpecies, Long> {

    // 1. AI 진단 결과(학명)로 공식 식물 정보를 찾을 때 사용
    Optional<PlantSpecies> findByScientificName(String scientificName);

    // 2. (선택적) 한글 이름으로 검색할 때
    Optional<PlantSpecies> findByKoreanName(String koreanName);

    // 3. [추가됨] 맞춤 식물 추천 필터링 쿼리 (Hybrid Logic)
    // - 사용자 환경(광량, 물주기, 난이도, 크기)과 일치하는 식물을 찾습니다.
    // - 반려동물 안전 여부(isPetSafe)가 true일 때만 isPetFriendly=true인 식물을 찾고, false면 모두 찾습니다.
    @Query("SELECT p FROM PlantSpecies p " +
            "WHERE p.sunlightLevel = :light " +
            "AND p.wateringFrequency = :water " +
            "AND p.difficultyLevel = :difficulty " +
            "AND p.sizeCode = :size " +
            "AND (:isPetSafe = false OR p.isPetFriendly = true)")
    List<PlantSpecies> findRecommendations(
            @Param("light") LightLevel light,
            @Param("water") WaterFrequency water,
            @Param("difficulty") DifficultyLevel difficulty,
            @Param("size") PlantSize size,
            @Param("isPetSafe") boolean isPetSafe
    );
}

