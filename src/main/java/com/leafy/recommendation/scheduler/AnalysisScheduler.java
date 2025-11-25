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

    // 매일 새벽 3시에 실행 (Cron 표현식: 초 분 시 일 월 요일)
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void analyzeUserPatterns() {
        log.info("[Batch] Start User Pattern Analysis...");

        // 1. 추천 정보가 있는 모든(혹은 활성) 데이터 조회
        // (실무에서는 페이징 처리나 배치 프레임워크를 쓰지만, 여기선 전체 조회로 구현)
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

        // 2. 최근 3개월간의 '물주기' 기록 조회
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<GrowthRecord> wateringRecords = growthRecordRepository.findAllWateringRecordsByUser(userId, threeMonthsAgo);

        // 기록이 너무 적으면 분석 불가 (최소 3번 이상 줘야 주기 판단 가능) -> 분석 스킵 혹은 기본값 유지
        if (wateringRecords.size() < 3) {
            return;
        }

        // 3. 평균 물주기 간격 계산
        double averageIntervalDays = calculateAverageInterval(wateringRecords);

        // 4. Enum으로 매핑 (FREQUENT, NORMAL, RARE)
        WaterFrequency analyzedPattern = mapToFrequency(averageIntervalDays);

        // 5. 분석 결과 업데이트 (Entity 편의 메서드 사용)
        // 성공 특성 등은 추후 확장 가능 (여기서는 물주기 패턴만 업데이트)
        rec.updateAnalysis(
                null,           // LightLevel (아직 분석 안함)
                analyzedPattern,// WaterFrequency (분석됨!)
                null,           // successTraits
                null            // failureTraits
        );

        log.info("User[{}] Analysis Updated: Interval={} days -> Pattern={}",
                userId, String.format("%.1f", averageIntervalDays), analyzedPattern);
    }

    // 날짜 간격의 평균을 구하는 로직
    private double calculateAverageInterval(List<GrowthRecord> records) {
        long totalDays = 0;
        int intervalCount = records.size() - 1;

        for (int i = 0; i < intervalCount; i++) {
            LocalDate prev = records.get(i).getRecordDate(); // 혹은 getRecordDate()
            LocalDate next = records.get(i + 1).getRecordDate();

            totalDays += ChronoUnit.DAYS.between(prev, next);
        }

        return (double) totalDays / intervalCount;
    }

    // 평균 일수에 따라 Enum 결정 (규칙은 기획에 따라 변경)
    private WaterFrequency mapToFrequency(double days) {
        if (days <= 5.0) {
            return WaterFrequency.FREQUENT; // 5일 이내: 자주 줌
        } else if (days <= 10.0) {
            return WaterFrequency.NORMAL;   // 6~10일: 보통
        } else {
            return WaterFrequency.RARE;     // 11일 이상: 게으름/건조하게 키움
        }
    }
}