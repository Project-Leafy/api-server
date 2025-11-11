package com.leafy.journal.dto;

import com.leafy.journal.domain.GrowthRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GrowthRecordResponse(
    Long recordId,
    Long plantId,
    LocalDate recordDate,
    String photoUrl,
    String memo,
    Boolean watered,
    Boolean fertilized,
    Boolean pruned,
    Boolean repotted,
    String waterAmountType,
    String fertilizerType,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static GrowthRecordResponse from(GrowthRecord growthRecord) {
        return new GrowthRecordResponse(
            growthRecord.getRecordId(),
            growthRecord.getMyPlant().getPlantId(),
            growthRecord.getRecordDate(),
            growthRecord.getPhotoUrl(),
            growthRecord.getMemo(),
            growthRecord.getWatered(),
            growthRecord.getFertilized(),
            growthRecord.getPruned(),
            growthRecord.getRepotted(),
            growthRecord.getWaterAmountType(),
            growthRecord.getFertilizerType(),
            growthRecord.getCreatedAt(),
            growthRecord.getUpdatedAt()
        );
    }
}
