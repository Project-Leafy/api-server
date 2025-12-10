package com.leafy.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.GrowthSpeed;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.WaterFrequency;
import com.leafy.plant.dto.PlantDataDto;
import com.leafy.recommendation.domain.Recommendation;
import com.leafy.recommendation.dto.RecommendationRequest;
import com.leafy.recommendation.dto.RecommendationResponseDto;
import com.leafy.recommendation.repository.RecommendationRepository;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    private static final String S3_KEY = "plants/final_plants.json";

    @Transactional
    public List<RecommendationResponseDto> recommendPlants(User user, RecommendationRequest request) {
        // 1. S3에서 모든 식물 데이터를 실시간으로 로드
        List<PlantDataDto> allPlants = loadPlantsFromS3();

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

        // 3. 하이브리드 필터링을 위한 물주기 값 결정
        WaterFrequency targetWaterFreq = determineTargetWaterFrequency(recommendationProfile, request.getPreferredWater());

        // 4. 점수 계산 및 랭킹 (Pet Safety는 Hard Filter로 먼저 적용)
        List<ScoredPlant> scoredPlants = allPlants.stream()
                .filter(plant -> !request.isHasPet() || !plant.isToxic()) // 반려동물 안전 하드 필터
                .map(plant -> {
                    int totalScore = 0;
                    totalScore += calculateLightScore(plant.getLightLux(), request.getPreferredLight());
                    totalScore += calculateWaterScore(plant, targetWaterFreq);
                    totalScore += calculateDifficultyScore(plant.getDisplayDifficulty(), request.getUserSkill());
                    totalScore += calculateGrowthSpeedScore(plant.getGrowthSpeed(), request.getGrowthSpeed());

                    // --- 실패 특성 페널티 적용 ---
                    List<String> analyzedFailureTraits = recommendationProfile.getAnalyzedFailureTraits();
                    if (analyzedFailureTraits != null && !analyzedFailureTraits.isEmpty() && plant.getKeywordTags() != null) {
                        for (String failureTrait : analyzedFailureTraits) {
                            if (plant.getKeywordTags().contains(failureTrait)) {
                                totalScore -= 20; // 페널티 점수 (예: -20점)
                            }
                        }
                    }
                    totalScore = Math.max(0, totalScore); // 점수가 0 미만으로 내려가지 않도록 보정

                    return new ScoredPlant(plant, totalScore);
                })
                .sorted((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore())) // 점수 내림차순 정렬
                .limit(10) // 상위 10개만 추천
                .collect(Collectors.toList());

        // 5. 추천 결과를 DTO로 변환 (태그 고도화 포함)
        List<RecommendationResponseDto> responseDtos = scoredPlants.stream()
                .map(scoredPlant -> mapToResponseDto(scoredPlant, recommendationProfile, request)) // request도 전달
                .collect(Collectors.toList());

        // 6. 추천 결과 ID를 DB에 저장
        List<Long> recommendedIds = scoredPlants.stream().map(sp -> sp.getPlant().getId()).toList();
        recommendationProfile.updateRecommendations(recommendedIds);

        return responseDtos;
    }

    private List<PlantDataDto> loadPlantsFromS3() {
        try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(S3_KEY)
                .build())) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to load or parse plant data from S3: {}", e.getMessage());
            throw new RuntimeException("Could not load plant data.", e);
        }
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
        Recommendation newRec = Recommendation.builder()
                .user(user)
                .build();
        return recommendationRepository.save(newRec);
    }

    // --- Scoring Helper Methods ---
    private int calculateLightScore(String plantLight, LightLevel userLight) {
        if (userLight == null || plantLight == null || plantLight.isEmpty()) return 0; // Default if data is missing or user has no preference

        int score = 0;
        switch (userLight) {
            case LOW:
                if (plantLight.contains("낮은")) score += 25;
                else if (plantLight.contains("중간")) score += 15;
                break;
            case MEDIUM:
                if (plantLight.contains("중간")) score += 25;
                else if (plantLight.contains("낮은") || plantLight.contains("높은")) score += 15;
                break;
            case HIGH:
                if (plantLight.contains("높은")) score += 25;
                else if (plantLight.contains("중간")) score += 15;
                break;
        }
        return score;
    }

    private int calculateWaterScore(PlantDataDto plant, WaterFrequency userWaterFreq) {
        if (userWaterFreq == null) return 0;

        int month = LocalDate.now().getMonthValue();
        int cycle;

        if (month >= 3 && month <= 5) cycle = plant.getWaterSpring();
        else if (month >= 6 && month <= 8) cycle = plant.getWaterSummer();
        else if (month >= 9 && month <= 11) cycle = plant.getWaterAutumn();
        else cycle = plant.getWaterWinter();

        if (cycle == 0) return 0; // If data is missing for the season

        switch (userWaterFreq) {
            case FREQUENT:
                if (cycle == 3) return 25;
                else if (cycle == 7) return 10; // Partial match
                break;
            case NORMAL:
                if (cycle == 7) return 25;
                else if (cycle == 3 || cycle == 14) return 10; // Partial match
                break;
            case INFREQUENT:
                if (cycle == 14) return 25;
                else if (cycle == 7) return 10; // Partial match
                break;
        }
        return 0; // No match
    }

    private int calculateDifficultyScore(String plantDifficulty, DifficultyLevel userSkill) {
        if (userSkill == null || plantDifficulty == null || plantDifficulty.isEmpty()) return 0;

        int score = 0;
        switch (userSkill) {
            case EASY:
                if (plantDifficulty.contains("초보자")) score += 25;
                else if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) score += 15;
                break;
            case NORMAL:
                if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) score += 25;
                else if (plantDifficulty.contains("초보자") || plantDifficulty.contains("전문가")) score += 15;
                break;
            case HARD:
                if (plantDifficulty.contains("전문가")) score += 25;
                else if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) score += 15;
                break;
        }
        return score;
    }

    private int calculateGrowthSpeedScore(String plantSpeed, GrowthSpeed userSpeed) {
        if (userSpeed == null || plantSpeed == null || plantSpeed.isEmpty()) return 0;

        int score = 0;
        switch (userSpeed) {
            case SLOW:
                if (plantSpeed.contains("느림")) score += 25;
                else if (plantSpeed.contains("보통")) score += 15;
                break;
            case NORMAL:
                if (plantSpeed.contains("보통")) score += 25;
                else if (plantSpeed.contains("느림") || plantSpeed.contains("빠름")) score += 15;
                break;
            case FAST:
                if (plantSpeed.contains("빠름")) score += 25;
                else if (plantSpeed.contains("보통")) score += 15;
                break;
        }
        return score;
    }

    // --- Tagging Helper Method ---
    // This will be used in mapToResponseDto
    private String getDifficultyTag(String plantDifficulty) {
        if (plantDifficulty == null || plantDifficulty.isEmpty()) return "";
        if (plantDifficulty.contains("초보자")) return "#초보자용";
        if (plantDifficulty.contains("경험자") || plantDifficulty.contains("보통")) return "#경험자용";
        if (plantDifficulty.contains("전문가")) return "#전문가용";
        return "";
    }

    private RecommendationResponseDto mapToResponseDto(ScoredPlant scoredPlant, Recommendation rec, RecommendationRequest request) {
        PlantDataDto plant = scoredPlant.getPlant();
        List<String> tags = new ArrayList<>();

        // 1. Tags based on user preferences and high score contribution (from scoring logic)
        // Pet Safety (Hard filter, so if it's here and user has pet, it's safe)
        if (request.isHasPet() && !plant.isToxic()) { // Only add if user has pet AND plant is safe
            tags.add("#반려동물에게_안전");
        } else if (!request.isHasPet() && plant.isToxic()){
            // User doesn't have pet but plant is toxic (no tag needed as it's not a preference match)
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
        if (rec.getAnalyzedWateringPattern() != null) { // If hybrid logic applied
            tags.add("#당신의_물주기습관에_딱!");
        }

        // Difficulty / Skill
        if (calculateDifficultyScore(plant.getDisplayDifficulty(), request.getUserSkill()) == 25) {
            tags.add("#선호_난이도_완벽일치");
        } else if (calculateDifficultyScore(plant.getDisplayDifficulty(), request.getUserSkill()) > 0) {
            tags.add("#선호_난이도_양호");
        }
        // Add basic difficulty tag if not a perfect match, or to clarify
        String difficultyTag = getDifficultyTag(plant.getDisplayDifficulty());
        if (!difficultyTag.isEmpty()) {
            tags.add(difficultyTag);
        }

        // Growth Speed
        if (calculateGrowthSpeedScore(plant.getGrowthSpeed(), request.getGrowthSpeed()) == 25) {
            tags.add("#선호_성장속도_완벽일치");
        } else if (calculateGrowthSpeedScore(plant.getGrowthSpeed(), request.getGrowthSpeed()) > 0) {
            tags.add("#선호_성장속도_양호");
        }

        // 2. Add general plant characteristic tags from keywordTags in JSON
        if (plant.getKeywordTags() != null) {
            plant.getKeywordTags().forEach(tag -> {
                if (!tags.contains(tag)) { // Avoid duplicate tags
                    tags.add(tag);
                }
            });
        }

        return RecommendationResponseDto.builder()
                .speciesId(plant.getId())
                .koreanName(plant.getKoreanName())
                .scientificName(plant.getScientificName())
                .officialImageUrl(plant.getImageUrl())
                .description(plant.getDescription()) // ADDED DESCRIPTION HERE
                .tags(tags)
                .matchScore(scoredPlant.getScore())
                .build();
    }

    /**
     * 추천 점수를 계산하기 위한 내부 래퍼 클래스
     */
    private static class ScoredPlant {
        private final PlantDataDto plant;
        private final int score;

        public ScoredPlant(PlantDataDto plant, int score) {
            this.plant = plant;
            this.score = score;
        }

        public PlantDataDto getPlant() {
            return plant;
        }

        public int getScore() {
            return score;
        }
    }
}
