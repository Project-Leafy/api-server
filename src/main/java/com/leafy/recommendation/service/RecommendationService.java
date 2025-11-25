package com.leafy.recommendation.service;

import com.leafy.global.type.WaterFrequency;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.plant.repository.PlantSpeciesRepository;
import com.leafy.recommendation.domain.Recommendation;
import com.leafy.recommendation.dto.RecommendationRequest;
import com.leafy.recommendation.dto.RecommendationResponseDto;
import com.leafy.recommendation.repository.RecommendationRepository;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final PlantSpeciesRepository plantSpeciesRepository;

    /**
     * 맞춤 식물 추천 (Hybrid: 설문 + 행동 데이터)
     */
    @Transactional
    public List<RecommendationResponseDto> recommendPlants(User user, RecommendationRequest request) {

        // 1. 사용자 추천 프로필 조회 (없으면 생성)
        Recommendation recommendation = recommendationRepository.findByUser(user)
                .orElseGet(() -> createNewRecommendation(user));

        // 2. 설문 데이터 업데이트 (최신 요구사항 반영)
        recommendation.updateSurvey(
                request.getPreferredLight(),
                request.getPreferredWater(),
                request.getUserSkill(),
                request.getPreferredSize(),
                request.isHasPet()
        );

        // 3. [핵심] 하이브리드 필터링 로직 결정
        // 기본적으로 설문값(request)을 따르지만, 배치 분석 결과(analyzed)가 있다면 그것을 우선시함.
        // 예: 유저는 '자주 주고 싶다(FREQUENT)' 했지만, 실제로는 '게으름(RARE)' -> RARE 식물 추천 (식물 보호)

        WaterFrequency targetWaterFreq = request.getPreferredWater(); // 기본: 설문값

        if (recommendation.getAnalyzedWateringPattern() != null) {
            targetWaterFreq = recommendation.getAnalyzedWateringPattern(); // 보정: 실제 습관
            log.info("User[{}] Hybrid applied: Survey({}) -> Actual({})",
                    user.getUserId(), request.getPreferredWater(), targetWaterFreq);
        }

        // 4. 식물 도감 검색
        List<PlantSpecies> plants = plantSpeciesRepository.findRecommendations(
                request.getPreferredLight(),
                targetWaterFreq,   // 보정된 물주기 값 사용
                request.getUserSkill(),
                request.getPreferredSize(),
                request.isHasPet()
        );

        // 5. 결과 변환 및 태그 생성
        List<RecommendationResponseDto> responseDtos = plants.stream()
                .map(plant -> mapToDto(plant, recommendation))
                .collect(Collectors.toList());

        // 6. 추천 결과 ID 저장 (나중에 "지난번 추천 목록" 조회 시 사용)
        List<Long> plantIds = plants.stream().map(PlantSpecies::getSpeciesId).toList();
        recommendation.updateRecommendations(plantIds);

        return responseDtos;
    }

    // --- 내부 헬퍼 메서드 ---

    private Recommendation createNewRecommendation(User user) {
        Recommendation newRec = Recommendation.builder()
                .user(user)
                .build();
        return recommendationRepository.save(newRec);
    }

    private RecommendationResponseDto mapToDto(PlantSpecies plant, Recommendation rec) {
        List<String> tags = new ArrayList<>();

        // 태그 생성 로직 (프론트엔드 노출용)
        tags.add("#" + plant.getDifficultyLevel().name()); // #EASY
        if (Boolean.TRUE.equals(plant.getIsPetFriendly())) tags.add("#반려동물안전");

        // 하이브리드 추천인 경우 특별 태그 추가
        if (rec.getAnalyzedWateringPattern() != null) {
            tags.add("#당신의_물주기습관에_딱!");
        }

        return RecommendationResponseDto.builder()
                .speciesId(plant.getSpeciesId())
                .koreanName(plant.getKoreanName())
                .scientificName(plant.getScientificName())
                .officialImageUrl(plant.getOfficialImageUrl())
                .tags(tags)
                .matchScore(100) // 현재는 단순 필터링이므로 100점 고정 (추후 정교화 가능)
                .build();
    }
}