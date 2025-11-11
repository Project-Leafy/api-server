// com/leafy/diagnosis/domain/DiagnosisHistory.java

package com.leafy.diagnosis.domain;

import com.leafy.global.common.BaseTimeEntity;
import com.leafy.plant.domain.MyPlant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder; // ⬅️ 이 부분을 추가해야 합니다.
import lombok.AllArgsConstructor; // ⬅️ 이 부분도 함께 추가합니다.
import java.math.BigDecimal; // 1. decimal(5, 4) 타입을 위해 BigDecimal 사용
import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // ⬅️ 추가: 모든 필드를 인자로 받는 생성자 생성
@Builder // ⬅️ 추가: Builder 패턴 자동 생성
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "diagnosis_history", indexes = {
    // 2. ERD의 인덱스 설정: (plant_id, diagnosis_datetime)
    @Index(name = "idx_plant_diagnosis_datetime", columnList = "plant_id, diagnosis_datetime")
})
public class DiagnosisHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diagnosis_id")
    private Long diagnosisId;

    // 3. N:1 관계 (DiagnosisHistory(N) -> MyPlant(1))
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id", nullable = false)
    private MyPlant myPlant;

    @Column(name = "diagnosis_datetime", nullable = false)
    private LocalDateTime diagnosisDatetime;

    @Column(name = "request_image_url", nullable = false, length = 2048)
    private String requestImageUrl;

    @Column(name = "api_access_token", unique = true, length = 255)
    private String apiAccessToken;

    // 4. precision(총 자릿수), scale(소수점 자릿수) 설정
    @Column(name = "is_plant_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal isPlantProbability;

    @Column(name = "is_healthy", nullable = false)
    private Boolean isHealthy;

    @Column(name = "health_probability", precision = 5, scale = 4)
    private BigDecimal healthProbability;

    @Column(name = "disease_name", length = 255)
    private String diseaseName;

    @Column(name = "disease_probability", precision = 5, scale = 4)
    private BigDecimal diseaseProbability;

    @Column(name = "solution_detail", columnDefinition = "TEXT")
    private String solutionDetail;

    private LocalDate feedbackDueDate;

    @Column(name = "user_feedback_code", length = 50)
    private String userFeedbackCode; // (Tip: Enum 관리 추천)

    private LocalDateTime feedbackAt;
}