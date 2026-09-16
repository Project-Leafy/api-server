// com/leafy/notification/domain/Notification.java

package com.leafy.notification.domain;

import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.global.common.BaseTimeEntity;
import com.leafy.journal.domain.GrowthRecord;
import com.leafy.plant.domain.MyPlant;
import com.leafy.schedule.domain.Schedule;
import com.leafy.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "notification", indexes = {
    // 1. ERD의 인덱스 설정: (user_id, created_at)
    @Index(name = "idx_user_created_at", columnList = "user_id, created_at")
})
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    // 2. 수신 사용자 (N:1) - 필수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 3. 관련 식물 (N:1) - 선택 (시스템 공지 등은 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id") // nullable = true (default)
    private MyPlant myPlant;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType; // (Tip: Enum 관리 추천)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    // --- 4. 알림 근거 데이터 추적 (N:1) - 모두 선택적 ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_schedule_id")
    private Schedule relatedSchedule; // 'schedule_id'가 아닌 객체 참조

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_record_id")
    private GrowthRecord relatedRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_diagnosis_id")
    private DiagnosisHistory relatedDiagnosis;

    /** 알림함에서 읽음 처리 */
    public void markAsRead() {
        this.isRead = true;
    }
}
