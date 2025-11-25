package com.leafy.journal.repository;

import com.leafy.journal.domain.GrowthRecord;
import com.leafy.plant.domain.MyPlant; // MyPlant 엔티티 import
import com.leafy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrowthRecordRepository extends JpaRepository<GrowthRecord, Long> {

    // 1. 특정 식물의 모든 성장 일지 조회 (일지 목록)
    // 엔티티에 정의한 인덱스(plant_id, record_date)를 활용하기 위해
    // OrderByRecordDateDesc (기록일 기준 내림차순)를 추가
    List<GrowthRecord> findAllByMyPlantOrderByRecordDateDesc(MyPlant myPlant);
    
    // 2. (대안) 식물 ID로 직접 조회
    // List<GrowthRecord> findAllByMyPlantPlantIdOrderByRecordDateDesc(Long plantId);

    // 3. [추가] 특정 사용자의 모든 식물에 대한 성장일지 조회 (최신순)
    // 해석: GrowthRecord -> MyPlant -> User 가 파라미터 user와 같은지 확인
    List<GrowthRecord> findAllByMyPlant_UserOrderByRecordDateDesc(User user);
}