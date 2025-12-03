package com.leafy.recommendation.scheduler;

import com.leafy.global.type.WaterFrequency;
import com.leafy.journal.domain.GrowthRecord;
import com.leafy.journal.repository.GrowthRecordRepository;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final RecommendationRepository recommendationRepository;
    private final GrowthRecordRepository growthRecordRepository;

    @Scheduled(cron = "0 0 3 * * *")
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

        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<GrowthRecord> wateringRecords = growthRecordRepository.findAllWateringRecordsByUser(userId, threeMonthsAgo);

        if (wateringRecords.size() < 3) {
            return;
        }

        double averageIntervalDays = calculateAverageInterval(wateringRecords);
        WaterFrequency analyzedPattern = mapToFrequency(averageIntervalDays);

        rec.updateAnalysis(
                null,
                analyzedPattern,
                null,
                null
        );

        log.info("User[{}] Analysis Updated: Interval={} days -> Pattern={}",
                userId, String.format("%.1f", averageIntervalDays), analyzedPattern);
    }

    private double calculateAverageInterval(List<GrowthRecord> records) {
        long totalDays = 0;
        int intervalCount = records.size() - 1;

        for (int i = 0; i < intervalCount; i++) {
            LocalDate prev = records.get(i).getRecordDate();
            LocalDate next = records.get(i + 1).getRecordDate();

            totalDays += ChronoUnit.DAYS.between(prev, next);
        }

        return (double) totalDays / intervalCount;
    }

    private WaterFrequency mapToFrequency(double days) {
        if (days <= 5.0) {
            return WaterFrequency.FREQUENT;
        } else if (days <= 10.0) {
            return WaterFrequency.NORMAL;
        } else {
            return WaterFrequency.INFREQUENT;
        }
    }
}
