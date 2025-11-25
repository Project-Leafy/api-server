package com.leafy.journal.repository;

import com.leafy.journal.domain.GrowthRecord;
import com.leafy.plant.domain.MyPlant; // MyPlant 엔티티 import
import com.leafy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;

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

    // [NEW] 스케줄러 분석용: 특정 유저의 '물주기(watered=true)' 기록만 조회
    // 1. MyPlant를 거쳐 User를 찾습니다 (mp.user.userId).
    // 2. watered가 true인 것만 필터링합니다.
    // 3. 최근 3개월 등 기간 조건을 위해 recordDate를 비교합니다.
    @Query("SELECT g FROM GrowthRecord g " +
            "JOIN g.myPlant mp " +
            "WHERE mp.user.userId = :userId " +
            "AND g.watered = true " +
            "AND g.recordDate >= :startDate " +
            "ORDER BY g.recordDate ASC")
    List<GrowthRecord> findAllWateringRecordsByUser(@Param("userId") Long userId,
                                                    @Param("startDate") LocalDate startDate);
}