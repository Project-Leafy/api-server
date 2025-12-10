package com.leafy.recommendation.scheduler;

import com.leafy.global.type.DifficultyLevel;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.PlantStatus;
import com.leafy.global.type.WaterFrequency;
import com.leafy.journal.domain.GrowthRecord;
import com.leafy.journal.repository.GrowthRecordRepository;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.dto.PlantDataDto;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.plant.service.PlantDataCache;
import com.leafy.recommendation.domain.Recommendation;
import com.leafy.recommendation.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final RecommendationRepository recommendationRepository;
    private final GrowthRecordRepository growthRecordRepository;
    private final MyPlantRepository myPlantRepository;
    private final PlantDataCache plantDataCache;

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시에 실행
    @Transactional
    public void analyzeUserPatterns() {
        log.info("[Batch] Start User Pattern Analysis...");
        List<Recommendation> recommendations = recommendationRepository.findAll();

        for (Recommendation rec : recommendations) {
            try {
                analyzeSingleUser(rec);
            } catch (Exception e) {
                log.error("Failed to analyze user id: {}", rec.getUser().getUserId(), e);
            }
        }
        log.info("[Batch] Analysis Finished. Total processed: {}", recommendations.size());
    }

    private void analyzeSingleUser(Recommendation rec) {
        Long userId = rec.getUser().getUserId();

        // 1. 물주기 패턴 분석
        WaterFrequency analyzedWateringPattern = analyzeWateringPattern(userId);

        // 2. 보유 식물 기반 분석
        List<MyPlant> userPlants = myPlantRepository.findAllByUser(rec.getUser());
        if (userPlants.isEmpty()) {
            rec.updateAnalysis(null, analyzedWateringPattern, null, null, null);
            log.info("User[{}] Analysis Updated: Water={}", userId, analyzedWateringPattern);
            return;
        }

        // 2.1 성공 식물 기반 분석 (HEALTHY, SICK 상태)
        List<PlantDataDto> successPlantDetails = userPlants.stream()
                .filter(p -> p.getStatus() == PlantStatus.HEALTHY || p.getStatus() == PlantStatus.SICK)
                .map(myPlant -> plantDataCache.getPlantById(myPlant.getPlantId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LightLevel analyzedLightLevel = analyzeLightLevel(successPlantDetails);
        DifficultyLevel analyzedUserSkill = analyzeUserSkill(successPlantDetails);
        List<String> analyzedSuccessTraits = analyzeSuccessTraits(successPlantDetails);

        // 2.2 실패 식물 기반 분석 (WITHERED 상태)
        List<PlantDataDto> witheredPlantDetails = userPlants.stream()
                .filter(myPlant -> myPlant.getStatus() == PlantStatus.WITHERED)
                .map(myPlant -> plantDataCache.getPlantById(myPlant.getPlantId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<String> analyzedFailureTraits = analyzeFailureTraits(witheredPlantDetails);

        // 3. 모든 분석 결과 DB에 업데이트
        rec.updateAnalysis(
                analyzedLightLevel,
                analyzedWateringPattern,
                analyzedUserSkill,
                analyzedSuccessTraits,
                analyzedFailureTraits
        );
        log.info("User[{}] Analysis Updated: Light={}, Water={}, Skill={}, SuccessTraits={}, FailureTraits={}",
                userId, analyzedLightLevel, analyzedWateringPattern, analyzedUserSkill, analyzedSuccessTraits, analyzedFailureTraits);
    }

    private WaterFrequency analyzeWateringPattern(Long userId) {
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<GrowthRecord> wateringRecords = growthRecordRepository.findAllWateringRecordsByUser(userId, threeMonthsAgo);

        if (wateringRecords.size() < 3) {
            return null; // 데이터 부족 시 분석 안함
        }

        long totalDays = 0;
        int intervalCount = wateringRecords.size() - 1;
        for (int i = 0; i < intervalCount; i++) {
            totalDays += ChronoUnit.DAYS.between(wateringRecords.get(i).getRecordDate(), wateringRecords.get(i + 1).getRecordDate());
        }
        double averageIntervalDays = (double) totalDays / intervalCount;

        if (averageIntervalDays <= 5.0) return WaterFrequency.FREQUENT;
        if (averageIntervalDays <= 10.0) return WaterFrequency.NORMAL;
        return WaterFrequency.INFREQUENT;
    }

    private LightLevel analyzeLightLevel(List<PlantDataDto> plants) {
        if (plants.isEmpty()) return null;
        Map<LightLevel, Long> lightCounts = plants.stream()
                .map(p -> {
                    String lightLux = p.getLightLux();
                    if (lightLux == null || lightLux.isEmpty()) return null;
                    if (lightLux.contains("낮은")) return LightLevel.LOW;
                    if (lightLux.contains("중간")) return LightLevel.MEDIUM;
                    if (lightLux.contains("높은")) return LightLevel.HIGH;
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return lightCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private DifficultyLevel analyzeUserSkill(List<PlantDataDto> plants) {
        if (plants.isEmpty()) return null;
        int maxDifficulty = plants.stream()
                .map(p -> {
                    String difficulty = p.getDisplayDifficulty();
                    if (difficulty == null || difficulty.isEmpty()) return 0;
                    if (difficulty.contains("전문가")) return 3;
                    if (difficulty.contains("경험자") || difficulty.contains("보통")) return 2;
                    if (difficulty.contains("초보자")) return 1;
                    return 0;
                })
                .max(Integer::compareTo)
                .orElse(0);

        return switch (maxDifficulty) {
            case 3 -> DifficultyLevel.HARD;
            case 2 -> DifficultyLevel.NORMAL;
            case 1 -> DifficultyLevel.EASY;
            default -> null;
        };
    }

    private List<String> analyzeSuccessTraits(List<PlantDataDto> plants) {
        if (plants.isEmpty()) return null;
        return plants.stream()
                .map(PlantDataDto::getKeywordTags)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> analyzeFailureTraits(List<PlantDataDto> plants) {
        if (plants.isEmpty()) return null;
        return plants.stream()
                .map(PlantDataDto::getKeywordTags)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
