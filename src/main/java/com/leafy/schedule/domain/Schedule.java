// com/leafy/schedule/domain/Schedule.java

package com.leafy.schedule.domain;

import com.leafy.global.common.BaseTimeEntity;
import com.leafy.plant.domain.MyPlant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "schedule", uniqueConstraints = {
    // 1. ERD의 Unique Index (plant_id, schedule_type) 설정
    // 한 식물에 대해(plant_id) 동일한 스케줄 타입(schedule_type)은 
    // 하나만 존재해야 함 (예: '초록이'의 '물주기' 스케줄은 1개)
    @UniqueConstraint(
        name = "uk_plant_schedule_type", 
        columnNames = {"plant_id", "schedule_type"}
    )
})
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    // 2. N:1 관계 (Schedule(N) -> MyPlant(1))
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id", nullable = false)
    private MyPlant myPlant;

    @Column(name = "schedule_type", nullable = false, length = 50)
    private String scheduleType; // (Tip: Enum 관리 추천: 'WATERING', 'REPOTTING' 등)

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "frequency_days") // 3. Integer는 nullable (1회성 스케줄)
    private Integer frequencyDays;

    private LocalDate lastCompletedDate;

    @Builder.Default
    @Column(name = "notification_status", nullable = false, length = 50)
    private String notificationStatus = "PENDING"; // (Tip: Enum 관리 추천)

    // --- 알림 상태 변경 메서드 ---
    public void changeNotificationStatus(String status) {
        this.notificationStatus = status;
    }
}