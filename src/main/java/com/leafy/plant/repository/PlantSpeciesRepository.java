package com.leafy.plant.repository;

import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.domain.PlantSpecies;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.leafy.user.domain.User;
import java.util.List;

public interface PlantSpeciesRepository extends JpaRepository<PlantSpecies, Long> {

    // 1. AI 진단 결과(학명)로 공식 식물 정보를 찾을 때 사용
    Optional<PlantSpecies> findByScientificName(String scientificName);

    // 2. (선택적) 한글 이름으로 검색할 때
    Optional<PlantSpecies> findByKoreanName(String koreanName);

}

