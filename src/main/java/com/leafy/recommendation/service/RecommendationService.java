package com.leafy.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.GrowthSpeed;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.WaterFrequency;
import com.leafy.plant.dto.PlantDataDto;
import com.leafy.plant.service.PlantDataCache;
import com.leafy.recommendation.domain.Recommendation;
import com.leafy.recommendation.dto.RecommendationRequest;
import com.leafy.recommendation.dto.RecommendationResponseDto;
import com.leafy.recommendation.repository.RecommendationRepository;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final PlantDataCache plantDataCache;

    @Transactional
    public List<RecommendationResponseDto> recommendPlants(User user, RecommendationRequest request) {
        // 1. 캐시에서 모든 식물 데이터 로드
        Collection<PlantDataDto> allPlants = plantDataCache.getAllPlants();
        log.debug("Loaded {} plants from cache.", allPlants.size());

        // 2. 사용자 추천 프로필 조회 및 설문 결과 업데이트
        Recommendation recommendationProfile = recommendationRepository.findByUser(user)
                .orElseGet(() -> createNewRecommendation(user));
        recommendationProfile.updateSurvey(
                request.getPreferredLight(),
                request.getPreferredWater(),
                request.getUserSkill(),
                request.getGrowthSpeed(),
                request.isHasPet()
        );
        log.debug("User's request: Light={}, Water={}, Skill={}, Speed={}, HasPet={}",
                request.getPreferredLight(), request.getPreferredWater(), request.getUserSkill(), request.getGrowthSpeed(), request.isHasPet());

        // 3. 하이브리드 필터링을 위한 물주기 값 결정
        WaterFrequency targetWaterFreq = determineTargetWaterFrequency(recommendationProfile, request.getPreferredWater());

        // 4. 점수 계산 및 랭킹
        List<ScoredPlant> scoredPlants = allPlants.stream()
                .filter(plant -> !request.isHasPet() || !plant.isToxic()) // 반려동물 안전 하드 필터
                .map(plant -> {
                    int lightScore = calculateLightScore(plant.getLightLux(), request.getPreferredLight());
                    int waterScore = calculateWaterScore(plant, targetWaterFreq);
                    int difficultyScore = calculateDifficultyScore(plant.getDisplayDifficulty(), request.getUserSkill());
                    int growthSpeedScore = calculateGrowthSpeedScore(plant.getGrowthSpeed(), request.getGrowthSpeed());
                    int totalScore = lightScore + waterScore + difficultyScore + growthSpeedScore;

                    // 실패 특성 페널티 적용
                    List<String> analyzedFailureTraits = recommendationProfile.getAnalyzedFailureTraits();
                    if (analyzedFailureTraits != null && !analyzedFailureTraits.isEmpty() && plant.getKeywordTags() != null) {
                        for (String failureTrait : analyzedFailureTraits) {
                            if (plant.getKeywordTags().contains(failureTrait)) {
                                totalScore -= 20; // 페널티 점수
                            }
                        }
                    }
                    totalScore = Math.max(0, totalScore); // 점수가 0 미만으로 내려가지 않도록 보정
                    log.debug("Scoring plant '{}': Light={}, Water={}, Difficulty={}, Speed={}, Penalty Applied. Total={}",
                            plant.getKoreanName(), lightScore, waterScore, difficultyScore, growthSpeedScore, totalScore);
                    return new ScoredPlant(plant, totalScore);
                })
                .sorted((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore())) // 점수 내림차순 정렬
                .limit(10) // 상위 10개만 추천
                .collect(Collectors.toList());
        
        log.debug("Top 10 scored plants count: {}", scoredPlants.size());

        // 5. 추천 결과를 DTO로 변환
        List<RecommendationResponseDto> responseDtos = scoredPlants.stream()
                .map(scoredPlant -> mapToResponseDto(scoredPlant, recommendationProfile, request))
                .collect(Collectors.toList());

        // 6. 추천 결과 ID를 DB에 저장
        List<Long> recommendedIds = scoredPlants.stream().map(sp -> sp.getPlant().getId()).toList();
        recommendationProfile.updateRecommendations(recommendedIds);

        log.info("Returning {} recommendations for user {}", responseDtos.size(), user.getUserId());
        return responseDtos;
    }

    private WaterFrequency determineTargetWaterFrequency(Recommendation rec, WaterFrequency surveyFreq) {
        if (rec.getAnalyzedWateringPattern() != null) {
            log.info("User[{}] Hybrid applied: Survey({}) -> Actual({})",
                    rec.getUser().getUserId(), surveyFreq, rec.getAnalyzedWateringPattern());
            return rec.getAnalyzedWateringPattern();
        }
        return surveyFreq;
    }

    private Recommendation createNewRecommendation(User user) {
        Recommendation newRec = Recommendation.builder().user(user).build();
        return recommendationRepository.save(newRec);
    }

    // --- Scoring Helper Methods ---
    private int calculateLightScore(String plantLight, LightLevel userLight) {
        if (userLight == null || plantLight == null || plantLight.isEmpty()) return 0;
        int score = 0;
        switch (userLight) {
            case LOW:
                if (plantLight.contains("낮은")) score = 25;
                else if (plantLight.contains("중간")) score = 15;
                break;
            case MEDIUM:
                if (plantLight.contains("중간")) score = 25;
                else if (plantLight.contains("낮은") || plantLight.contains("높은")) score = 15;
                break;
            case HIGH:
                if (plantLight.contains("높은")) score = 25;
                else if (plantLight.contains("중간")) score = 15;
                break;
        }
        return score;
    }

    private int calculateWaterScore(PlantDataDto plant, WaterFrequency userWaterFreq) {
        if (userWaterFreq == null) return 0;
        int month = LocalDate.now().getMonthValue();
        int cycle = (month >= 3 && month <= 5) ? plant.getWaterSpring() :
                    (month >= 6 && month <= 8) ? plant.getWaterSummer() :
                    (month >= 9 && month <= 11) ? plant.getWaterAutumn() :
                    plant.getWaterWinter();
        if (cycle == 0) return 0;
        int score = 0;
        switch (userWaterFreq) {
            case FREQUENT:
                if (cycle == 3) score = 25;
                else if (cycle == 7) score = 10;
                break;
            case NORMAL:
                if (cycle == 7) score = 25;
                else if (cycle == 3 || cycle == 14) score = 10;
                break;
            case INFREQUENT:
                if (cycle == 14) score = 25;
                else if (cycle == 7) score = 10;
                break;
        }
        return score;
    }

    private int calculateDifficultyScore(String plantDifficulty, DifficultyLevel userSkill) {
        if (userSkill == null || plantDifficulty == null || plantDifficulty.isEmpty()) return 0;
        int score = 0;
        switch (userSkill) {
            case EASY:
                if (plantDifficulty.contains("초보자")) score = 25;
                else if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) score = 15;
                break;
            case NORMAL:
                if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) score = 25;
                else if (plantDifficulty.contains("초보자") || plantDifficulty.contains("전문가")) score = 15;
                break;
            case HARD:
                if (plantDifficulty.contains("전문가")) score = 25;
                else if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) score = 15;
                break;
        }
        return score;
    }

    private int calculateGrowthSpeedScore(String plantSpeed, GrowthSpeed userSpeed) {
        if (userSpeed == null || plantSpeed == null || plantSpeed.isEmpty()) return 0;
        int score = 0;
        switch (userSpeed) {
            case SLOW:
                if (plantSpeed.contains("느림")) score = 25;
                else if (plantSpeed.contains("보통")) score = 15;
                break;
            case NORMAL:
                if (plantSpeed.contains("보통")) score = 25;
                else if (plantSpeed.contains("느림") || plantSpeed.contains("빠름")) score = 15;
                break;
            case FAST:
                if (plantSpeed.contains("빠름")) score = 25;
                else if (plantSpeed.contains("보통")) score = 15;
                break;
        }
        return score;
    }

    private String getDifficultyTag(String plantDifficulty) {
        if (plantDifficulty == null || plantDifficulty.isEmpty()) return null;
        if (plantDifficulty.contains("초보자")) return "#초보자용";
        if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) return "#경험자용";
        if (plantDifficulty.contains("전문가")) return "#전문가용";
        return null;
    }

    private RecommendationResponseDto mapToResponseDto(ScoredPlant scoredPlant, Recommendation rec, RecommendationRequest request) {
        PlantDataDto plant = scoredPlant.getPlant();
        List<String> tags = new ArrayList<>();
        
        // Pet Safety
        if (request.isHasPet() && !plant.isToxic()) {
            tags.add("#반려동물에게_안전");
        }

        // Light
        if (calculateLightScore(plant.getLightLux(), request.getPreferredLight()) == 25) {
            tags.add("#선호_햇빛조건_완벽일치");
        } else if (calculateLightScore(plant.getLightLux(), request.getPreferredLight()) > 0) {
            tags.add("#선호_햇빛조건_양호");
        }

        // Water
        WaterFrequency actualWaterFreq = determineTargetWaterFrequency(rec, request.getPreferredWater());
        if (calculateWaterScore(plant, actualWaterFreq) == 25) {
            tags.add("#선호_물주기조건_완벽일치");
        } else if (calculateWaterScore(plant, actualWaterFreq) > 0) {
            tags.add("#선호_물주기조건_양호");
        }
        if (rec.getAnalyzedWateringPattern() != null) {
            tags.add("#당신의_물주기습관에_딱!");
        }

        // Difficulty / Skill
        if (calculateDifficultyScore(plant.getDisplayDifficulty(), request.getUserSkill()) == 25) {
            tags.add("#선호_난이도_완벽일치");
        }
        String difficultyTag = getDifficultyTag(plant.getDisplayDifficulty());
        if (difficultyTag != null) {
            tags.add(difficultyTag);
        }

        // Growth Speed
        if (calculateGrowthSpeedScore(plant.getGrowthSpeed(), request.getGrowthSpeed()) == 25) {
            tags.add("#선호_성장속도_완벽일치");
        }

        // Failure Trait
        List<String> analyzedFailureTraits = rec.getAnalyzedFailureTraits();
        if (analyzedFailureTraits != null && !analyzedFailureTraits.isEmpty() && plant.getKeywordTags() != null) {
            if (plant.getKeywordTags().stream().anyMatch(analyzedFailureTraits::contains)) {
                tags.add("#과거_실패_경험_주의");
            }
        }
        
        // General Keyword Tags
        if (plant.getKeywordTags() != null) {
            plant.getKeywordTags().forEach(tag -> {
                if (!tags.contains(tag)) {
                    tags.add(tag);
                }
            });
        }
        
        RecommendationResponseDto dto = RecommendationResponseDto.builder()
                .speciesId(plant.getId())
                .koreanName(plant.getKoreanName())
                .scientificName(plant.getScientificName())
                .officialImageUrl(plant.getImageUrl())
                .description(plant.getDescription())
                .tags(tags)
                .matchScore(scoredPlant.getScore())
                .build();
        
        log.debug("Mapped DTO: id={}, name={}, score={}, tags={}", dto.getSpeciesId(), dto.getKoreanName(), dto.getMatchScore(), dto.getTags());
        return dto;
    }

    private static class ScoredPlant {
        private final PlantDataDto plant;
        private final int score;
        public ScoredPlant(PlantDataDto plant, int score) { this.plant = plant; this.score = score; }
        public PlantDataDto getPlant() { return plant; }
        public int getScore() { return score; }
    }
}