package com.leafy.journal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leafy.journal.domain.GrowthRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GrowthRecordResponse(
        @JsonProperty("record_id")
        Long recordId,

        @JsonProperty("plant_id")
        Long plantId,

        // ✅ [추가됨] 화면 상단에 식물 이름을 띄우기 위해 필수입니다.
        @JsonProperty("plant_nickname")
        String plantNickname,

        @JsonProperty("record_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
        LocalDate recordDate,

        @JsonProperty("photo_url")
        String photoUrl,

        @JsonProperty("memo")
        String memo,

        @JsonProperty("watered")
        Boolean watered,

        @JsonProperty("fertilized")
        Boolean fertilized,

        @JsonProperty("pruned")
        Boolean pruned,

        @JsonProperty("repotted")
        Boolean repotted,

        @JsonProperty("water_amount_type")
        String waterAmountType,

        @JsonProperty("fertilizer_type")
        String fertilizerType,

        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
    public static GrowthRecordResponse from(GrowthRecord growthRecord) {
        return new GrowthRecordResponse(
                growthRecord.getRecordId(),
                growthRecord.getMyPlant().getPlantId(),

                // ✅ [추가됨] 식물 엔티티에서 닉네임을 가져와서 넣습니다.
                growthRecord.getMyPlant().getNickname(),

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