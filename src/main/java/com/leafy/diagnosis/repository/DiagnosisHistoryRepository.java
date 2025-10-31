package com.leafy.diagnosis.repository;

import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.plant.domain.MyPlant; // MyPlant 엔티티 import
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiagnosisHistoryRepository extends JpaRepository<DiagnosisHistory, Long> {

    // 1. 특정 식물의 모든 진단 기록 조회 (진단 내역)
    // diagnosis_datetime 기준 내림차순 정렬
    List<DiagnosisHistory> findAllByMyPlantOrderByDiagnosisDatetimeDesc(MyPlant myPlant);
}