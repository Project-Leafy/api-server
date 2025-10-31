// com/leafy/journal/domain/GrowthRecord.java

package com.leafy.journal.domain;

import com.leafy.global.common.BaseTimeEntity;
import com.leafy.plant.domain.MyPlant; // 1. MyPlant 엔티티 import
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "growth_record", indexes = {
    // 2. ERD의 인덱스 설정: (plant_id, record_date)
    @Index(name = "idx_plant_record_date", columnList = "plant_id, record_date")
})
public class GrowthRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    // 3. N:1 관계 (GrowthRecord(N) -> MyPlant(1))
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id", nullable = false)
    private MyPlant myPlant;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "photo_url", nullable = false, length = 2048)
    private String photoUrl;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private Boolean watered = false;

    // ... (fertilized, pruned, repotted 등 나머지 boolean 필드) ...

    @Column(name = "water_amount_type", length = 50)
    private String waterAmountType;

    @Column(name = "fertilizer_type", length = 50)
    private String fertilizerType;
}