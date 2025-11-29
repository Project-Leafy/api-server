package com.leafy.diagnosis.repository;

import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.global.type.DiagnosisFeedbackStep;
import com.leafy.plant.domain.MyPlant; // MyPlant 엔티티 import
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiagnosisHistoryRepository extends JpaRepository<DiagnosisHistory, Long> {

    // 1. 특정 식물의 모든 진단 기록 조회 (진단 내역)
    // diagnosis_datetime 기준 내림차순 정렬
    List<DiagnosisHistory> findAllByMyPlantOrderByDiagnosisDatetimeDesc(MyPlant myPlant);

    //  피드백에 대한 팁(D+2)을 보내야 하고, 아직 안 보낸(NONE) 기록 조회
    List<DiagnosisHistory> findAllByTipDateAndFeedbackStep(LocalDate date, DiagnosisFeedbackStep step);

    //  피드백에 대한 응답(D+5)을 해야 하고, 팁은 보냈던(TIP_SENT) 기록 조회
    List<DiagnosisHistory> findAllByCheckDateAndFeedbackStep(LocalDate date, DiagnosisFeedbackStep step);

    // ✅ [추가] 2단계: 특정 식물의 가장 최근 진단 기록 1개 조회
    Optional<DiagnosisHistory> findTopByMyPlantOrderByDiagnosisDatetimeDesc(MyPlant myPlant);
}