package com.leafy.schedule.repository;

import com.leafy.schedule.domain.Schedule;
import com.leafy.plant.domain.MyPlant; // MyPlant 엔티티 import
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDate;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // 1. 특정 식물의 모든 스케줄 조회
    List<Schedule> findAllByMyPlant(MyPlant myPlant);

    // 2. 알림 배치를 위해, 오늘 날짜(next_due_date)가 도래하고
    //    아직 발송 안 된(PENDING) 스케줄 전체 조회
    List<Schedule> findAllByNextDueDateAndNotificationStatus(LocalDate today, String notificationStatus);
    
    // 3. (대안) 식물 ID와 스케줄 타입으로 특정 스케줄 찾기 (물주기 완료 처리 등)
    // Optional<Schedule> findByMyPlantPlantIdAndScheduleType(Long plantId, String scheduleType);

    // ✅ 추가된 부분: 사용자의 ID(userId)로 연결된 모든 식물의 스케줄 조회
    @Query("SELECT s FROM Schedule s JOIN FETCH s.myPlant p WHERE p.user.userId = :userId ORDER BY s.nextDueDate ASC")
    List<Schedule> findAllByUserId(@Param("userId") Long userId);
}